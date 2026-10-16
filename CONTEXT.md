# Fluxion 调度领域

Fluxion 在 Broker 与 Worker 之间协调任务调度。本词汇表用于统一调度生命周期中的领域术语。

## 术语

**Schedule（调度）**：
一个 SCHEDULE 类型 Trigger 的调度生命周期，依据 Trigger 的配置维护未来触发点并产生 Execution。
_避免使用_：ScheduleConfig、任务实例、执行计划

**Schedule 触发游标**：
`lastTriggerAt` 是 Schedule 最后一个已作出处理决策的计划触发时间，`nextTriggerAt` 是下一个尚未处理的计划触发时间。处理决策可以是创建 Execution 或按遗漏触发恢复明确丢弃；两者都不等同于最后一个实际运行的 Execution。除幂等重复外，任何点处理失败都不构成处理决策，游标不得推进。
_避免使用_：最后执行时间、Execution 游标

**Schedule 处理所有权**：
Schedule 只能由其 Bucket 的当前 Broker 处理。处理者在同一事务中确认 Bucket 所有权并串行化该 Schedule 的状态变更；Bucket 转移必须等待旧处理完成，转移后旧 Broker 不得继续推进 Schedule。非 Owner 的配置发布不直接推进触发游标，由当前 Owner 后续扫描处理。
_避免使用_：仅凭扫描时的 Bucket 归属、无所有权校验的直接触发

**Executable（可执行体）**：
被 Execution 运行的 Executor 或 Workflow。Executor 归属于一个 Trigger；Workflow 可以被多个 Trigger 复用。
_避免使用_：Execution、ScheduleInstance

**Job（运行单位）**：
Execution 进入运行阶段后，Workflow 各节点产生的、由 Worker 实际执行的最小单位。一个 Execution 可聚合多个 Job；Job 是 Worker 选择、下发、超时、重试、租约与结果 fencing 的边界，独立于 Execution 拥有自己的派发租约与重试状态。`jobId` 是跨 Broker 和 Worker 的稳定幂等键。
_避免使用_：Execution、Task、派发记录

**Execution（执行实例）**：
由 Schedule 在某个计划触发时间预先创建的唯一持久化实例，覆盖等待触发、领取、运行、完成或跳过的完整生命周期。其身份必须包含所属 Trigger（当前等同于 Schedule）和计划触发时间；不同 Trigger 即使引用同一个 Workflow，也各自拥有独立的 Execution。
_避免使用_：ScheduleInstance、Job、仅指实际运行记录

**Execution 状态**：
`PENDING` 表示已计划但尚未被 Broker 领取；`CLAIMED` 表示 Broker 持有短租约、正在创建 Job，尚未开始实际运行；`RUNNING` 表示 Job 已创建并开始其生命周期；`INITED` 表示非调度方式（手动、接口触发）创建的 Execution 的初始状态，等待首个 Job 启动后进入 `RUNNING`（调度类 Execution 一律经 `PENDING` 进入，不走该状态）；`INVALID` 表示未运行即作废：Trigger 配置发布作废尚未创建首个 Job 的 `PENDING`、`CLAIMED` 实例，首批启动校验发现 Trigger 已停用时作废 `CLAIMED` 实例，均为终态，不派发、不补偿。`SUCCEED`、`FAILED`、`SKIPPED`、`MISFIRED` 和 `EXPIRED` 为其余终态：`MISFIRED` 表示补偿创建 Job 已耗尽仍失败，`FAILED` 表示已创建 Job 后的执行或 Workflow 最终失败，`SKIPPED` 表示策略明确选择不执行，`EXPIRED` 表示激活截止时间已到而被 Broker 或 Worker 拒绝。状态定义与发生状态迁移的代码必须说明进入条件、租约归属、允许迁移及崩溃后的恢复动作。
_避免使用_：把 `INITED` 当作已开始运行；把 `INVALID`、`EXPIRED` 混同于 `FAILED`

**Job 状态**：
`INITED` 表示已创建但尚未派发；`RESTARTED` 表示重试调度中、等待重新派发，二者同属可派发态。`RUNNING` 表示已派发到 Worker 且正在执行。`RETRY_WAIT` 表示失败后已持久化 `next_retry_at`、等待下一次重试到期。`SUCCEED`、`FAILED`、`CANCELLED`、`TERMINATED`、`EXPIRED` 为终态：`SUCCEED` 表示 Worker 上报成功、`FAILED` 表示执行失败、`CANCELLED` 表示被取消、`TERMINATED` 表示被手工终止、`EXPIRED` 表示 Worker 因激活截止时间拒绝开始业务代码。`PAUSED` 表示暂停、不计入终态。状态定义与发生状态迁移的代码必须说明进入条件、所属租约、允许迁移及崩溃后的恢复动作。

