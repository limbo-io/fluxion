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

> 状态名以 [CONTEXT.md](../../CONTEXT.md) 为权威。代码见 `ExecutionStatus` 与 `ExecutionScheduleCommandService`。旧文档的 `WAITING / DISPATCHING` 措辞不复使用。

```
        (ScheduleLoader 每 5 分钟生成触发点 → ExecutionCreateCmd 预建 Execution)
                                │
                                ▼
                          ┌──────────┐
        ┌── (SKIP 策略) ──▶│ PENDING  │◀────────────┐ (releaseForRetry)
        │                  │ (已计划)  │             │ next_fire_at += 1s
        │                  └────┬─────┘             │
        │        ┌────────────┼──────────────┐    │
        │        │ 到期可领取    │ 超过 5s       │    │
        │        │ (逐条 claim) │ (misfire)     │    │
        │        ▼            ▼              │    │
        │  ┌──────────────┐ (attempt 耗尽 → MISFIRED)
        │  │   CLAIMED    │                  │    │
        │  │ 15s 一次性租约 │                  │    │
        │  │ 创建 Job 中    │                  │    │
        │  └──┬───────┬───┘                  │    │
        │     │       │                      │    │
        │     │ 事务内  │ Trigger 禁用          │    │
        │     ▼       ▼                      │    │
        │  ┌────────┐ ┌─────────┐            │    │
        │  │RUNNING │ │ INVALID │            │    │
        │  └───┬────┘ └─────────┘            │    │
        │      │ (进入 RUNNING 即清租约，      │    │
        │      │  移交 Job 自身的租约/重试)     │    │
        │      ├── Workflow 到达唯一 END 成功 ──▶ SUCCEED
        │      └── Workflow 失败规则判定 ─────▶ FAILED
        │
        └──▶ SKIPPED     (misfire 判定后策略要求跳过)
             MISFIRED    (FIRE_RETRY.maxFireAttempts 耗尽仍未创建成功)
```

### 状态转换约束

| 当前状态 | 允许转换 | 条件与归属 |
|----------|----------|-----------|
| PENDING | CLAIMED | Broker 条件 UPDATE 成功，置 `leaseOwner / executionToken / leaseUntil(+15s)` |
| PENDING | SKIPPED | misfire 且策略为 SKIP，保留审计、阻止重复处理 |
| PENDING | MISFIRED | FIRE_RETRY `maxFireAttempts` 耗尽仍未成功 claim |
| CLAIMED | RUNNING | 同事务：Trigger `isEnabled` 校验通过 + Job 持久化 + fence token 条件迁拓 |
| CLAIMED | INVALID | 事务内校验 Trigger `isEnabled=false`，以租约令牌条件迁拓 |
| CLAIMED | MISFIRED | 事务内创建 Job 失败且 `fire_attempt` 已达上限 |
| CLAIMED | PENDING | `releaseForRetry`：置 `nextFireAt = now + 1s`，保留原 lease 令牌 |
| RUNNING | SUCCEED | Workflow 到达唯一 END 节点成功 |
| RUNNING | FAILED | Workflow 失败规则判定失败 |

进入 `RUNNING` 的事务必须清除 Execution 调度租约；此后由每个 Job 自己的租约与重试机制负责。调度周周期：`ScheduleLoader` 每 5 分钟生成触发点，`ExecutionLoader` 每 1 分钟发 `ExecutionsLoadCmd` 扫描+claim。

**关键约束**：Worker 执行结果只接受与当前 `attempt + worker` 匹配的请求，过期 attempt 的结果被忽略。终态为 `SUCCEED / FAILED / SKIPPED / MISFIRED / INVALID`。

## 租约机制

### Broker 调度租约（Execution claim lease）

Broker 对 `PENDING` Execution 的领取是**一次性设租约，不续租**。设计目的：防止 Broker 故障导致任务停滞。参数以 `ExecutionScheduleCommandService` 常量与 `ScheduleLoader` / `ExecutionLoader` 周期为准。

```
┌────────────────────────────────────────────────────────────┐
│                  Execution claim lease 生命周期             │
├────────────────────────────────────────────────────────────┤
│  1. Broker-A claim 成功                                    │
│     └── 条件 UPDATE PENDING → CLAIMED                       │
│     └── lease_owner = brokerA；execution_token = UUID       │
│     └── lease_until = NOW(3) + 15s（CLAIM_LEASE_SECONDS）   │
│     └── fire_attempt++（仅 misfired 时）                     │
│  特点：单次设租约，进入 RUNNING 前不续租                     │
│                                                             │
│  2. Broker-A 故障 / 慢                                      │
│     └── lease_until 到期不动                                │
│                                                             │
│  3. ExecutionLoader 每 1 分钟发 ExecutionsLoadCmd           │
│     └── reclaimExpiredClaims：bucket 内 CLAIMED             │
│         且 lease_until ≤ now 的记录可被重新 claim            │
│     └── 后续 claim 由任意 Broker 条件领取                    │
│                                                             │
│  说明：自恢复依赖下一趟 ExecutionsLoadCmd，              │
│        最大接管延迟为 15s lease + 1min 小周期            │
└────────────────────────────────────────────────────────────┘
```

> ⚠️ `ScheduleLeaseProperties` 虽定义了 `duration=15 / renewInterval=10 / reclaimInterval=5`，但全仓代码均未调用其 getter，是死配置类。真实数值来自 `ExecutionScheduleCommandService.CLAIM_LEASE_SECONDS=15` 等常量与 Loader 周期。旧版 “LeaseMaintainer 每 10s 续租”、“过期 CLAIMED 释放回 INIT” 属于已删的 ScheduleDelay 模型，请勿参照。

### 交接围栏 (Fencing)

Broker 在以下操作前必须以 `execution_token` 条件化验证租约有效性：

1. `CLAIMED → RUNNING` 状态转换（事务内）
2. `CLAIMED → INVALID`（Trigger 未启用）
3. `CLAIMED → MISFIRED` / `releaseForRetry` 等中间态变更

写入终态（SUCCEED / FAILED / SKIPPED / MISFIRED / INVALID）均需验明归属令牌。旧 Broker 即使恢复，也不能继续执行已被接管的 Execution。

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

**当前语义**：对所有触发点，每个到期触发点都生成唯一 Execution，**不折叠、不丢弃历史点**。这对应 [CONTEXT.md](../../CONTEXT.md) 的“Execution 预生成窗口”与“历史补建不折叠”约定。`ScheduleBacklogPlanner.plan` 原样返回加载窗口内的全部触发点，不做 LATEST_ONLY 折叠；misfire 策略在 Execution 被加载到点后由 Broker 判定。

```
积压触发点: 09:00, 09:01, 09:02, 09:03
Broker 恢复时间: 09:05

处理结果: 各点分别创建唯一 Execution（(triggerId, triggerAt) 唯一键）
  每个到期点各自走 misfire 判定（now - triggerAt > 5s）
  ├─ SKIP policy  → SKIPPED
  └─ FIRE_RETRY   → 领取尝试创 Job，失败重试耗尽 → MISFIRED
未来触发点（09:04 / 09:05+）正常触发。
```

**当前实现**：`ScheduleCommandService` 收集加载窗口内的触发点，`ScheduleBacklogPlanner.plan` 原样返回，再为每个点发 `ExecutionCreateCmd` 创建唯一 Execution。不适用旧版 LATEST_ONLY 的“只保留最新历史点”语义——那是已删除模型。

**FixedDelay 调度**：从最后一次执行完成时间计算下次触发，自然跳过架上未点的中间点。

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
