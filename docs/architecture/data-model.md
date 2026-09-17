# 数据模型

本文档详细说明 Fluxion 的数据库设计。

## 实体关系图

以 `V20250101__init.sql` 为准的真实关系：

```
fluxion_app 1──* fluxion_worker 1──1 fluxion_worker_metric
                    │
                    └──* fluxion_worker_executor

fluxion_worker  *──(ref: fluxion_tag.ref_id, ref_type=WORKER)
fluxion_trigger *──(ref: fluxion_tag)

fluxion_trigger 1──* fluxion_execution 1──* fluxion_job 1──* fluxion_job_record
   │            (trigger_id;             (execution_id;    (job_id, times)
   │             uk(trigger_id,trigger_at))
   └──(配置版本快照: fluxion_version ref_id/ref_type=TRIGGER)

fluxion_workflow ──(被 trigger 引用为 Executable；配置版本快照 ref_type=WORKFLOW)
                └──1──* fluxion_job (workflow 节点各产生一个 job; ref_id=节点引用的 executor)

fluxion_broker 1──* fluxion_bucket (broker_id)      # bucket 分片归属
fluxion_execution / fluxion_job 按自身 bucket 列与 Broker 对齐

fluxion_id        # 全局发号器 (uk: type)
fluxion_lock      # 通用分布式锁 (uk: name)
fluxion_version   # Trigger/Workflow 版本快照 (uk: ref_id, ref_type, version)
```

> 说明：
> - 「调度（Schedule）」不是独立实体表的对外关系：SCHEDULE 类型 Trigger 的调度参数存于 `fluxion_schedule`（通过 trigger 概念关联），每个计划触发点产生一行 Execution。
> - 旧图的 `fluxion_schedule_delay` / `fluxion_delay_schedule` / `fluxion_execution_instance` 已删除。
> - Execution 持有 `dispatch_attempt` fencing；Job 持有自己的 lease / retry 字段（两层租约，见 [CONTEXT.md](../../CONTEXT.md)）。

## 核心表结构

### 1. 应用管理

#### fluxion_app

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT UNSIGNED | 自增主键 |
| app_id | VARCHAR(64) | 应用唯一标识（`uk_app`） |
| app_name | VARCHAR(255) | 应用名 |

### 2. 节点管理

#### fluxion_broker

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT UNSIGNED | 自增主键 |
| host / port / protocol | VARCHAR(255) / INT / VARCHAR(64) | 地址（`uk_broker(host, port)` 唯一） |
| broker_load | INT | 负载水位（选路参考） |
| last_heartbeat_at | DATETIME(3) | 心跳时间（`idx_broker_last_heartbeat`）；`BrokerManger` 依此判上下线 |

#### fluxion_worker

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT UNSIGNED | 自增主键 |
| worker_id | VARCHAR(64) | Worker 唯一标识（`uk_worker`） |
| app_id | VARCHAR(64) | 所属应用（`idx_app`） |
| host / port / protocol | VARCHAR(255) / INT / VARCHAR(64) | Worker 地址 |
| status | VARCHAR(32) | ONLINE / OFFLINE（`WorkerChecker` 按 6 秒未心跳判离线） |
| is_enabled | BIT(1) | 是否启用（禁用不再参与派发） |

Worker 标签存于 `fluxion_tag`（ref_type=WORKER）；执行器清单存于 `fluxion_worker_executor`。

#### fluxion_worker_executor

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT UNSIGNED | 自增主键 |
| worker_id | VARCHAR(64) | 所属 Worker |
| name | VARCHAR(255) | 执行器名（`uk_worker_executor(worker_id, name)`） |

#### fluxion_worker_metric

选路用的最新负载快照（`uk_worker_metric(worker_id)`，一 Worker 一行）：

