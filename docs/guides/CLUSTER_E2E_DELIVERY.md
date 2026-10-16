# 集群回归的实现与验收约定

状态：设计文档，尚未实现。与 [总体方案](CLUSTER_E2E_TESTING.md)、[完整用例目录](CLUSTER_E2E_CASES.md) 配套阅读。本文说明测试工具需要交付什么、怎样判定测试本身可靠，以及已知能力缺口，不承诺现有 Maven 已提供这些入口。

## 运行入口与场景选择

保留 JUnit 为场景定义方式，Maven 为统一构建和执行入口，不另造 YAML 场景引擎。实现时需支持以下独立维度，具体 profile、属性名和命令在实现完成后写回测试指南，不提前给出不可执行的命令。

| 维度 | 约定 |
| --- | --- |
| 用例选择 | 单个稳定编号、多个编号、场景组、全部；同一次选择去重，每个变体明确列出 |
| 串行 | 固定排序，首个失败、错误、超时或阻塞即停止，其余为 NOT_RUN；排序写入运行清单，不依赖测试发现顺序 |
| 并行 | 每个活动用例独立集群和存储，有并发上限；一个失败不取消其他用例，最终汇总 |
| 时间参数 | 快速/默认两套，记录实际生效值；默认模式使用生产默认时间，不使用测试加速替换 |
| 产物目录 | 每次运行唯一目录，所有报告及日志只写入此目录，无需在线服务 |

未知用例编号在启动集群前失败，记录选择错误，不能表现为零用例通过。将所有候选具体用例写入清单后再执行，确保串行停止和初始化失败仍有完整结果。

能力缺口已知时记录 BLOCKED 及缺口编号；明确不属于所选范围的用例无需出现在本次报告。选入了却未执行的用例不能消失。并行模式继续运行其他就绪用例；串行模式在第一个 BLOCKED 处停止。只有所有选中用例实际通过且没有运行级错误时退出码才为零。

## 隔离环境生命周期

1. 检查 JDK 21、Maven、Podman 及运行中的 Podman machine、镜像可用性与端口条件。
2. 通过本次 reactor 构建准备节点代码及测试执行器，避免运行本地 Maven 仓库旧依赖。测试 Worker 不依赖人工维护 demo 的业务行为。
3. 为场景生成资源清单：本次运行标识、节点身份、Java PID、端口、MySQL 容器/库、Worker 本地任务库、测试控制地址、产物路径。
4. 启动 MySQL 并完成项目真实 Flyway 迁移；不得用 H2 代替 Broker MySQL，也不另维护一份可能漂移的简化业务 schema。MySQL 镜像版本与目标部署兼容性在实现阶段明确并固定，不能只依赖 latest。
5. 启动两个 Broker，等待 API 可用、数据库连接、注册/心跳及 Bucket 所有权就绪；再启动两个 Worker，等待注册、心跳、执行器及筛选条件就绪。
6. 通过真实 API 准备、发布、启用场景。成功返回需检查业务响应，不只检查 HTTP 状态；Workflow publish 返回的校验信息也须检查。
7. 按用例等待事件、执行故障或配置变更并断言；在每个关键阶段记录证据及耗时。
8. 完成或失败后归档产物、停止本场景进程、删除专用容器和数据目录。清理失败写入报告并使整次运行非零，不能杀端口相同但不属于本次运行的进程。

只要求一个可用本机 Podman 运行时，不新增 Docker 依赖。正常场景启动环境不可达时直接失败，不连接开发库或共享库兜底。Worker 目前使用本地 H2 任务仓储，该路径也必须按场景和节点隔离；REJ-002 同一次重启保留其节点本地存储，以检测残留状态，场景结束后再删除。

数据库会话与运行态时间采用 UTC；连接保留生产的 `useAffectedRows=true`、UTC 会话初始化及 Hibernate UTC 设置，不能用测试连接差异绕过真实锁语义。默认参数模式不等于真实生产容量或网络环境。

## API 准备与只读观测