**调度反馈（Schedule Feedback）**：
Execution 到达任一终态时，Broker 必须以该时刻记一次反馈：写入所属 Schedule 的反馈时间并据此推算下一个触发点。未创建或未激活 Job 的终态（`SKIPPED`、`MISFIRED`、`INVALID`、`EXPIRED`）同样必须反馈；只有 Schedule 已删除时才允许不反馈。FIXED_DELAY 类型的 Schedule 完全依赖反馈推进——每个触发点的间隔从反馈时刻起算；任何到达终态却不反馈的 Execution 都会使该 Schedule 永久停摆。
_避免使用_：Job 上报（那是 Worker 与 Broker 之间的运行通信）、心跳

**节点失败后继续策略（continueOnFailure）**：
Workflow 节点级配置，决定该节点的 Job 在重试耗尽后失败时，是否仍将该节点视为已完成并推进下游。启用时，Job 仍保持真实的 `FAILED` 状态，但在下游依赖判定中视为已完成；Execution 的终态由 Workflow 是否按其完成策略到达结束条件决定。旧名称 `skipWhenFail` 的兼容迁移不得改变既有配置的运行含义。

**Workflow 结束条件**：
Workflow 配置必须恰好有一个 `START` 节点和一个 `END` 节点；所有并行分支从前者的下游分叉并汇合至后者。仅该节点成功完成时，Execution 才可进入 `SUCCEED`，从而避免某个分支提前结束导致 Execution 过早成功。

**Execution 版本快照**：
Execution 创建时必须固化所属 Trigger 和 Executable 的版本。每个 Execution 始终使用创建时的版本。

**Trigger 配置发布**：
任意 Trigger 配置发布都会切换未来 Execution 的版本。尚未创建首个 Job 的 `PENDING`、`CLAIMED` Execution 必须置为 `INVALID`，再按新配置生成未来实例；这既修正时间规则变更后的 `triggerAt`，也保证未激活实例不混用新旧 Workflow 或 Executor 配置。已创建首个 Job 的 Execution 不回滚，继续使用已固化的版本；发布与 Job 创建并发时同样以此为切点。

**Trigger 启用校验**：
Trigger 的启用和禁用操作不直接修改既有 Execution。Broker 在持有租约且创建首批 Job 前校验 Trigger 当前的 `isEnabled`；未启用时，使用租约令牌将该 Execution 条件更新为 `INVALID`，否则继续执行。校验仅适用于启动首批 Job，已启动的 Workflow 后续节点及 Map/Reduce 后续阶段或子任务不再校验 Trigger 启用状态。已实际运行的 Execution 不因 Trigger 禁用而被强制中断。

**创建 Job 的原子边界**：
在租约令牌仍有效的条件下，`CLAIMED → RUNNING` 状态迁移、Execution 版本校验和 Job 持久化必须处于同一数据库事务。事务提交后才可派发 Worker；提交前失败不留下半创建的 Job，租约到期后可安全恢复。

**Execution 创建幂等**：
每个 `(scheduleId, triggerAt)` 只允许创建一个 Execution。Broker 在事务提交后、Worker 派发前崩溃时，只重新派发该 Execution 下已经持久化但尚未运行的 Job，绝不重新创建 Execution 或 Job。

**Job 派发语义**：
Fluxion 保证 Execution 创建一次，但 Job 为至少一次派发。Worker 超时、网络响应丢失或租约恢复可能导致同一个 Job 再次派发；`jobId` 是跨 Broker 和 Worker 的稳定幂等键，业务侧必须据此防止外部副作用重复发生。

**Job 时间语义**：
`triggerAt` 是 Job 自身可被调度的时刻，随派发请求交给 Worker。调度 Execution 的首批 Job 继承 `Execution.triggerAt`；Workflow 后续 Job 在前序完成后创建，其 `triggerAt` 是自身创建时刻，不继承 Execution 的计划触发时刻。Worker 在本机实际接收 Job 时记录 `acceptedAt`；该时刻不由 Broker 提供，以免将发送时刻误作接收时刻。
_避免使用_：Broker 接收时间、所有 Job 继承 Execution 的触发时刻

**Worker 存活时刻**：
Broker 接收 Worker 心跳的 UTC 时刻，是判断 Worker 在线与否的唯一事实源；Worker 随请求携带的发送时刻只用于诊断，不能参与存活判定。只有从 `ONLINE` 条件迁移为 `OFFLINE` 成功的 Worker，才触发其 Job 接管。
_避免使用_：客户端心跳时间、仅凭扫描到记录就接管