| 字段 | 类型 | 说明 |
|------|------|------|
| worker_id | VARCHAR(64) | 所属 Worker |
| cpu_processors | INT | CPU 核数 |
| cpu_load | FLOAT | CPU 负载（LEAST_CPU_LOAD 选路用） |
| free_memory | BIGINT | 空闲内存 |
| available_queue_num | INT | 剩余可排队数 |
| last_heartbeat_at | DATETIME(3) | 随心跳更新（`idx_worker_last_heartbeat`） |

### 3. 任务管理

#### fluxion_job

Job 是 Execution 内的**运行单位**（见 [CONTEXT.md](../../CONTEXT.md)「Job（运行单位）」），与旧设计的“任务定义表”不同：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT UNSIGNED | 自增主键 |
| job_id | VARCHAR(64) | 全局唯一（`uk_job`），跨 Broker/Worker 的幂等键 |
| execution_id | VARCHAR(64) | 所属 Execution（与 ref_id 构成 `uk_job_execution`） |
| bucket | INT UNSIGNED | 所属 bucket（恢复扫描分片） |
| job_type | VARCHAR(32) | NORMAL / BROADCAST / MAP_REDUCE（见 [job-types.md](./job-types.md)） |
| ref_id | VARCHAR(64) | 引用对象 ID（Executor 的 refId / Workflow 节点 ID） |
| status | VARCHAR(32) | JobStatus 字符串（见 [job-types.md](./job-types.md) 状态流转） |
| trigger_at / start_at / end_at | DATETIME(3) | 本次执行时间线 |
| worker_address | VARCHAR(64) | 派发到的 Worker 地址 |
| last_report_at | DATETIME(3) | 最近上报时间 |
| retry_times | INT UNSIGNED | 已重试次数 |
| result / error_msg | TEXT | 结果与错误 |
| monitor | VARCHAR(255) | 监控标注 |
| dispatch_attempt / lease_owner / lease_until / timeout_at / next_retry_at | INT / VARCHAR / DATETIME(3) | V20260728 容错字段：派发版本、Job 租约、超时与下次重试时间 |

**配置 JSON 结构**（DispatchOption / RetryOption / OvertimeOption 不存于本表；它们属于 Trigger/Workflow 配置，以版本快照形式存于 `fluxion_version.config`）：



```json
// dispatch_option
{
  "loadBalanceType": "ROUND_ROBIN",
  "tagFilterOption": {
    "tags": [{"key": "env", "value": "production"}]
  },
  "appointWorkerIds": ["worker-001"]
}

// retry_option
{
  "retryType": "ON_FAILURE",
  "maxRetryTimes": 3,
  "retryIntervalSeconds": 30
}

// overtime_option
{
  "timeoutSeconds": 300
}
```

#### fluxion_schedule

SCHEDULE 类型 Trigger 的调度参数（见 [CONTEXT.md](../../CONTEXT.md)「Schedule（调度）」）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT UNSIGNED | 自增主键 |
| schedule_id | VARCHAR(64) | 唯一标识（`uk_schedule`） |
| bucket | INT UNSIGNED | 所属 bucket |
| schedule_type | VARCHAR(64) | FIXED_RATE / FIXED_DELAY / CRON 类型编码（schedule_delay/schedule_interval/schedule_cron + schedule_cron_type 组合表达） |
| start_time / end_time | DATETIME(3) | 调度窗口 |
| schedule_delay | BIGINT | FixedDelay 间隔秒数 |
| schedule_interval | BIGINT | FixedRate 间隔秒数 |
| schedule_cron / schedule_cron_type | VARCHAR(128) / VARCHAR(32) | Cron 表达式与类型 |
| misfire_policy | VARCHAR(32) | SKIP / FIRE_RETRY（默认 FIRE_RETRY） |
| max_fire_attempts | INT UNSIGNED | FIRE_RETRY 允许尝试创建 Job 的总次数（默认 1） |
| last_trigger_at / last_feedback_at / next_trigger_at | DATETIME(3) | 上次触发/上轮反馈/下次触发时间 |
| is_enabled | BIT(1) | 启用状态 |
| KEY | | `idx_next_trigger_start_end_bucket(next_trigger_at, start_time, end_time, bucket)` |


