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
│    └── execution / job / attempt 持久化状态                 │
│                                                             │
│  第二层: Broker 内存                                          │
│    └── ExecutionStateStore (本地索引缓存，可重建)              │
│                                                             │
│  第三层: Worker 内存                                          │
│    └── JobTracker (运行时状态，丢失后从 Broker 恢复)           │
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

一个 Execution 代表一次任务调度的完整执行过程，包含：

| 字段 | 说明 |
|------|------|
| `execution_id` | 全局唯一标识 |
| `job_id` | 关联的任务定义 |
| `status` | 状态: WAITING → PENDING → DISPATCHING → RUNNING → SUCCEED/FAILED |
| `dispatch_attempt` | 派发尝试次数 |
| `worker_id` | 当前执行的 Worker |
| `lease_owner` | 租约持有者 (Broker ID) |
| `lease_until` | 租约过期时间 |

### Job (Worker 侧)

Worker 接收到的任务单元，用于抑制重复：

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
│     └── 发现 lease_until < now 且 owner = broker-a         │
│     └── 条件更新: 接管为新 owner，dispatch_attempt++         │
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
1. 创建新的 attempt (`dispatch_attempt` 递增)
2. Execution 状态回归 `PENDING`
3. 重新选择 Worker 派发
4. 原 Worker 的过期结果忽略

## 周期调度积压处理

### 调度积压场景

当系统繁忙或 Broker 故障恢复时，可能堆积大量未执行的触发点：

```
预期触发:  09:00  09:01  09:02  09:03  09:04  09:05
实际执行:   X      X      ▼
                              09:05 Broker 恢复
                              09:03 发现积压
```

### 跳过策略

**Cron 调度**：跳过所有过期触发点，只执行最新触发点。

```
积压触发点: 09:00, 09:01, 09:02, 09:03
Broker 恢复时间: 09:05

处理结果:
  - 09:00 ~ 09:03: 判定过期，跳过
  - 09:05: 无触发点，等待下一周期
  - 09:06: 正常触发 (如果调度周期为 1 分钟)
```

**FixedRate 调度**：同样跳过过期点，从最新点开始。

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
          - 将 Worker-A 上运行的 execution 标记为待重试
          - 触发重试流程，选择其他 Worker
```

### 幂等接收实现

Worker 使用 `ConcurrentHashMap<JobKey, JobTracker>` 抑制重复：

```java
record JobKey(String executionId, int dispatchAttempt) {}

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

1. 启动时从 MySQL 加载本 Broker 负责 buckets 的活跃 execution
2. 为每个活跃 execution 领取新租约
3. 注册到 ExecutionStateStore 内存索引
4. 重新加入 TimeoutManager 监控
5. 已超时的 execution 不走恢复，直接触发超时处理

### Worker 重启/下线

1. Broker 检测到 Worker 心跳超时 (6 秒)
2. 将该 Worker 标记为离线
3. 查询该 Worker 上运行的 execution 列表
4. 触发每个 execution 的重试流程 (dispatch_attempt 递增)
5. 新 Worker 接收后，`job_id + new_dispatch_attempt` 是新 key，无冲突

## 实现清单

- [x] MySQL 作为唯一权威存储
- [x] Execution 表支持 lease_owner、lease_until、dispatch_attempt 字段
- [x] Broker 租约领取 + 续租机制
- [x] Worker 幂等接收 (job_id + dispatch_attempt)
- [ ] Broker 启动恢复执行监控
- [ ] Worker 下线故障转移

## 参见

- [数据模型](data-model.md) - 数据库表结构设计
- [任务调度](scheduling.md) - 调度流程详解
- [执行器](executor.md) - Worker 端执行器开发指南