| 操作 | 已发现入口 | 测试使用方式 |
| --- | --- | --- |
| Trigger 创建 | `/api/v1/trigger/create` | 请求含 name/description，记录返回 ID |
| Trigger 配置 | `/api/v1/trigger/draft`、`/api/v1/trigger/publish` | 请求携带 id/config，按真实发布流程生成快照 |
| Trigger 启停 | `/api/v1/trigger/enable`、`/api/v1/trigger/disable` | 操作后确认实际配置状态，再释放相应暂停点 |
| Trigger 查询 | `/api/v1/trigger/get` | 确认当前启用状态及引用 |
| Workflow 配置 | `/api/v1/workflow/create`、`/draft`、`/publish`、`/get`（均在 `/api/v1/workflow` 下） | 先创建并发布 Workflow，再配置引用它的 Trigger；记录两个版本，不假定自动级联切换 |
| 执行与节点查询 | `/api/v1/execution/page`、`/api/v1/worker/page`、`/api/v1/observation/overview` | 用于就绪、结果和汇总观察，不假定这些 API 已暴露全部租约或 Job 细节 |

接口取自 Server start 模块 Controller，表中未指定 HTTP method，因为现有入口使用 RequestMapping，具体客户端约定须在实现时验证。App 等准备信息优先通过真实 Worker 注册流程获取；不因缺少 App create API 而直接插表。

Job、租约、Bucket、版本等细粒度断言可通过只读 SQL 补齐。Worker 子任务状态在其本地仓储及 Tracker 中，不能假定 MySQL 包含所有 Task 数据；通过测试事件或测试专用只读快照采集。所有记录依场景 ID、Trigger、Execution 和 Job 关联筛选，不导出其他运行数据。

## 精确控制点与业务事件

下列是所需控制点的逻辑位置，不是现有 API 名称。优先使用测试装配/拦截；确需生产接入时单独列出最小改动并保证正常启动不启用。

| 位置 | 用途与限制 |
| --- | --- |
| Execution 预生成入口、未推进游标前 | 构造遗漏触发；先确认目标历史点没有被预生成 |
| 激活前、尚未创建首批 Job | 测禁用、发布、Broker 故障；明确是否已 CLAIMED，不持有妨碍管理操作提交的锁 |
| 创建 Job 事务内、提交前 | 测崩溃回滚；只等待故障注入，不同时等待竞争事务成功以免测试自锁 |
| 创建 Job 提交后、网络派发前 | 测 Broker 重启只恢复派发；必须证明事务已提交 |
| Worker 接收后、新建 Tracker 前 | 测 Deadline 最终拒绝，不能在启动业务后才暂停 |
| Executor 业务开始/结束、分片/Reduce 开始/结束 | 控制运行中故障、超时及汇合，记录实际执行路径 |
| Broker 结果校验入口 | 证明旧 attempt 的真实成功/失败报文到达并被拒绝 |

暂停命中带 jobId/executionId、attempt、节点和阶段，控制器确认后才故障注入。普通 API 操作仍走真实网络。测试控制通道有自身超时，失联不会永久挂起整个测试；节点强制退出不等待业务清理钩子。

Executor/TaskContext 没有完整场景元数据，不能要求业务接口天然提供 attempt 或 Execution ID；测试装配层根据真实派发报文及持久化关联补全，不修改业务 Executor 接口来服务测试。业务标记与事件由测试进程持久化；事件接收确认后再杀 Worker，以免丢失故障阶段证据。

MapReduceExecutor.reduce 返回 void，Executor.run 也返回 void。聚合与业务结果验证采用测试输出事件/标记；不把当前接口不存在的参数、返回值或 JSON 字段作为测试前提。

## 时间预算

每个用例有独立启动、命中控制点、业务收敛、采集和清理期限。记录实际生效值及推导，禁止全部统一为任意 sleep 或固定 30 秒。

