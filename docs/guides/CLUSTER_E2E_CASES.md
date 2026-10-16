# 集群端到端回归用例

本文展开 [测试方案](CLUSTER_E2E_TESTING.md) 中第一版全部场景组。以下为待实现的验收定义，不代表已运行通过。环境、观测与报表契约见 [实现与验收约定](CLUSTER_E2E_DELIVERY.md)。

## 共同执行约定

- 每个场景使用独立的 2 Broker、2 Worker 和 MySQL 环境，等待节点注册、心跳及调度所有权就绪后开始。
- 通过 Server API 创建、发布和启用任务，不通过插表或直接调用调度服务触发执行。
- 使用唯一运行标识关联 Trigger、Execution、Job、测试执行器事件及诊断文件。
- 测试执行器事件记录场景、Execution/Job、dispatch attempt、Worker、事件类型和输入/输出。事件由测试程序收集，不能只存于可能被杀死的 Worker 内存中。
- 等待条件使用有限期限轮询或事件通知，不以固定睡眠后读取一次作为通过依据。轮询不能保证捕获短暂状态，必须证明的中间阶段使用测试暂停点或事件证据。
- 故障窗口未命中、节点未就绪、未真实发生定时触发均不能算通过。
- 每行稳定编号对应一个可独立选择的用例。行内明确列出的变体须分别记录结果，例如 `LATE-001/success` 和 `LATE-001/failure`，不能只执行其中一个就宣称覆盖整行。
- 除表中覆盖项外，默认采用 FIXED_RATE + STANDALONE、合法 IANA 时区、无激活截止时间、业务重试关闭。目标触发点、Job 及版本必须显式记录；“不执行”须在覆盖相关扫描/重试周期的有限观察窗口内结合业务事件、数据库与控制点证据判断。
- 启动、场景执行和产物采集分别设置期限；恢复期限按实际心跳、失联判定、扫描、重试及业务运行配置推导并记录，数值待实测。
- finally 路径先采集诊断再清理，仅终止本场景启动的进程、容器和资源。采集错误不得掩盖原始测试失败。

## SCH-001：固定速率真实触发并完成

### 前置条件和输入

- 调度类型 FIXED_RATE，执行模式 STANDALONE，使用产生固定业务输出的测试 Executor。
- 配置合法的短周期和未来首次触发时间，不配置激活截止时间，关闭业务失败重试，以便暴露意外失败。
- 测试控制数据携带唯一标识及已知值，通过 jobId/taskId 关联固定业务标记。当前 Executor.run 返回 void，不虚构参数或返回值接口；使用测试事件验证实际业务输出，框架结果字段按真实协议检查。
- 测试只观察明确选定的连续两个计划触发点，不用全表总行数判定实例唯一性。

### 步骤及证据

1. 通过 API 创建并发布 Trigger 配置，记录发布版本、调度周期和首次触发时间，再启用。
2. 等待 Broker 自动预生成目标触发点的 Execution，记录其身份、triggerAt 和配置版本。
3. 等待真实时间到达，收集 Worker 业务开始、完成事件，以及结果上报后的 Job、Execution 状态。
4. 对第二个计划触发点重复观测，证明调度持续推进。
5. 两个目标实例结束后通过 API 禁用 Trigger，防止继续产生业务执行；只对这两个目标实例作终态断言。

### 通过条件

- 两个目标触发点均有对应 Execution，配置版本与发布版本一致；正常执行路径没有重复创建相同计划身份的有效实例。
- 首批 Job 的 triggerAt 与所属 Execution 一致；Worker 业务开始不早于该计划时刻。正常测试集群使用同机时钟。
- Job 关联正确的 Execution；实际执行器和测试业务输出符合预期，由符合条件的 Worker 执行。
- 两次 Job 与 Execution 均在期限内 SUCCEED，业务失败重试次数为零，没有意外 FAILED、INVALID 或 EXPIRED。
- 不将全局业务执行恰好一次作为框架保证；记录所有 attempt 和业务事件，异常重复交由具体派发规则分析。

