# Fluxion 调度领域

Fluxion 在 Broker 与 Worker 之间协调任务调度。本词汇表用于统一调度生命周期中的领域术语。

## 术语

**Schedule（调度）**：
一个 SCHEDULE 类型 Trigger 的调度生命周期，依据 Trigger 的配置维护未来触发点并产生 Execution。
_避免使用_：ScheduleConfig、任务实例、执行计划

**Executable（可执行体）**：
被 Execution 运行的 Executor 或 Workflow。Executor 归属于一个 Trigger；Workflow 可以被多个 Trigger 复用。
_避免使用_：Execution、ScheduleInstance

**Execution（执行实例）**：
由 Schedule 在某个计划触发时间预先创建的唯一持久化实例，覆盖等待触发、领取、运行、完成或跳过的完整生命周期。其身份必须包含所属 Trigger（当前等同于 Schedule）和计划触发时间；不同 Trigger 即使引用同一个 Workflow，也各自拥有独立的 Execution。
_避免使用_：ScheduleInstance、Job、仅指实际运行记录

**Execution 状态**：
`PENDING` 表示已计划但尚未被 Broker 领取；`CLAIMED` 表示 Broker 持有短租约、正在创建 Job，尚未开始实际运行；`RUNNING` 表示 Job 已创建并开始其生命周期；`SUCCEEDED`、`FAILED`、`SKIPPED` 和 `MISFIRED` 为终态。`MISFIRED` 表示补偿创建 Job 已耗尽仍失败，`FAILED` 表示已创建 Job 后的执行或 Workflow 最终失败，`SKIPPED` 表示策略明确选择不执行。状态定义与发生状态迁移的代码必须说明进入条件、租约归属、允许迁移及崩溃后的恢复动作。

**节点失败后继续策略（continueOnFailure）**：
Workflow 节点级配置，决定该节点的 Job 在重试耗尽后失败时，是否仍将该节点视为已完成并推进下游。启用时，Job 仍保持真实的 `FAILED` 状态，但在下游依赖判定中视为已完成；Execution 的终态由 Workflow 是否按其完成策略到达结束条件决定。旧名称 `skipWhenFail` 的兼容迁移不得改变既有配置的运行含义。

**Workflow 结束条件**：
Workflow 配置必须恰好有一个 `END` 节点；所有并行分支必须汇合至该节点。仅该节点成功完成时，Execution 才可进入 `SUCCEEDED`，从而避免某个分支提前结束导致 Execution 过早成功。

**Execution 版本快照**：
Execution 创建时必须固化所属 Trigger 和 Executable 的版本。每个 Execution 始终使用创建时的版本。

**Trigger 配置发布**：
任意 Trigger 配置发布都会切换未来 Execution 的版本。既有 `PENDING` Execution 必须批量置为 `INVALID`，再按新配置生成未来实例；这既修正时间规则变更后的 `triggerAt`，也保证未执行实例不混用新旧 Workflow 或 Executor 配置。`CLAIMED` 和 `RUNNING` 的 Execution 不回滚，继续使用已固化的版本。

**Trigger 启用校验**：
Trigger 的启用和禁用操作不直接修改既有 Execution。Broker 在持有租约且创建 Job 前校验 Trigger 当前的 `isEnabled`；未启用时，使用租约令牌将该 Execution 条件更新为 `INVALID`，否则继续执行。已实际运行的 Execution 不因 Trigger 禁用而被强制中断。

**创建 Job 的原子边界**：
在租约令牌仍有效的条件下，`CLAIMED → RUNNING` 状态迁移、Execution 版本校验和 Job 持久化必须处于同一数据库事务。事务提交后才可派发 Worker；提交前失败不留下半创建的 Job，租约到期后可安全恢复。

**Execution 创建幂等**：
每个 `(scheduleId, triggerAt)` 只允许创建一个 Execution。Broker 在事务提交后、Worker 派发前崩溃时，只重新派发该 Execution 下已经持久化但尚未运行的 Job，绝不重新创建 Execution 或 Job。

**Job 派发语义**：
Fluxion 保证 Execution 创建一次，但 Job 为至少一次派发。Worker 超时、网络响应丢失或租约恢复可能导致同一个 Job 再次派发；`jobId` 是跨 Broker 和 Worker 的稳定幂等键，业务侧必须据此防止外部副作用重复发生。

**两层租约**：
Execution 的 Broker 短租约只保护 `PENDING → CLAIMED → RUNNING` 中创建 Job 的权限。进入 `RUNNING` 的事务必须清除 Execution 租约；之后由每个 Job 自己的派发和运行租约、超时检查与重试机制负责恢复。两层租约不能互相回收或重建对方的记录。

**Execution 预生成窗口**：
`ScheduleLoader` 每 5 分钟扫描一次，并预生成未来 10 分钟的 Execution，保留一个完整扫描周期的冗余。Broker 恢复后，缺失的历史触发点必须按原计划时间补建为唯一 Execution，再由 misfire 策略决定其终态或补偿运行；不得以“最新一次”折叠丢弃触发点。历史补建按固定批次和游标持续推进，作为吞吐保护，不丢弃或合并应有触发点。

**发布方式**：
Fluxion 当前采用停服切换升级：所有 Broker 停止后执行数据库迁移，再启动新版本。因此不需要为 `ScheduleDelay → Execution` 合并保留新旧 Broker 并行时的双写或兼容读取逻辑。当前不保留开发和测试库中的旧数据，建表脚本和迁移可直接调整为目标模型，并以干净数据库验证。

**Misfire Policy（错过触发策略）**：
决定尚未被领取的到期 Execution 如何处理的规则。当 `now - triggerAt` 超过全局 `misfireThreshold`（默认 5 秒）时才应用。`SKIP` 不删除 Execution，而是将其置为 `SKIPPED`，以保留审计记录并阻止重复处理。`FIRE_RETRY.maxFireAttempts` 表示错过触发后允许尝试创建 Job 的总次数，默认 1；它只在尚未创建 Job 时生效，Job 创建成功后的失败重试由 `RetryOption` 独立处理。未成功创建 Job 且仍可重试时，以全局 `fireRetryInterval`（默认 1 秒）延迟下一次领取。
_避免使用_：Job 重试策略

**Retry Option（重试配置）**：
Execution 已进入运行阶段后，针对失败 Job 执行重试的规则。
_避免使用_：错过触发策略