| 参数 | 当前核对情况 | 对方案的影响 |
| --- | --- | --- |
| Worker 心跳 | WorkerProperties.heartbeat，默认由协议常量定义 | 可配置但应大于零，并给失联阈值留余量 |
| Worker 失联检查 | `fluxion.worker.liveness.check-interval-seconds` 默认 3 秒、`offline-timeout-seconds` 默认 6 秒 | 已有配置及校验，快速模式可调整；默认模式保持原值 |
| Schedule 预生成扫描 | ScheduleConstants.LOAD_INTERVAL=5 分钟，窗口 10 分钟 | 当前常量，不能宣称已有命令行配置；遗漏恢复用例的主要耗时来源 |
| Execution 加载 | ExecutionLoader 构造周期 1 分钟 | 当前硬编码；需验证初次加载和之后扫描，不以手动调用 Loader 代替 |
| Execution claim/Misfire 时间 | ExecutionScheduleCommandService 使用内置常量 | 快速模式需先核实并提供测试装配，不能假定 ScheduleLeaseProperties 已连接该链路 |
| Job 超时、重试检查 | JobTimeoutChecker 为 1 秒；RetryOption.retryInterval 单位秒 | 预算包含扫描误差及负载余量，Job overtime 配置单位按真实实现核对 |

Worker 故障恢复预算至少包括：最后心跳到失联阈值、一次失联扫描、重试等待、重试及派发扫描、恢复业务和上报耗时，再加测得的负载余量。Broker 恢复还需加入 Broker 失联、Bucket 移交及相关租约恢复预算。不得通过提高测试通过阈值掩盖已明确的业务 Deadline。

快速模式只改变允许调整的运行间隔，不改变任务类型语义、重试次数、遗漏恢复 10 点上限、结果 fencing 或业务时间判断规则。未具备加速装配的场景可按默认参数运行并在报告注明，不能声称已实现快速覆盖。

## 报表数据契约

一次运行目录至少包含下列内容，具体文件名作为实现约定：

```text
<run-id>/
  manifest.json           # 代码版本、选择清单、生效配置、环境与资源身份
  report.html             # 离线打开的总览、逐用例断言和诊断链接
  results.json            # 机器可读结果，与 HTML 使用同一数据
  junit.xml               # CI 汇总
  cases/<case-id>/<variant>/
    events.jsonl          # 阶段及业务证据，包含实际采集时间
    assertions.json       # 每项预期、实际值、结论及关联证据
    nodes/                # 每节点 stdout/stderr 日志、超时线程栈
    snapshots/            # 相关数据库行、本地 Task 快照
```

运行清单记录 commit、dirty 状态、选定及解析后的用例/变体、串并行模式、并发上限、快速/默认参数、JDK/数据库版本和实际资源身份。不能只记录分支名称；dirty 工作区允许运行，但必须标明结果不能仅用 commit 重现。

逐用例结果记录 caseId/variant、开始/结束时刻、耗时、status、阶段、配置快照、关联 ID、断言、异常、故障注入及恢复信息、产物路径和诊断采集错误。未发生或未观测的数据填空并注明原因，不填造时间。Job 身份、计数和版本来自实际数据，不靠日志字符串猜测。

| 状态 | 含义 | JUnit XML 映射 |
| --- | --- | --- |
| PASS | 用例实际执行，所有必需断言及证据满足 | 成功 testcase |
| FAIL | 业务或过程断言不符 | failure |
| ERROR | 启动、控制、数据库/测试程序错误，无法完成验证 | error |
| TIMEOUT | 命中阶段、业务收敛或运行期限已到 | failure，保留 timeout 阶段 |
| BLOCKED | 已知能力缺口或待决策语义，尚不能完成该用例验收 | skipped，附 blocked 原因及缺口编号 |
| NOT_RUN | 因串行前序失败等原因未开始 | skipped，附原因 |

HTML/JSON 保留完整状态，不把 BLOCKED 与 NOT_RUN 混成通过。JUnit 不原生表达 BLOCKED，因此命令退出码必须另行汇总，不能只按 JUnit 失败数判断全绿。报告总数满足选中数=上述六种状态之和；清理或报告写入错误属于运行级错误，单独显示并非零退出。