### 失败产物

记录失败阶段（API 准备、预生成、等待触发、派发、执行、上报或终态），保存两个 Broker 和两个 Worker 日志，以及目标 Trigger、Schedule、Execution、Job 与相关节点/租约快照；超时采集存活 Java 进程线程栈。

## WRK-001：Worker 执行中强制退出，存活 Worker 恢复

### 前置条件和输入

- FIXED_RATE + STANDALONE，两台 Worker 都提供同名测试 Executor 且符合相同筛选条件。
- 故障恢复及失败重试配置允许至少一次恢复，不设置会提前结束本场景的激活截止时间；运行超时长于故障发现与恢复预算，以便区分离线恢复和运行超时恢复。
- 测试 Executor 在首次业务执行开始后，先向测试程序报告身份和 attempt，再阻塞于测试控制点；恢复 attempt 在存活 Worker 上可以正常完成。
- 初次 Worker 无须预先指定：根据已收到的业务开始事件选择真正承载目标 Job 的进程，不能随机杀另一台 Worker。

### 步骤及证据

1. 通过 API 创建、发布、启用调度，等待真实定时触发。
2. 确认目标 Job 已进入运行阶段，并收到包含其 jobId、attempt 和 Worker 身份的业务开始事件；确认该执行尚未结束。
3. 对对应测试 Worker 进程发送强制退出信号，确认进程已退出，记录故障时间；保持两个 Broker 和另一台 Worker 存活。
4. 等待真实心跳失联检测和恢复任务推进，不手工修改节点状态或调用接管服务。
5. 等待存活 Worker 接受并完成恢复派发，收集新 attempt 的业务开始、完成和结果上报证据。
6. 等待目标 Execution 终态，通过 API 禁用 Trigger。故障 Worker 的重新加入属于独立 REJ 用例，本用例不提前重启它。

### 通过条件

- 故障确实发生在目标 Job 的业务开始后、完成前；被杀进程属于本场景，存活 Worker 未受影响。
- Broker 将故障 Worker 判为 OFFLINE，并按配置触发恢复；短暂迁移过程通过事件、日志或专用证据观察，不能仅凭最终数据库快照推断已验证。
- 恢复沿用同一 Execution 和稳定 jobId，不重新创建另一份运行单位代替原 Job。
- 恢复派发使用有效的新 dispatch attempt 和存活 Worker 身份。当前 WorkerChecker 经 JobFailCmd 进入 RetryOption 链路，本用例仅注入一次故障，成功恢复时 retryTimes 应为 1；不能把 dispatchAttempt 与 retryTimes 当成同一个计数。
- 存活 Worker 的实际执行器及配置版本正确，业务输出符合预期，Job 和 Execution 在恢复期限内 SUCCEED。
- 所有权、租约及上报身份与恢复 attempt 一致，不因两个 Broker 同时检查而产生重复恢复副作用。需要短暂过程证据时补充测试观测，不能用一次终态查询替代。
- 不检查已被杀进程的迟到结果；迟到上报由 LATE 场景验证。

### 失败产物

除共同日志和数据库快照外，保存故障目标 PID/节点身份、退出确认、首次业务开始事件、故障前后 attempt/重试/归属变化、恢复 Worker 事件及恢复预算。报告区分未命中故障窗口、未检测离线、未恢复派发、恢复执行失败和终态未收敛。

## 报表验收样例

每个场景至少输出稳定 ID、配置摘要、执行模式、开始/结束时间、状态、失败阶段、断言列表和产物路径。WRK-001 额外显示故障节点、故障时间、恢复节点及恢复耗时；没有成功观测恢复时不填造恢复耗时。

串行模式若 SCH-001 失败，WRK-001 仍有一条未执行记录，注明前序失败。并行模式两者使用独立集群，各自产出结果。环境启动失败记录为错误，不伪装为业务断言失败；任何选中用例都不能从报告中消失。

