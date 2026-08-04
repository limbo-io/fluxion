# 执行状态与投递语义

本文档定义 Fluxion 分布式调度系统的执行状态管理和投递可靠性保证。

## 设计原则

### 单点权威原则

MySQL 作为 execution/job/attempt 状态的**唯一权威存储**。所有状态变更必须通过数据库事务保证原子性和持久性。

```
┌─────────────────────────────────────────────────────────────┐
│                     状态权威层级                             │
├─────────────────────────────────────────────────────────────┤
│  第一层: MySQL                                              │
│    └── Execution 聚合、Job lease/attempt/retry 持久化状态    │
│                                                             │
│  第二层: Broker 内存                                          │
│    └── 调度器与短暂的 Worker 下发目标缓存，不是状态源          │
│                                                             │
│  第三层: Worker 内存                                          │
│    └── JobTracker (本地执行状态，故障后由 Job 重试重建)        │
└─────────────────────────────────────────────────────────────┘
```

**重要约束**：本期不引入 Redis，避免增加部署成本。所有跨 Broker 协调依赖 MySQL 条件更新实现。

## 投递语义

### 至少一次保证 (At-Least-Once)

Broker 到 Worker 的任务派发保证**至少一次投递**：

| 场景 | 框架行为 | 业务要求 |
|------|----------|----------|
| 正常派发 | Worker 接收并执行 | 正常处理 |
| 网络抖动 | Broker 超时重试，Worker 可能收到重复 | 业务执行器幂等 |
| Worker 故障 | Broker 重试其他 Worker | 业务执行器幂等 |
| Broker 故障 | 新 Broker 接管后继续派发 | 业务执行器幂等 |

**框架承诺**：
- 框架通过持久化 attempt 尽量避免重复下发
- 通过 `job_id + dispatch_attempt` 抑制同一次派发的重复接收
- **不承诺精确一次 (Exactly-Once)** 执行

**业务责任**：
- 业务执行器必须实现幂等逻辑
- 幂等键建议：`execution_id` 或业务唯一标识

## 核心概念

### Execution

一个 Execution 代表一次任务调度的完整业务执行过程；Workflow 下可聚合多个 Job。它不保存单个 Worker 的 lease、attempt 或 retry：

| 字段 | 说明 |
|------|------|
| `execution_id` | 全局唯一标识 |
| `trigger_id` / `trigger_at` | 本次触发来源与时间点 |
| `status` | 整体业务聚合状态 |
| `executable_id` / `version` | 执行定义及版本 |

### Job（Broker 容错单位）

Job 是 Worker 选择、下发、超时、重试、lease 与结果 fencing 的最小单位：

| 字段 | 说明 |
|------|------|
| `job_id` | 全局唯一 Job 标识 |
| `execution_id` | 所属 Execution |
| `dispatch_attempt` | 每次新下发递增的持久化版本 |
| `lease_owner` / `lease_until` | 当前 Broker 对 Job 的可续租所有权 |
| `timeout_at` / `next_retry_at` | 超时和重试的持久化触发时间 |

| 属性 | 说明 |
|------|------|
| `job_id` | 关联 execution_id |
| `dispatch_attempt` | 第几次派发尝试 |
| 重复判定 | `job_id + dispatch_attempt` 唯一 |

### Attempt

一次完整的派发-执行-上报周期：

```
Attempt #1:
  Broker ──派发──▶ Worker
  Worker ──接收──▶ 检查 job_id + dispatch_attempt 是否已处理
  Worker ──执行──▶ 业务代码
  Worker ──上报──▶ Broker

Attempt #2 (重试):
  dispatch_attempt = 2
  同上演进，Worker 视其为全新的 job
```

## 状态机

### Execution 状态流转