HTML 需总览计数、模式/版本/配置、场景明细、预期和实际差异、故障与恢复时间线、日志/快照链接。使用相对产物链接，拷贝运行目录后仍可查阅；日志、异常、任务输入等作为文本转义。首次失败保留，即使人工重跑成功也产生新 run-id，不覆盖旧报告。不增加跨运行问题跟踪平台，稳定用例 ID 用于后续人工关联。

诊断保留各节点日志、场景相关 Trigger/Schedule/Execution/Job、Job 失败记录、版本、Broker/Worker/Bucket/租约数据和必要的 Worker Task 快照。失败或超时尽可能在清理前采集；Worker 已退出无法提供线程栈时明确标记，不把缺少已死亡进程线程栈当成新的业务失败。保存实际配置时剔除连接密码等凭据。产物不自动删除，后续由开发者或 CI 明确保留策略。

## 能力缺口与待决策项

以下来自源码检查，均未通过本次运行验证。已确认的目标不再征求重复确认；尚未确认的语义不替用户选择。实现缺口修复与测试建设分开跟进，必要时先留下失败用例。

| 编号 | 已确认目标与代码证据 | 受影响用例及闭环条件 |
| --- | --- | --- |
| G1 | 广播区分是否以执行成败判定整体结果，但两种模式均等待全部目标执行完成结果，明确不是投递接受确认。关注执行成败时任一失败则整体失败；不关注时收齐全部执行完成结果即成功，包括全部业务失败。模式配置未找到，现有 Tracker 不能充当需求依据 | MOD-001～004。实现模式配置及 Server→Worker 传递；无可用 Worker 或持续缺少完成反馈已确认按 Job 容错配置重试，耗尽失败；默认模式已确认为关注执行成败（全部成功才成功），省略配置与显式配置均须覆盖。模式配置与反馈缺失检测实现前，受影响用例仍受能力缺口阻塞；不再讨论是否需要等业务完成 |
| G2 | WorkerClientHandler 的 MAP/MAP_REDUCE 均使用 MapReduceJobTracker；其 run 直接转为 MapReduceExecutor，而 MapExecutor 是不同父类型 | MOD-005/006。用真正 MapExecutor 复现并修复入口类型处理；不能通过更换测试执行器类型掩盖 |
| G3 | RetryType 存在 ALL/ONLY_FAIL_PART，但 Java 全局搜索仅找到枚举和 RetryOption 字段，未找到运行读取 | MOD-010/011。核对配置传递、子任务重试范围、结果持久化；ONLY_FAIL_PART 支持完成前明确阻塞，不从枚举存在推断行为已支持 |
| G4 | 已确认 Map 子任务异常遵循 Job 节点容错配置，包括重试次数，不另设策略。WorkerChecker 按持久化运行 Job 找到故障 Worker，调用 JobFailCmd；父 Job 重试路径已发现，子任务 Worker 丢失和旧子结果隔离未完整核对 | MOD-012/013。按 Job 配置核对失联检测、重派范围、次数及结果隔离；分别覆盖允许恢复与重试耗尽，不将任意成功或失败都算通过。剩余为配置生效链路核对，不重新讨论是否另设容错策略 |
| G5 | 用户已确认首个 Job 创建为发布切点。ScheduleCommandService 只在 ScheduleOption 变化时失效 PENDING，未包含 CLAIMED；ExecutionEntityRepo 身份冲突 upsert 为 no-op | VER-002/003。验证仅执行配置变更也失效；并发发布/创建同一事务边界；解决同计划时刻 INVALID 旧记录和重建之间的唯一身份矛盾。不在文档中擅自决定删行、复用行或修改唯一键 |
| G6 | WorkflowCommandService.publish 保存 Workflow 版本，未看到它在该方法内级联更新所有引用 Trigger | VER-004。用真实 Trigger 发布步骤显式切换引用，验证在途旧快照；不把 Workflow 单独发布自动更新全部 Trigger 当成既有承诺 |
| G7 | 部分扫描和租约/Misfire 周期硬编码，Worker liveness 有真实配置；ScheduleLeaseProperties 的存在不能证明当前 Execution 使用它 | 全部快速模式和 BND-006～008。逐项核对生效路径，补测试装配或保留默认耗时，测量后固定预算；默认模式不替换机制 |
| G8 | API 查询未必暴露 Job/租约；Worker Task 使用本地仓储；业务 Context 不提供完整场景元数据 | 所有过程断言。最小测试事件、暂停点及只读快照方案接入并验证事务位置，禁止把最后状态当成全部过程证据 |
| G9 | 现有测试 profile 只有组件、H2 和少量 MySQL 集成；集群编排、选择、并行隔离与报表尚未实现。Podman machine 已确认可用，完整集群容量未测 | TOOL 全组及运行入口。完成真实启动、默认/快速参数、场景选择、隔离、诊断和报告自检；镜像版本和并发默认值在实测后明确 |