**Job 派发结果**：
Worker 对一次 Job 派发返回的确定性结果。`ACCEPTED` 表示已接受并会开始处理；`REJECTED_DEADLINE` 表示激活截止时间已到，必须收敛为 `EXPIRED` 而不重试；`REJECTED_UNAVAILABLE` 表示 Worker 暂不可用，仍可按 Job 容错策略处理。
_避免使用_：Boolean 派发结果、把截止拒绝视为普通下发失败

**两层租约**：
Execution 的 Broker 短租约只保护 `PENDING → CLAIMED → RUNNING` 中创建 Job 的权限。进入 `RUNNING` 的事务必须清除 Execution 租约；之后由每个 Job 自己的派发和运行租约、超时检查与重试机制负责恢复。两层租约不能互相回收或重建对方的记录。

**Execution 预生成窗口**：
`ScheduleLoader` 每 5 分钟扫描一次，并预生成未来 10 分钟的 Execution，保留一个完整扫描周期的冗余。未被预生成的历史触发点属于遗漏触发点，其是否物化为 Execution 由遗漏触发恢复决定；它们不能与已创建但延迟激活的 Execution 混同。

**遗漏触发恢复（Missed-trigger Recovery）**：
Schedule 对未创建 Execution 的历史触发点采用的恢复规则，仅适用于 `CRON` 和 `FIXED_RATE`。恢复范围是 `nextTriggerAt` 之后尚未作出处理决策的历史区间，每个点只处理一次。触发点晚于当前时刻超过 5 秒才被认定为遗漏。默认关闭并丢弃全部遗漏触发点；显式开启时，仅按原计划触发时间检查最接近当前时刻的 10 个，较早的点明确丢弃且不创建 `SKIPPED` Execution。该上限按原始触发点计数，已存在的同点 Execution 也占用范围，不向更早历史扩张。恢复开关按 Broker 实际读取到的配置即时生效。恢复点若已满足 `SKIP` 的 Misfire 条件，或其首批 Job 的 Deadline 已过，同样直接丢弃、不创建 Execution；其余恢复点才物化并适用 Misfire Policy。同一 `(triggerId, triggerAt)` 的 Execution 已存在或发生唯一键冲突时，视为该点已处理并继续恢复。`FIXED_DELAY` 不允许开启恢复，因为其下一次触发依赖前一次的调度反馈。
_避免使用_：Misfire、完整历史补建

**遗漏触发恢复观测**：
遗漏触发恢复只按结果累计已恢复与已丢弃的数量，用于告警和容量判断；不为丢弃点创建审计记录，不将 Schedule 标识作为指标标签。
_避免使用_：逐触发点日志、按 Schedule 的时序标签

**调度背压**：
Broker 的内部容量不足以即时处理到期 Execution 时，对新领取和 Job 创建施加的有界限制。背压只能造成可观测的延迟与补偿，不能将内部饱和误判为用户选择的 `SKIPPED`；`SKIP` 只表达 Misfire Policy 的明确业务决定。
_避免使用_：内部丢弃、队列满即跳过

**Broker 资源域**：
Broker 将调度控制面与 Job 派发面视为相互隔离的资源域。调度控制面负责 Execution 进入运行态；Job 派发面负责将已创建的 Job 交给 Worker，任一资源域饱和不应阻塞另一个资源域。
_避免使用_：共享 IO 池

**背压延后**：
因调度控制面容量不足而暂不处理的 `PENDING` Execution。背压延后保留原始触发点，记录再次可处理时间和延后原因，不属于错过触发或创建 Job 失败，因而不适用 Misfire Policy。配置了激活截止时间的实例可等待至截止时间后以 `EXPIRED` 结束；未配置的实例持续等待并告警，不自动丢弃。
_避免使用_：Misfire、FIRE_RETRY

**Execution 领取**：
Broker 对一个 `PENDING` Execution 取得短时、可回收的处理归属。领取、释放、回收及转为 `EXPIRED` 都以当前状态、归属标识和未过期租约作条件更新；条件不符即停止处理。首次 Job 由 `(execution_id, ref_id)` 唯一约束兜底：冲突时复用已存在 Job，视为该 Execution 已激活。
_避免使用_：无条件状态更新、重复下发

**调度背压观测**：
以 Broker 为维度记录背压延后次数、积压量、最早积压年龄和 `EXPIRED` 数量，并记录 `triggerAt` 至首个 Job 持久化的端到端延迟。指标不携带 Schedule 或 Trigger 标识；排障接口单独返回积压最久的少量 Schedule/Trigger。最早积压年龄超过 30 秒或出现 `EXPIRED` 时告警，正常运行目标为端到端延迟 P99 不超过 3 秒。
_避免使用_：按 ScheduleId 打指标、将排障明细作为时序标签

