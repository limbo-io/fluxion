# 调度可靠性现状与风险台账

本文档记录当前代码中任务创建、分发、重试与集群故障转移的实际链路，以及仍需注意的边界。它描述当前实现，不承诺业务侧恰好一次执行。

## 1. 当前主链路

```text
ScheduleLoader
  → ScheduleTriggerCmd
  → fluxion_schedule_delay (INIT)
  → Broker 条件领取 (CLAIMED, 15s lease)
  → DelayedTaskScheduler
  → RUNNING + execution token
  → ExecutionCreateCmd
  → Executable.execute
  → JobsCreateCmd / JobRunCmd
  → Worker 选择与 JobDispatchRequest
  → JobReportCmd / JobSuccessCmd / JobFailCmd
```

Schedule delay lease 采用 15 秒有效期、10 秒续租、5 秒扫描。过期的 `CLAIMED` delay 被释放为 `INIT`，再由拥有 bucket 的 Broker 重新领取；过期的 `RUNNING` delay 会根据同一 trigger 的 Execution 是否已创建，分别收敛为 `SUCCEED` 或回到 `INIT`。Job lease 也按 15 秒有效期、10 秒续租维护。Worker 下线、Job 超时和 Broker lease 过期统一进入 Job retry policy，而不是直接搬迁运行中的 Worker Task。

## 2. 已确认风险

| ID | 风险 | 影响 | 当前证据 |
|---|---|---|---|
| R1 | 框架仍是至少一次执行 | lease 失效或网络分区时，新 attempt 可能与旧 Worker 并存 | `dispatch_attempt` 只 fencing Broker 状态写入；业务执行器必须幂等 |
| R2 | Job lease 续租依赖 Broker 定时任务 | 长时间 GC、暂停或调度线程饥饿会使 lease 过期并触发 retry | `JobLeaseRenewChecker` 每 10 秒扫描，lease 有效期为 15 秒 |
| R3 | `dispatchTargets` 是本 Broker 的短暂 Worker 目标缓存 | 它不能作为恢复或 fencing 状态源 | Job 的 attempt、owner、lease 和结果状态均以 MySQL 为准 |
| R4 | H2 回归不覆盖 MySQL 并发语义 | 锁竞争、隔离级别和 MySQL 原子 upsert 回归仍需生产 MySQL 验证 | H2 profile 使用本地锁替代数据库分布式锁 |

## 3. 设计边界

- 当前语义是 best-effort 去重加至少一次执行；业务执行器负责最终幂等。
- 生产协调存储为 MySQL，不引入 Redis。
- H2 是本地/CI 的通用集成回归数据库，不替代 MySQL 并发验证。
- “Worker 下线转移”在当前代码中应称为“按 retry policy 重跑”，不能称为无缝迁移。

## 4. 改造顺序

已完成的 Job 级改造计划见 `.planning/schedule-fix/task_plan.md`。后续演进应聚焦生产 MySQL 压测、定时任务可观测性和业务幂等约束，而不是恢复 execution 级 retry 或无缝 Task 迁移。

历史审计计划见 `.planning/test-governance-history/task_plan.md` 的 Phase 7；当前 Job 级容错改造计划见 `.planning/schedule-fix/task_plan.md`。