## 其余用例的共同结构

以下各表的“准备与动作”是该用例独立执行的步骤，“断言”是通过条件。全部继承前述环境就绪、API 准备、期限、诊断和清理约定。表中 G 编号指 [能力缺口](CLUSTER_E2E_DELIVERY.md)；存在缺口不代表取消用例，不得标成通过。

### 正常调度与执行链路

| 编号 | 准备与动作 | 结果及过程断言 |
| --- | --- | --- |
| SCH-002 | CRON + 明确 cronType/timeZone，选择未来连续两个匹配时刻，启用并等待真实触发 | 两个时刻均完成；triggerAt 与指定时区的 Cron 计划一致，不提前执行；实际执行及配置版本正确 |
| SCH-003 | FIXED_DELAY，首轮 Executor 阻塞，超过一个 interval 后释放；等待第二轮 | 首轮未结束时不因 interval 到期产生后续业务执行；下一触发点以终态反馈时刻加 interval 推进，两轮成功 |
| SCH-004 | FIXED_DELAY，重试关闭，首轮业务失败，第二轮成功 | 首轮 FAILED 仍产生反馈，Schedule 不停摆；第二轮触发点基于失败反馈，最终成功 |
| EXE-001 | Workflow START → A → B → END，在 A 暂停后释放 | A 未完成时 B 不运行；节点按顺序推进，END 成功后 Execution 才成功；后续节点使用自身就绪时间语义 |
| EXE-002 | Workflow START → A/B → END，一支快速完成，另一支阻塞 | 快支完成不能令 Execution 提前成功；释放慢支后才汇合；Job 与节点身份对应，无重复创建同一节点 Job |

STANDALONE 正常用例由 SCH-001 承担，不另建同义编号。时间计算的夏令时穷举保留给已有组件测试；SCH-002 使用可等待的真实未来时刻，不修改系统时钟。

### 重试、运行超时和迟到结果

| 编号 | 准备与动作 | 结果及过程断言 |
| --- | --- | --- |
| RET-001 | retryTimes=1，retryInterval=R>0；首次业务抛异常，第二次成功 | 同一 jobId 重试，retryTimes=1；新 attempt 在允许重试时间到达后运行；第一次失败记录保留，Job/Execution 最终 SUCCEED |
| RET-002 | retryTimes=1，两次业务均失败 | 初次执行加一次重试后 FAILED，失败记录完整；观察至少覆盖一次额外重试扫描，不能再启动第三次；Workflow 策略关闭 |
| RET-003 | retryTimes=0，业务阻塞超过运行超时 | 超时检查进入失败路径，Job/Execution FAILED；无业务失败重试；与 Deadline EXPIRED 区分，最后释放并清理阻塞执行 |
| LATE-001 | 超时后允许一次重试；旧 attempt 阻塞，新 attempt 分别成功或重试耗尽失败，再释放旧 attempt | 两个终态变体各自验证：旧成功上报不覆盖当前成功/失败状态，不重推 Workflow 或调度反馈 |
| LATE-002 | 首次超时，阻塞恢复 attempt 使其仍在运行；释放旧 attempt，并分别产生成功/失败迟到上报 | 旧 attempt 的两种结果均不结束当前 Job，不增加额外重试；再释放当前 attempt 并核对其结果被接受 |

迟到结果必须实际到达 Broker 的结果校验入口。若框架在 Worker 本地已拦截旧执行，测试需通过测试专用报文回放证明 Broker fencing，保留原 jobId、旧 attempt 和旧 Worker 身份；仅看到业务结束事件不能声称验证了 Broker 拒绝旧结果。普通业务异常仍通过真实 Executor 产生。

### Workflow 失败策略