```
                    ┌───────────┐
         ┌──────────│  WAITING  │◀─────────┐
         │          │  (等待中)  │          │
         │          └─────┬─────┘          │
         │                │ trigger        │
         │                ▼                │
         │          ┌───────────┐          │
         │          │  PENDING  │◀─────────┤
         │          │ (待调度)   │          │
         │          └─────┬─────┘          │
         │                │ claim          │ retry
         │                ▼                │
         │         ┌────────────┐           │
         │    ┌───▶│DISPATCHING │──────┐    │
         │    │    │ (派发中)   │      │    │
         │    │    └─────┬──────┘      │    │
         │    │          │ dispatch    │    │
         │    │          ▼             │    │
         │    │    ┌───────────┐       │    │
         │ timeout │  RUNNING  │       │    │
         │    │    │ (运行中)   │       │    │
         │    │    └─────┬─────┘       │    │
         │    │          │ result      │    │
         │    │          ▼             │    │
         │    │    ┌───────────┐       │    │
         └───┬┴───▶│  SUCCEED  │       │    │
              │    │  (成功)   │       │    │
              │    └───────────┘       │    │
              │                        │    │
              └────────────────────▶┌───┴────▼┴───┐
                                  │   FAILED    │
                                  │   (失败)     │
                                  └─────────────┘
```

### 状态转换约束

| 当前状态 | 允许转换 | 条件 |
|----------|----------|------|
| WAITING | PENDING | 触发时间到达 |
| PENDING | DISPATCHING | Broker 领取租约成功 |
| DISPATCHING | RUNNING | Worker 确认接收并成功启动 |
| DISPATCHING | FAILED | 所有 Worker 派发失败 |
| RUNNING | SUCCEED | Worker 上报成功，且 attempt/worker 匹配 |
| RUNNING | FAILED | Worker 上报失败 或 超时 |
| RUNNING | PENDING | Worker 下线或超时触发重试 |

**关键约束**：Worker 执行结果只接受与当前 `attempt + worker` 匹配的请求，过期 attempt 的结果被忽略。

## 租约机制

### Broker 调度租约

Broker 领取的调度记录是**带过期时间的租约**，防止 Broker 故障导致任务停滞。

```
┌────────────────────────────────────────────────────────────┐\│                       租约生命周期                           │
├────────────────────────────────────────────────────────────┤
│  1. Broker-A 领取记录                                       │
│     └── 设置 lease_owner = broker-a-id                       │
│     └── 设置 lease_until = now + 15s                         │
│                                                             │
│  2. LeaseMaintainer 每 10s 续租                             │
│     └── 条件: lease_owner = current AND lease_until > now    │
│     └── 新 lease_until = now + 15s                           │
│                                                             │
│  3. Broker-A 故障                                           │
│     └── 停止续租                                             │
│                                                             │
│  4. Broker-B 每 5s 扫描过期租约                             │
│     └── 将过期的 CLAIMED 记录释放回 INIT                    │
│     └── 后续 load 再由新的 Broker 条件领取                  │
│                                                             │
│  5. 最大接管延迟: 20s (15s 租约 + 5s 扫描间隔)                │
└────────────────────────────────────────────────────────────┘
```

### 交接围栏 (Fencing)

Broker 在以下操作前必须验证租约有效性：

1. `CLAIMED → RUNNING` 状态转换
2. 创建 execution 记录
3. 写入终态 (SUCCEED/FAILED)

旧 Broker 即使恢复，也不能继续执行已被接管的记录。

## 重试流程

### 触发条件

| 场景 | 检测机制 | 处理动作 |
|------|----------|----------|
| 执行超时 | TimeoutManager | 标记失败，检查重试策略 |
| Worker 下线 | Heartbeat 检测 | 回收任务，重新派发 |
| 派发失败 | Dispatch 异常 | 换 Worker 重试或进入失败态 |
| Worker 上报失败 | Result 处理异常 | 按重试策略处理 |

### 重试策略

```java
retry_option: {
  "retryType": "ON_FAILURE",      // ON_FAILURE / NONE
  "maxRetryTimes": 3,             // 最大重试次数
  "retryIntervalSeconds": 30      // 重试间隔
}
```

**重试流程**：
1. 失败的 Job 条件迁移为 `RETRY_WAIT`，并持久化 `next_retry_at`
2. 到期后 Job 回到 `INITED`，重新选择 Worker 派发
3. 新下发会使 `dispatch_attempt` 递增
4. 原 Worker 的过期结果因 attempt 与 worker address 条件不匹配而被忽略