#### fluxion_trigger

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT UNSIGNED | 自增主键 |
| trigger_id | VARCHAR(64) | 唯一标识（`uk_trigger`） |
| name / description | VARCHAR(255) | 名称与描述 |
| publish_version | VARCHAR(64) | 已发布版本（Execution 固化该版本的配置） |
| draft_version | VARCHAR(64) | 草稿版本 |
| is_enabled | BIT(1) | 启用状态（Broker 在创建 Job 前校验） |

Trigger 配置（含 ExecuteConfig、DispatchOption、RetryOption 等 JSON）以版本快照存于 `fluxion_version.config`，按 `ref_id=triggerId, ref_type=TRIGGER` 关联。任意发布切换 `publish_version`，既有 `PENDING` Execution 置 `INVALID`（见 CONTEXT.md「Trigger 配置发布」）。


### 4. 执行记录

#### fluxion_execution

调度实例与执行实例合并后的唯一事实记录（见 [CONTEXT.md](../../CONTEXT.md)「Execution（执行实例）」）。字段以 `V20250101__init.sql` 为准：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT UNSIGNED | 自增主键 |
| execution_id | VARCHAR(64) | 全局唯一 Execution 标识（`uk_execution`） |
| trigger_id | VARCHAR(64) | 所属 Trigger（与 trigger_at 构成唯一键 `uk_execution_trigger`） |
| trigger_type | VARCHAR(64) | 触发类型（SCHEDULE / WEBHOOK 等） |
| executable_id / executable_type / executable_version | VARCHAR(64) | 创建时固化的 Executable 版本快照 |
| status | VARCHAR(32) | Execution 状态（字符串，见下方流转） |
| trigger_at | DATETIME(3) | 计划触发时间（身份组成之一） |
| start_at / end_at | DATETIME(3) | 运行起止时间 |
| worker_id | VARCHAR(64) | 执行 Worker |
| dispatch_attempt | INT | Worker 派发尝试版本（fencing 依据之一） |
| state_updated_at | DATETIME(3) | 最近状态迁移时间 |
| lease_owner / lease_until / execution_token | VARCHAR(64) / DATETIME(3) | Broker 一次性领取租约与交接围栏令牌 |
| fire_attempt | INT UNSIGNED | misfire 补偿尝试创建 Job 的次数（策略 FIRE_RETRY 用） |
| next_fire_at | DATETIME(3) | 允许被 claim 的最早时间（漏触发/失败重试点亮） |
| bucket | INT UNSIGNED | 所属 bucket（Broker 分片） |
| recovery_owner | VARCHAR(64) | 恢复接管者（与 worker_id 分离） |
| is_deleted / created_at / updated_at | BIT / DATETIME | 通用审计列 |

**唯一键**：`uk_execution(execution_id)`；`uk_execution_trigger(trigger_id, trigger_at)` —— 一个 Schedule 触发点只能有一个 Execution。

**状态流转（字符串值，代码 `ExecutionStatus`）：**

```
PENDING → CLAIMED → RUNNING → SUCCEED / FAILED        （主链路）
PENDING → SKIPPED / MISFIRED                          （misfire 策略）
CLAIMED → INVALID                                     （Trigger 未启用校验）
CLAIMED → PENDING                                     （releaseForRetry，next_fire_at += 1s）
```

状态语义以 [CONTEXT.md](../../CONTEXT.md)「Execution 状态」与 [execution-state.md](./execution-state.md) 为权威。旧表的 `WAITING / DISPATCHING / TINYINT 数字编码` 已废弃。