**Execution 激活截止时间**：
某次 `SCHEDULE` Execution 从其原始 `triggerAt` 起允许首次激活的最晚时间，由创建时固化的业务时效决定。其值是 UTC 绝对时刻；Broker 在派发前检查，Worker 在业务代码开始前以 NTP 同步的本机时钟作最终检查。它约束首个 Job 开始前的全部投递尝试，不约束 Worker 已开始后的执行、Workflow 后续节点或整个 Workflow 的完成时长；未配置时不设截止时间，允许迟到执行。其配置字段为可空的 `maxStartDelayMillis`，非空值最小为 1000；创建时换算并固化为 Deadline。Deadline 是硬上限：未超过时仍适用 Misfire Policy，超过时首个 Job 与 Execution 统一以 `EXPIRED` 结束且不再重试。手动/API Execution 不适用该概念。
_避免使用_：Schedule 生效结束时间、Job 运行超时、Workflow 完成截止时间

**配置发布切点**：
Schedule/Trigger 配置变更后，尚未创建首个 Job 的 `PENDING`、`CLAIMED` Execution 批量转为 `INVALID`，由新配置重新预生成；已创建首个 Job 的 Execution 保持创建时快照继续执行。要停止已激活的 Execution，必须使用明确的取消语义。
_避免使用_：配置变更隐式取消已激活执行

**统一时间模型**：
触发点、下一次触发点、租约、Deadline 和 Job 生命周期等运行态时间均为 UTC `Instant`；数据库以 UTC `DATETIME(3)` 存储，并通过显式转换器与 `Instant` 双向转换，持久化、传输与比较不依赖 JVM、连接或机器默认时区。Worker RPC 以 epoch 毫秒传递绝对时间；管理/API 以 RFC 3339 UTC 字符串返回运行态时间。Schedule 的 Cron、开始和结束等日历语义使用本地日期时间及必填的 IANA `timeZone` 配置解释；仅展示层转换为用户时区。Cron 遇夏令时前跳的不存在本地时刻时跳过，遇回拨的重复本地时刻时只触发第一次；固定频率和固定延迟调度不受该规则影响。所有节点以 NTP 校时。
_避免使用_：`ZoneId.systemDefault()`、以 `LocalDateTime` 表示跨节点的运行态时刻

**发布方式**：
Fluxion 当前采用停服切换升级：所有 Broker 停止后执行数据库迁移，再启动新版本。因此不需要为 `ScheduleDelay → Execution` 合并保留新旧 Broker 并行时的双写或兼容读取逻辑。当前不保留开发和测试库中的旧数据，建表脚本和迁移可直接调整为目标模型，并以干净数据库验证。

**Misfire Policy（错过触发策略）**：
决定尚未被领取的到期 Execution 如何处理的规则。当 `now - triggerAt` 超过全局 `misfireThreshold`（默认 5 秒）时才应用。`SKIP` 不删除 Execution，而是将其置为 `SKIPPED`，以保留审计记录并阻止重复处理。`FIRE_RETRY.maxFireAttempts` 表示错过触发后允许尝试创建 Job 的总次数，默认 1；它只在尚未创建 Job 时生效，Job 创建成功后的失败重试由 `RetryOption` 独立处理。未成功创建 Job 且仍可重试时，以全局 `fireRetryInterval`（默认 1 秒）延迟下一次领取。
_避免使用_：Job 重试策略

**Retry Option（重试配置）**：
Execution 已进入运行阶段后，针对失败 Job 执行重试的规则。
_避免使用_：错过触发策略

**广播完成判定模式**：
广播默认采用关注执行成败模式；两种模式均要求所有目标任务执行完成，并收到对应 Worker 的执行结果，而非接受确认。关注执行成败模式要求全部成功，任一失败则整体失败；不关注执行成败模式在收齐全部执行完成结果后整体成功，即使全部目标均执行失败。没有可用 Worker 或持续缺少完成反馈时，遵循 Job 节点容错配置重试，耗尽后整体失败；投递完成、接受确认或结果缺失均不等同于广播执行完成。
_避免使用_：部分成功即成功、将投递完成等同于业务执行成功

**Map 子任务容错**：
Map 子任务节点异常后的恢复遵循所属 Job 节点的容错配置，包括重试次数等，不另设独立于 Job 配置的恢复规则。测试依据该节点具体配置判断恢复范围、次数及最终结果。