## 周期调度积压处理

### 调度积压场景

当系统繁忙或 Broker 故障恢复时，可能堆积大量未执行的触发点：

```
预期触发:  09:00  09:01  09:02  09:03  09:04  09:05
实际执行:   X      X      ▼
                              09:05 Broker 恢复
                              09:03 发现积压
```

### 积压策略现状

**当前语义（LATEST_ONLY）**：对 Cron / FixedRate schedule，只保留最新的已过期触发点；更早的历史点不进入后续调度。未来触发点必须保留，不能因一次积压处理而被删除。

```
积压触发点: 09:00, 09:01, 09:02, 09:03
Broker 恢复时间: 09:05

处理结果:
  - 09:00 ~ 09:02: 跳过
  - 09:03: 保留并执行（最新的历史触发点）
  - 09:06: 正常触发（未来触发点不受影响）
```

**当前实现**：`ScheduleCommandService` 收集加载窗口内的触发点，再由 `ScheduleBacklogPlanner` 仅折叠 `<= now` 的历史点；未来点均创建为 delay。上述处理结果可以作为当前能力依赖。

**FixedDelay 调度**：从最后一次执行完成时间计算下次触发，自然跳过积压。

## Worker 心跳机制

### 心跳参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 心跳间隔 | 3 秒 | Worker 每 3 秒发送一次心跳 |
| 超时阈值 | 6 秒 | Broker 6 秒未收到心跳即判定下线 |

### 下线判定

```
Worker-A 心跳时序:
  T+0s    心跳 1 ✓
  T+3s    心跳 2 ✓
  T+6s    心跳 3 ✗ (丢失)
  T+6s+   Broker 判定 Worker-A 下线
          - 查询 Worker-A 上运行的 Job 并标记为待重试
          - 触发重试流程，选择其他 Worker
```

### 幂等接收实现

Worker 使用 `ConcurrentHashMap<JobKey, JobTracker>` 抑制重复：

```java
record JobKey(String jobId, int dispatchAttempt) {}

// 处理派发请求
JobKey key = new JobKey(jobId, dispatchAttempt);
JobTracker existing = runningJobs.putIfAbsent(key, newTracker);
if (existing != null) {
    // 重复接收，直接返回已存在的 tracker 状态
    return existing.getStatus();
}
```

## 故障恢复

### Broker 重启恢复

1. Broker core task 从 MySQL 扫描本 Broker bucket 中到期的 `RETRY_WAIT` Job、超时 Job 和过期 Job lease。
2. 超时或 lease 过期 Job 通过当前 `dispatch_attempt` 进入统一 retry 状态机。
3. 到期 retry 将 Job 置回 `INITED`，由 `JobUnRunChecker` 和 Job runner 重新下发。
4. 正在运行的 Job lease 每 10 秒续租；续租失败后由过期 lease 扫描接管。

### Worker 重启/下线

1. Broker 检测到 Worker 心跳超时 (6 秒)
2. 将该 Worker 标记为离线
3. 查询该 Worker 上运行的 Job 列表及当前 `dispatch_attempt`
4. 触发每个 Job 的 retry 流程；下一次下发才递增 `dispatch_attempt`
5. 新 Worker 接收后，`job_id + new_dispatch_attempt` 是新 key，无冲突

## 实现清单

- [x] MySQL 作为唯一权威存储
- [x] Job 表支持 lease_owner、lease_until、dispatch_attempt、timeout_at、next_retry_at 字段
- [x] Broker Job lease 领取 + 续租机制
- [x] Worker 幂等接收 (job_id + dispatch_attempt)
- [x] Broker 通过持久化 Job 扫描恢复 retry、超时与过期 lease
- [x] Worker 下线通过 bucket 范围的持久化 Job 查询唯一接管

## 参见

- [数据模型](data-model.md) - 数据库表结构设计
- [任务调度](scheduling.md) - 调度流程详解
- [执行器](executor.md) - Worker 端执行器开发指南