#### fluxion_job_record

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT UNSIGNED | 自增主键 |
| job_id | VARCHAR(64) | 所属 Job |
| times | INT UNSIGNED | 第几次执行记录（`uk_job_record(job_id, times)`） |
| start_at / end_at | DATETIME(3) | 本次执行起止 |
| status | VARCHAR(32) | 本次执行状态 |
| worker_address | VARCHAR(64) | 实际执行的 Worker 地址 |
| result / error_msg | TEXT | 本次结果与错误 |

按执行次数记录 Job 的每次派发-执行-上报周期（Attempt 的持久化留痕）。旧文档的 `fluxion_execution_instance` 表不存在，广播/MapReduce 各 Worker 的执行留痕同样落在本表与 `fluxion_job`。

### 5. 延迟调度

延迟调度不再使用单独的存储表。当前实现：每个计划触发点（无论是 Cron、固定速率、延迟触发还是漏触发补偿）都通过 `fluxion_execution` 以唯一索引 `(trigger_id, trigger_at)` 创建一行 `Execution`，状态进入 `PENDING`，依赖调度引擎在 `next_fire_at` 到点时 claim。

字段语义（参见 §4 `fluxion_execution`）：

| Execution 字段 | 延迟调度场景下的含义 |
|------|------|
| `status` | 调度创建后为 `PENDING`；漏触发后由 misfire 策略写 `MISFIRED` / `SKIPPED`；运行成功为 `SUCCEED`，运行失败为 `FAILED` |
| `trigger_at` | 该 Execution 实例对应的应触发时刻（身份组成之一） |
| `next_fire_at` | 允许被 claim 的最早时间（重试/漏触发时会重写） |
| `fire_attempt` | 已尝试领取次数（漏触发后会递增） |

> misfire 判定阈值不是表字段：全局常量 `ExecutionScheduleCommandService.MISFIRE_THRESHOLD_SECONDS = 5`，
> 当 `now - trigger_at > 5s` 时应用 misfire 策略（`SKIP` / `FIRE_RETRY.maxFireAttempts` 配置在 `fluxion_schedule` 表）。


无需新增 schema 变更；历史 `fluxion_schedule_delay` / `fluxion_delay_schedule` 表已随 ScheduleDelay → Execution 合并删除，历史数据存储在新库（干净数据库验证）中。

> 若需快速查询哪些 Execution 尚未被 claim：
> ```sql
> SELECT id, trigger_id, trigger_at, next_fire_at, fire_attempt
>   FROM fluxion_execution
>  WHERE status = 'PENDING' AND next_fire_at <= NOW(3) AND trigger_at <= NOW(3)
>  ORDER BY trigger_at
>  LIMIT 100;
> ```

### 6. 工作流

#### fluxion_workflow

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT UNSIGNED | 自增主键 |
| workflow_id | VARCHAR(64) | 唯一标识（`uk_workflow`），可被多个 Trigger 复用 |
| name / description | VARCHAR(255) | 名称与描述 |
| publish_version / draft_version | VARCHAR(64) | 已发布 / 草稿版本 |

DAG 配置 JSON 以版本快照存于 `fluxion_version.config`（ref_type=WORKFLOW）。规则约束（恰好一个 `END` 节点、并行分支汇合、`continueOnFailure`）见 [CONTEXT.md](../../CONTEXT.md)。

**dag_config JSON 结构示例：**



```json
{
  "nodes": [
    {"id": "node-1", "type": "START",       "nextNodes": ["node-2"]},
    {"id": "node-2", "type": "EXECUTOR",    "executorId": "exec-xxx", "nextNodes": ["node-3"],
     "continueOnFailure": false},
    {"id": "node-3", "type": "END"}
  ]
}
```

> 节点连接用 `nextNodes` 表达（无独立 edges 数组）；全图必须**恰好一个 `END` 节点**，所有并行分支汇合至它（见 CONTEXT.md「Workflow 结束条件」）。

### 7. 标签系统