| 编号 | 准备与动作 | 结果及过程断言 |
| --- | --- | --- |
| WFL-001 | A → B，A 一次重试仍失败，continueOnFailure=false | A Job 保持 FAILED，B 不执行，Execution FAILED；失败前不能提前运行 B |
| WFL-002 | 同上，continueOnFailure=true，B 成功 | A Job 仍 FAILED，重试耗尽后才推进 B；END 成功后 Execution SUCCEED，不把 A 状态改成成功 |
| WFL-003 | 并行 A/B，A 耗尽重试且允许继续，B 保持阻塞 | A 的可继续策略不等于整个 Workflow 完成；B 完成并汇合后才到 END 和成功终态 |

### 广播、MAP、MAP_REDUCE

| 编号 | 准备与动作 | 结果及过程断言 |
| --- | --- | --- |
| MOD-001 | BROADCAST 关注结果模式，两台匹配 Worker，均成功；分别省略模式配置和显式指定关注执行成败 | 省略配置与显式关注执行成败的行为一致；记录实际目标集合，每个目标有对应任务；全部完成后 Job/Execution 成功。受 G1 阻塞 |
| MOD-002 | 关注执行成败模式，一个成功一个失败；分别省略模式配置和显式指定，并控制成功先到、失败先到，重试关闭 | 两种到达顺序均整体 FAILED，不依赖最后回调类型，不因部分成功判成功。受 G1 阻塞 |
| MOD-003 | BROADCAST 不关注执行成败，先让部分目标完成，另一个业务仍阻塞，再释放；分别全部成功、部分业务失败、全部业务失败 | 业务仍阻塞时即使已收到投递接受确认也不能结束；全部执行完成结果收齐后，三个变体均整体成功；必须区分已收到失败结果与尚未收到结果 |
| MOD-004 | 两种广播模式分别覆盖无可用 Worker，以及一个已完成、另一个未接受或执行结果未返回；配置一次重试，分别恢复成功和耗尽 | 缺少任一目标执行完成反馈时不得宣称已完成；按 Job 配置重试，耗尽后 FAILED；恢复成功时按所选模式判断实际执行结果，记录重试次数，不把结果缺失当成已收到失败结果 |
| MOD-005 | MAP 使用真正的 MapExecutor，确定分片集合 {1,2,3}，全部成功 | 实际拆分和完成的分片集合完整，Job/Execution 成功，不调用 Reduce；不得用 MapReduceExecutor 冒充纯 MAP 绕过 G2 |
| MOD-006 | MAP，分片 2 失败，重试关闭；分别控制失败结果先到、最后到 | 均在完整结果收敛后整体 FAILED，无遗漏分片被算成成功；两种结果到达顺序都执行 |
| MOD-007 | MAP_REDUCE，分片 1/2/3，最后一片暂停，其余成功，再释放 | 全部结果完整前 Reduce 不开始；Reduce 收到完整 taskId→结果集合，测试聚合值正确，Job/Execution 成功；通过测试事件检查聚合值，不假定 reduce 有返回值 |
| MOD-008 | MAP_REDUCE，分片 2 失败，重试关闭 | Job/Execution FAILED，Reduce 不运行，保留失败分片与错误；已成功片不能掩盖失败片 |
| MOD-009 | MAP_REDUCE，全部分片成功，Reduce 抛异常，重试关闭 | 记录失败阶段为 Reduce；Job/Execution 在期限内 FAILED，不错误成功或永久 RUNNING；实际异常收敛能力待验证 |
| MOD-010 | MAP_REDUCE，一次业务失败重试后成功；retryType=ALL | 对比两个 attempt 的完整分片事件集合及 Reduce 事件，重试范围符合 ALL，不沿用旧 attempt 混合结果。受 G3 的结果隔离核对影响 |
| MOD-011 | MAP_REDUCE，固定一个失败片，一次重试；retryType=ONLY_FAIL_PART | 成功片不因业务重试重跑，仅恢复失败片，最终 Reduce 输入完整；枚举虽存在但实际处理尚未找到，受 G3 阻塞 |
| MOD-012 | MAP、MAP_REDUCE 两个变体：一台 Worker 管理父 Job，另一台执行受控分片；只杀分片 Worker；各自覆盖 Job 节点允许重试和重试耗尽配置 | 必须证明目标是子任务执行节点且父 Tracker 仍存活；按 Job 节点容错配置检查恢复范围、次数与预期成功/失败，不另设恢复规则；不永久挂起、不提前聚合。配置到子任务恢复的实现链路待 G4 核对 |
| MOD-013 | MAP_REDUCE：证明父 Job Tracker 所在 Worker 已开始管理分片，再杀该 Worker，允许一次 Job 重试 | 父 Job 按 WRK-001 的失败重试链恢复，同一 jobId 新 attempt；旧子任务结果不得混入新 attempt，完整恢复后才聚合。子任务跨 attempt 隔离待 G4 核对 |

