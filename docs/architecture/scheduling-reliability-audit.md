# 调度可靠性现状与风险台账

本文档记录当前代码中任务创建、分发、重试与集群故障转移的实际链路，以及仍需注意的边界。它描述当前实现，不承诺业务侧恰好一次执行。术语以 [CONTEXT.md](../../CONTEXT.md) 为权威。

## 1. 当前主链路

```text
ScheduleLoader (Broker coreTask，周期触发)
  → ExecutionsLoadCmd 按 bucket 读 PENDING Execution
       (triggerAt ≤ now 且 nextFireAt ≤ now)
  → misfire 判定：now - triggerAt > 5s (MISFIRE_THRESHOLD_SECONDS)
       ├─ 触发 FIRE_RETRY：claim 失败则 releaseForRetry，nextFireAt += 1s
       └─ maxFireAttempts 耗尽 → finishPending(MISFIRED)
  → reclaimExpiredClaims()：bucket 内 CLAIMED 且 leaseUntil ≤ now 的 Execution 可被重新 claim
  → claim()：条件 UPDATE PENDING → CLAIMED，置 leaseOwner / executionToken / leaseUntil (+15s) / fire_attempt(+1 若 misfired)
  → runClaimedExecutionInTransaction()：同一事务内
       ├─ Trigger isEnabled 校验（未启用 → 条件更新为 INVALID 并放弃）
       ├─ Executable 组装 + Job 持久化
       └─ 条件 UPDATE CLAIMED → RUNNING（带 executionToken fencing），清除 Execution 调度租约
  → 事务提交后派发 Worker (JobDispatchRequest)
  → JobReportCmd / JobSuccessCmd / JobFailCmd
```

参数（均以代码为准）：

| 项 | 值 | 来源 |
|----|----|------|
| Execution claim lease 有效期 | 15 秒 | `ExecutionScheduleCommandService.CLAIM_LEASE_SECONDS`（`ScheduleLeaseProperties` 为死配置，见 execution-state.md 警告） |
| claim 续租间隔 | 10 秒 | `ScheduleLeaseProperties.renewInterval` |
| 过期 claim 回收扫描 | 5 秒 | `ScheduleLeaseProperties.reclaimInterval` |
| misfire 阈值 | 5 秒 | `MISFIRE_THRESHOLD_SECONDS` |
| FIRE_RETRY 间隔 | 1 秒 | `FIRE_RETRY_INTERVAL_SECONDS` |
| Job lease 有效期 | 15 秒 | `JobLeaseService.LEASE_DURATION_SECONDS` |
| Job lease 续租 | 10 秒 | `JobLeaseRenewChecker` |
| Job lease 过期回收 | 1 秒 | `JobLeaseRecoveryChecker` |
| Worker 心跳间隔 | 3 秒 | `WorkerRemoteConstant.HEARTBEAT_TIMEOUT_SECOND` |
| Worker 下线判定 | 6 秒未心跳 | `HEARTBEAT_TIMEOUT_SECOND * 2` |

> 当前已合并为 Execution 模型：`ScheduleDelay` 表与 INIT/CLAIMED/SUCCEED 三状态机已移除，`(trigger_id, trigger_at)` 唯一键落在 `fluxion_execution` 上。`DelayedTaskScheduler`（基础设施时间轮）与 `ExecutionCreateCmd`（生成触发点时创建 Execution）仍存在，但角色与旧文档主链路描述不同，请勿按旧图理解。

## 2. 已确认风险

| ID | 风险 | 影响 | 当前证据 |
|---|---|---|---|
| R1 | 框架仍是至少一次执行 | lease 失效或网络分区时，新 attempt 可能与旧 Worker 并存 | `dispatch_attempt` fencing Broker 状态写入；业务执行器必须幂等 |
| R2 | Job lease 续租依赖 Broker 定时任务 | 长时间 GC、暂停或调度线程饥饿会使 lease 过期并触发 retry | `JobLeaseRenewChecker` 每 10 秒续租，有效期 15 秒；`JobLeaseRecoveryChecker` 每 1 秒扫描过期 |
| R3 | 本 Broker 的 Worker 下发目标缓存不是状态源 | 不能作为恢复或 fencing 依据 | Job 的 attempt、owner、lease 和结果状态均以 `fluxion_job` 为准 |
| R4 | H2 回归不覆盖 MySQL 原生并发语义 | 唯一键兜底、条件 UPDATE fencing、reclaim/重领的**逻辑并发正确性**已由 `MultiBrokerClaimConcurrencyTest` 在 H2 上用真实多线程验证；剩余缺口为 MySQL 原生语义：原子 upsert、REPEATABLE READ 锁竞争与死锁行为 | H2 profile 排除 `DistributedLockMySqlTest`（MySQL 专有 upsert） |
| R5 | 无可观测性 | 当前未引入 Micrometer/Actuator/Prometheus，可靠性动作只写日志，无聚合 SLI/告警 | pom 全仓无 micrometer 依赖，无 `Counter.builder` 调用 |
| R6 | `fluxion.fault-tolerance` YAML 是死配置 | `retry/timeout/failover` 整段无代码读取，按其调参无效 | 全仓零 `@ConfigurationProperties(prefix="fluxion.fault-tolerance")` |
| R7 | 调度回调在时间轮单线程串行执行 DB 与 Execution 创建 | 慢 SQL 或大量同时到点任务会串行阻塞，导致后续触发延迟 | `DelayedTaskScheduler` 跑在单个时间轮线程 |

## 3. 设计边界

- 当前语义是 best-effort 去重加至少一次执行；业务执行器负责最终幂等。
- 生产协调存储为 MySQL，不引入 Redis。
- H2 是本地/CI 的通用集成回归数据库，不替代 MySQL 并发验证。
- "Worker 下线转移"在当前代码中应称为"按 retry policy 重跑"，不能称为无缝迁移。
- 可观测性当前仅靠日志；存量诊断接口已规划（见运维手册），速率型 SLI 暂无聚合链路。

## 4. 改造顺序

- 已完成：`ScheduleDelay → Execution` 合并（唯一键、短租约 claim、misfire、版本快照、Workflow 终态、原子边界）。计划见 `.planning/schedule-fix/task_plan.md`。
- 进行中：生产可运行性闭环——文档对齐 Execution 模型（运维手册、本文档、执行状态文档），以及存量诊断接口实现。
- 后续：生产 MySQL 多 Broker 并发/故障压测；速率型 SLI 以结构化日志聚合补齐。

历史审计计划见 `.planning/test-governance-history/task_plan.md` 的 Phase 7；当前 Job 级容错改造计划见 `.planning/schedule-fix/task_plan.md`。