#### fluxion_tag

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT UNSIGNED | 自增主键 |
| ref_id | VARCHAR(64) | 引用对象 ID |
| ref_type | VARCHAR(64) | 引用类型（WORKER / TRIGGER 等） |
| tag_name | VARCHAR(128) | 标签名 |
| tag_value | VARCHAR(255) | 标签值 |

唯一键 `uk_tag(ref_id, ref_type, tag_name, tag_value)`；关键词是 `tag_name`（旧文档的 `tag_key` 不存在）。

### 8. 分布式锁

#### fluxion_lock

通用分布式锁（`DatabaseDistributedLock` 的存储；MySQL 原子 upsert 实现）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT UNSIGNED | 自增主键 |
| name | VARCHAR(255) | 锁名（`uk_lock` 唯一，一锁一行） |
| owner | VARCHAR(255) | 持有者 token（条件更新/释放校验） |
| expire_at | DATETIME(3) | 过期时间（过期后可被 upsert 抢占） |

`DatabaseDistributedLock` 的 MySQL 原子 upsert 语义由 `DistributedLockMySqlTest` 覆盖（h2 profile 排除）。

### 9. 版本控制

#### fluxion_version

Trigger/Workflow 等 Entity 的**版本快照表**（`uk_version(ref_id, ref_type, version)`），支撑 Execution 创建时固化 Executable 版本（见 [CONTEXT.md](../../CONTEXT.md)「Execution 版本快照」）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT UNSIGNED | 自增主键 |
| ref_id | VARCHAR(64) | 引用实体 ID（triggerId / workflowId 等） |
| ref_type | VARCHAR(64) | 引用实体类型 |
| version | VARCHAR(64) | 版本号 |
| description | VARCHAR(255) | 版本描述 |
| config | MEDIUMTEXT | 该版本的完整配置 JSON（版本快照内容） |

> Flyway 迁移版本记录是其自建的 `flyway_schema_history` 表，与 `fluxion_version` 无关。

## 索引设计

### 高频查询索引

以 `V20250101__init.sql` 实际建表语句为准：

```sql
-- Execution：唯一身份 + claim 扫描
UNIQUE KEY uk_execution (execution_id)
UNIQUE KEY uk_execution_trigger (trigger_id, trigger_at)
KEY idx_execution_claim (bucket, status, trigger_at, next_fire_at, lease_until)

-- Job：唯一身份 + 容错恢复扫描
UNIQUE KEY uk_job (job_id)
UNIQUE KEY uk_job_execution (execution_id, ref_id)
KEY idx_job_status_trigger (job_id, bucket, trigger_at, status)
KEY idx_job_status_report (job_id, bucket, last_report_at, status)
KEY idx_job_fault_recovery (bucket, status, lease_until, next_retry_at)   -- V20260728

-- Schedule：加载窗口扫描
KEY idx_next_trigger_start_end_bucket (next_trigger_at, start_time, end_time, bucket)

-- 其他
KEY idx_worker_last_heartbeat (last_heartbeat_at)
KEY idx_broker_last_heartbeat (last_heartbeat_at)
```

> 旧文档列出的 `idx_execution_job_time(fluxion_execution.job_id, ...)` 不存在（execution 表没有 job_id 列）。

## 分区与数据清理

**当前 schema 未实施表分区**；上面对 `fluxion_execution` 按月分区的 SQL 是设计构想而非已实施 DDL。
若未来实施，需同步修订 `V20250101__init.sql` 与本表字段说明。

数据清理建议（尚未产品化，按需人工执行）：

```sql
-- 定期清理历史数据
DELETE FROM fluxion_execution
WHERE is_deleted = 1 AND updated_at < DATE_SUB(NOW(), INTERVAL 90 DAY);

-- 归档 Worker 指标数据
DELETE FROM fluxion_worker_metric
WHERE last_heartbeat_at < DATE_SUB(NOW(), INTERVAL 7 DAY);
```