这里的“分片 1/2/3”和聚合值通过测试控制数据及 Task ID 定义，不给当前 void Executor 接口虚构参数/返回协议。广播重试粒度复用同一 RetryType 需求，但不通过 MAP_REDUCE 用例宣称已覆盖广播；G1 闭环时再确定需要增加的广播重试子例。

### Broker 和 Worker 故障、节点重新加入

| 编号 | 准备与动作 | 结果及过程断言 |
| --- | --- | --- |
| BRK-001 | 目标 Execution 已提交但无 Job，分 pending 和 claimed-before-job 两个变体，暂停后杀其 Owner Broker；另一 Broker 保持存活 | Bucket 转移及租约恢复后仅一个有效激活，沿用 Execution 身份，首批 Job 不重复创建；过期 token 不能继续创建 Job |
| BRK-002 | 在创建 Job 事务内、提交前暂停，杀 Broker | 数据库回滚半创建状态；存活 Broker 恢复后 Job 与 Execution 关系完整，不出现 RUNNING 却没有首批 Job；不得在持锁暂停点同步等待一个必须取得同锁的操作 |
| BRK-003 | Job 创建事务提交成功、任何网络派发前暂停并杀 Broker | 存活 Broker 仅派发已持久化 Job，jobId/executionId 不变，不再次创建首批 Job；业务成功并收敛 |
| BRK-004 | Worker 业务已开始并阻塞，杀持有目标 Job 恢复归属的 Broker；保持 Worker 活着，再释放业务 | 新 Broker 取得有效恢复归属后状态可收敛；若发生重派允许至少一次语义，但不重建 Execution/Job；只接受有效 attempt/Worker 结果 |
| WRK-002 | 同 WRK-001，但 retryTimes=0 | 离线被检测后 Job/Execution FAILED，不错误承诺所有故障均自动成功，不出现新的业务重试 |
| REJ-001 | 完成 BRK-003 的接管及目标实例终态后，重启故障 Broker | 恢复注册和心跳，无双 Owner；选一个实际归其 Bucket 的后续触发验证其能工作，旧已完成实例不再次激活 |
| REJ-002 | 完成 WRK-001 恢复后重启故障 Worker，使后续用例任务仅匹配该 Worker | 重新注册且符合筛选后能接受新任务；旧 Job 不被本地残留任务错误上报或推进；保留重启前后身份及本地任务存储信息 |

故障阶段以数据库事务事实和控制事件为准，而非仅按枚举名称推断。CLAIMED 可能来自预加载，不保证计划时刻已经到达；禁用和 Job 创建的校验位置仍以实际激活事务为准。

### Trigger 禁用与配置发布