代码定位：

- [广播 Tracker](../../fluxion-worker/fluxion-worker-core/src/main/java/io/fluxion/worker/core/job/tracker/BroadcastJobTracker.java)、[Map/Reduce Tracker](../../fluxion-worker/fluxion-worker-core/src/main/java/io/fluxion/worker/core/job/tracker/MapReduceJobTracker.java)、[Worker 派发入口](../../fluxion-worker/fluxion-worker-core/src/main/java/io/fluxion/worker/core/remote/WorkerClientHandler.java)。
- [重试类型](../../fluxion-server/fluxion-server-core/src/main/java/io/fluxion/server/core/executor/option/RetryType.java)、[Worker 故障检查](../../fluxion-server/fluxion-server-core/src/main/java/io/fluxion/server/core/broker/task/WorkerChecker.java)、[Job 失败与重试](../../fluxion-server/fluxion-server-core/src/main/java/io/fluxion/server/core/job/service/JobCommandService.java)。
- [配置发布与预生成](../../fluxion-server/fluxion-server-core/src/main/java/io/fluxion/server/core/schedule/service/ScheduleCommandService.java)、[Execution 身份写入](../../fluxion-server/fluxion-server-core/src/main/java/io/fluxion/server/infrastructure/dao/repository/ExecutionEntityRepo.java)、[Workflow 发布](../../fluxion-server/fluxion-server-core/src/main/java/io/fluxion/server/core/workflow/service/WorkflowCommandService.java)。

## 实现批次与完成标准

| 批次 | 交付 | 验收 |
| --- | --- | --- |
| 1 | 环境生命周期、API 客户端、测试 Executor、事件/只读断言、SCH-001、基础报告 | 从当前代码一条入口真实触发两次，报告/日志完整；启动失败也有结果和清理 |
| 2 | 精确暂停点、WRK-001、BRK 样板与诊断 | 可重复命中故障阶段；保持真实检查器运行；确认身份、次数和终态 |
| 3 | 剩余用例实现，按任务类型及配置闭环 G1～G8 | 所有选中具体用例各有结果，能力缺口显式 BLOCKED；不降低预期，不顺手改业务逻辑 |
| 4 | 串行首失败停止、并行独立环境、JSON/HTML/JUnit 汇总 | TOOL-001～005 自检通过；并行上限及资源开销实测 |
| 5 | 默认参数完整回归与使用文档 | 写入经过验证的命令、具体类/方法映射、实际耗时和环境要求；默认及快速结果可区分 |

“测试设施交付”和“版本可以放行”分别判定：设施能可靠报告已有缺陷，不要求伪造全绿；版本放行则要求选定门禁用例全部 PASS，且没有 BLOCKED、ERROR、TIMEOUT 或 NOT_RUN。本文档整理完成只代表用例和契约已记录，不代表上述实现批次完成。