| 编号 | 准备与动作 | 结果及过程断言 |
| --- | --- | --- |
| ENA-001 | 首批 Job 创建前暂停；通过 API 禁用，确认请求完成，再释放激活 | 激活校验发现禁用，不创建或执行首批 Job；对应 Execution INVALID。控制点必须让禁用操作可提交，不能自己持锁导致假死 |
| ENA-002 | 在未来触发点前禁用，随后重新启用；确认该 Execution 尚未因禁用被激活校验作废 | 实际首批启动校验时已启用，正常执行；不要求复活一个已 INVALID 的实例 |
| ENA-003 | Workflow A 运行中暂停，通过 API 禁用，再释放 A | 后续 B、END 继续，无后续 Trigger 启用校验导致中断；同时检查禁用后的新触发不会启动 |
| ENA-004 | MAP 和 MAP_REDUCE 两个变体，首批已启动，拆分或子任务未完成时禁用，随后继续 | 后续分片/Reduce 按原执行推进，不因 Trigger 禁用被拒绝；MAP_REDUCE Reduce 仍等待完整结果 |
| VER-001 | 旧配置存在未创建 Job 的 PENDING，API 发布改变调度时间规则的新版本 | 旧实例 INVALID；新时间规则产生可执行实例且版本正确；旧实例不派发，作废记录可追踪 |
| VER-002 | 仅改变执行器名称（预装两个测试 Executor），不改变调度时间规则，再发布 | 旧未激活实例失效，后续实际运行新执行器，不能仅修改数据库版本字段；同一计划时刻的替换须解决 G5 身份约束，不能通过移动时间规避 |
| VER-003 | CLAIMED 尚无 Job，发布与 Job 创建并发，用暂停点分别控制发布先提交和 Job 创建先提交 | 发布先赢：旧实例 INVALID 且无 Job；Job 创建先赢：旧快照继续；不允许 INVALID 仍创建/派发 Job。受 G5 阻塞 |
| VER-004 | Workflow V1 的 A 已运行且 B 尚未创建；发布 V2 并通过 Trigger 发布流程切换后续引用，再释放 A | 在途实例 B 继续 V1，后续新 Execution 使用 V2。Workflow 单独发布不等同于自动更新所有 Trigger；检查实际引用版本，受 G6 核对影响 |

V1/V2 均为 Server 配置快照，测试用不同执行器名称或节点关系区分实际执行，不测试 Worker 业务代码升级兼容性。发布和禁用是不同语义：发布使未激活旧版本失效；禁用仅在首批启动时校验，不主动批量删记录。

### Deadline、Misfire 与遗漏触发恢复

| 编号 | 准备与动作 | 结果及过程断言 |
| --- | --- | --- |
| BND-001 | maxStartDelayMillis 合法非空，已创建 Execution；在 Broker 激活前暂停至 Deadline 后 | Execution EXPIRED，不运行业务，不按 Job 失败重试；未创建 Job 时不要求出现 Job 行 |
| BND-002 | Broker 派发前尚未过 Deadline，在 Worker 新建 Tracker 前暂停至 Deadline 后，再继续 | Worker 返回 REJECTED_DEADLINE，已有 Job 与 Execution EXPIRED，不运行业务、不重试；Broker 必须处理拒绝结果 |
| BND-003 | 首批业务在 Deadline 前已开始，延迟到 Deadline 后才完成；Workflow 后续节点再继续 | 不把 Deadline 当运行超时，不阻断已启动业务和 Workflow 后续节点；未配置运行超时时正常成功 |
| BND-004 | 无 Deadline，已创建的 PENDING 被测试暂停至超过 Misfire 阈值，misfirePolicy=SKIP | Execution SKIPPED，留审计记录，不创建首批 Job；与未物化历史触发点直接丢弃区分 |
| BND-005 | 已创建且迟到的 Execution，FIRE_RETRY，maxFireAttempts=2；测试注入创建 Job 提交前失败，分别第一次失败后成功、两次均失败 | 成功变体最终激活；耗尽变体 MISFIRED；fireAttempt 计数受上限约束，重试前未留下半创建 Job；不混用 Job.retryTimes |
| BND-006 | 分别使用 CRON/FIXED_RATE，recoveryEnabled=false；暂停预生成入口形成未物化历史缺口，再恢复 | 历史遗漏点不创建 Execution（包括 SKIPPED），游标推进，未来触发继续；观察范围必须证明停顿前这些点尚无记录 |
| BND-007 | 同上 recoveryEnabled=true，产生超过 10 个、超过 5 秒且尚未创建的遗漏点，FIRE_RETRY，无 Deadline | 恢复处理最多最近 10 个原始遗漏点，更早点丢弃；恢复与未来点区分计数；游标前进且下一轮不重复补建 |
| BND-008 | 恢复开启，分别配置 SKIP 且满足迟到条件、或 Deadline 已过，再恢复预生成 | 两个变体的不可执行恢复点直接丢弃，不先创建 SKIPPED/EXPIRED 记录；对应恢复/丢弃汇总可观测 |
| BND-009 | FIXED_DELAY + recoveryEnabled=true，通过 API 发布 | 发布被明确拒绝，不激活无效配置，不产生有效调度副作用 |
| BND-010 | FIXED_DELAY，分别使 Execution 因禁用 INVALID 或激活过期 EXPIRED | 终态反馈被记录；禁用变体重新启用后能继续，过期变体的后续触发能推进，不永久停摆；避免用新发布重置状态掩盖反馈缺失 |

遗漏触发用例必须制造“未创建 Execution”的缺口，单纯暂停派发已预生成记录只能验证 Misfire。优先使用不推进游标的测试专用预生成暂停点；若改用整个集群停机，须等待超过预生成窗口并记录实际缺口。此处仅作构造方法，不增加全群故障的独立验收承诺。短参数模式也不能暗改 10 点恢复上限或 5 秒业务判断边界。

### 测试工具与报表自身验收

这些是测试基础设施自检，不伪装为调度业务场景。可以用小型测试替身验证编排与报告，无需每次启动完整集群。

| 编号 | 准备与动作 | 结果及过程断言 |
| --- | --- | --- |
| TOOL-001 | 串行选择 A/B/C，A 通过、B 断言失败 | A=PASS、B=FAIL、C=NOT_RUN；仍生成三个条目和完整报告，命令非零退出 |
| TOOL-002 | 并行选择 A/B，A 注入进程故障并失败，B 正常执行 | 端口、数据库、本地存储、控制事件和产物互不影响；B 正常产出结果，汇总失败；不能把资源隔离等同于免受宿主机负载影响 |
| TOOL-003 | 分别注入镜像/数据库或 Java 启动失败、未命中暂停点超时 | ERROR/TIMEOUT 明确区分；部分启动资源清理，已选择用例都有记录，不能报“零测试成功” |
| TOOL-004 | 场景断言失败，同时令诊断采集的一个步骤失败 | 原始失败不丢失，记录采集错误，报告仍可打开；清理仍尝试执行 |
| TOOL-005 | 选择不存在的编号、重复编号，或选择到能力缺口用例 | 未知编号执行前报选择错误并生成运行摘要；重复编号去重；缺口用例 BLOCKED，不能静默跳过或导致整次运行全绿 |

## 覆盖映射与状态

| 方案场景组 | 本文用例 |
| --- | --- |
| SCH | SCH-001～004 |
| EXE | EXE-001～002，单 Executor 复用 SCH-001 |
| MOD | MOD-001～013，STANDALONE 复用 SCH/RET/WRK |
| WFL | WFL-001～003 |
| RET | RET-001～003 |
| LATE | LATE-001～002 |
| BRK | BRK-001～004 |
| WRK | WRK-001～002 |
| REJ | REJ-001～002 |
| BND | BND-001～010 |
| VER | VER-001～004 |
| ENA | ENA-001～004 |
| 测试工具自检 | TOOL-001～005 |

全部用例当前均为“已定义，未实现/未执行”。具体配置缺口与待决策项集中在能力缺口表，未解决不意味着文档遗漏；实现时应将用例编号映射到 JUnit 方法和变体，报告按选中的全部具体用例逐项列出。
