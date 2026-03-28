# 数据模型

本文档详细说明 Fluxion 的数据库设计。

## 实体关系图

```
┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│  fluxion_app    │       │  fluxion_broker │       │  fluxion_worker │
│   (应用信息)     │       │   (Broker节点)   │       │   (Worker节点)   │
└─────────────────┘       └─────────────────┘       └─────────────────┘
                                                 ┌────┴────────────────┐
                                                 │                     │
                                                 ▼                     ▼
                                      ┌──────────────────┐  ┌──────────────────┐
                                      │fluxion_worker_   │  │fluxion_worker_   │
                                      │  _executor       │  │  _metric         │
                                      │  (执行器列表)     │  │  (指标数据)       │
                                      └──────────────────┘  └──────────────────┘

┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
│fluxion_schedule │       │  fluxion_job    │       │fluxion_trigger  │
│   (调度配置)     │◀─────▶│   (任务定义)     │◀─────▶│   (触发器配置)   │
└─────────────────┘       └─────────────────┘       └─────────────────┘
            │                      │
            │              ┌───────┴───────┐
            │              │               │
            │              ▼               ▼
            │      ┌───────────────┐ ┌───────────────┐
            │      │fluxion_execution│ │fluxion_workflow│
            │      │   (执行记录)    │ │   (工作流定义)  │
            │      └───────┬───────┘ └───────────────┘
            │              │
            │      ┌───────┴───────┐
            │      │               │
            │      ▼               ▼
            │ ┌──────────────┐ ┌──────────────┐
            └▶│fluxion_schedule│ │fluxion_delay_│
              │  _delay       │ │  schedule    │
              │  (延迟调度)    │ │  (延迟执行)   │
              └──────────────┘ └──────────────┘
```

## 核心表结构

### 1. 应用管理

#### fluxion_app

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 主键 |
| name | VARCHAR(128) | 应用名称 |
| description | VARCHAR(512) | 描述 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 2. 节点管理

#### fluxion_broker

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 主键 (UUID) |
| host | VARCHAR(64) | 主机地址 |
| port | INT | 端口号 |
| protocol | VARCHAR(16) | 通信协议 (http/netty) |
| version | VARCHAR(32) | 版本号 |
| last_heartbeat_at | DATETIME | 最后心跳时间 |
| created_at | DATETIME | 创建时间 |
| status | TINYINT | 状态: 0-离线, 1-在线 |

#### fluxion_worker

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 主键 (UUID) |
| app_name | VARCHAR(128) | 应用名称 |
| host | VARCHAR(64) | 主机地址 |
| port | INT | 端口号 |
| protocol | VARCHAR(16) | 通信协议 |
| version | VARCHAR(32) | 版本号 |
| tags | VARCHAR(512) | 标签 (JSON) |
| last_heartbeat_at | DATETIME | 最后心跳时间 |
| created_at | DATETIME | 创建时间 |
| status | TINYINT | 状态: 0-离线, 1-在线, 2-禁用 |

#### fluxion_worker_executor

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 (自增) |
| worker_id | VARCHAR(64) | Worker ID |
| executor_name | VARCHAR(256) | 执行器名称 |
| description | VARCHAR(512) | 描述 |
| created_at | DATETIME | 创建时间 |

#### fluxion_worker_metric

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 (自增) |
| worker_id | VARCHAR(64) | Worker ID |
| cpu_usage | DECIMAL(5,2) | CPU 使用率 |
| memory_usage | DECIMAL(5,2) | 内存使用率 |
| queue_size | INT | 队列大小 |
| active_count | INT | 活跃线程数 |
| reported_at | DATETIME | 上报时间 |

### 3. 任务管理

#### fluxion_job

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 主键 (UUID) |
| app_name | VARCHAR(128) | 所属应用 |
| name | VARCHAR(128) | 任务名称 |
| description | VARCHAR(512) | 描述 |
| type | TINYINT | 任务类型: 1-Normal, 2-Broadcast, 3-MapReduce |
| executor_name | VARCHAR(256) | 执行器名称 |
| executor_params | TEXT | 执行器参数 (JSON) |
| dispatch_option | TEXT | 分发配置 (JSON) |
| retry_option | TEXT | 重试配置 (JSON) |
| overtime_option | TEXT | 超时配置 (JSON) |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| status | TINYINT | 状态: 0-禁用, 1-启用 |

**各配置字段 JSON 结构：**

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

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 主键 (UUID) |
| job_id | VARCHAR(64) | 任务 ID |
| type | TINYINT | 调度类型: 1-FIXED_RATE, 2-FIXED_DELAY, 3-CRON |
| cron | VARCHAR(32) | CRON 表达式 |
| interval_seconds | BIGINT | 间隔秒数 |
| start_time | DATETIME | 开始时间 |
| end_time | DATETIME | 结束时间 |
| next_trigger_time | DATETIME | 下次触发时间 |
| created_at | DATETIME | 创建时间 |
| status | TINYINT | 状态: 0-禁用, 1-启用 |

#### fluxion_trigger

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 主键 (UUID) |
| job_id | VARCHAR(64) | 任务 ID |
| type | TINYINT | 触发器类型: 1-Schedule, 2-Webhook, 3-Delay |
| config | TEXT | 触发器配置 (JSON) |
| created_at | DATETIME | 创建时间 |
| status | TINYINT | 状态: 0-禁用, 1-启用 |

### 4. 执行记录

#### fluxion_execution

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 主键 (UUID) |
| job_id | VARCHAR(64) | 任务 ID |
| trigger_id | VARCHAR(64) | 触发器 ID |
| status | TINYINT | 状态: 0-等待, 1-待调度, 2-分发中, 3-运行中, 4-成功, 5-失败 |
| worker_id | VARCHAR(64) | 执行 Worker ID |
| result | TEXT | 执行结果 |
| error_msg | TEXT | 错误信息 |
| trigger_at | DATETIME | 触发时间 |
| start_at | DATETIME | 开始执行时间 |
| end_at | DATETIME | 结束时间 |
| retry_times | INT | 重试次数 |
| created_at | DATETIME | 创建时间 |

**状态流转：**

```
WAITING(0) → PENDING(1) → DISPATCHING(2) → RUNNING(3) → SUCCEED(4)
                                                  ↘
                                                    FAILED(5)
```

#### fluxion_execution_instance

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 主键 (UUID) |
| execution_id | VARCHAR(64) | 执行 ID |
| worker_id | VARCHAR(64) | Worker ID |
| status | TINYINT | 实例状态 |
| result | TEXT | 执行结果 |
| start_at | DATETIME | 开始时间 |
| end_at | DATETIME | 结束时间 |

用于记录广播任务和 MapReduce 任务在各个 Worker 上的执行实例。

### 5. 延迟调度

#### fluxion_schedule_delay

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 主键 (UUID) |
| schedule_id | VARCHAR(64) | 调度 ID |
| execution_id | VARCHAR(64) | 执行 ID |
| delay_seconds | BIGINT | 延迟秒数 |
| expected_trigger_time | DATETIME | 预期触发时间 |
| status | TINYINT | 状态: 0-待执行, 1-已执行, 2-已取消 |
| created_at | DATETIME | 创建时间 |

#### fluxion_delay_schedule

专门用于延迟执行任务。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 主键 (UUID) |
| job_id | VARCHAR(64) | 任务 ID |
| params | TEXT | 任务参数 |
| trigger_at | DATETIME | 触发时间 |
| status | TINYINT | 状态 |
| created_at | DATETIME | 创建时间 |

### 6. 工作流

#### fluxion_workflow

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 主键 (UUID) |
| name | VARCHAR(128) | 工作流名称 |
| description | VARCHAR(512) | 描述 |
| dag_config | TEXT | DAG 配置 (JSON) |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| status | TINYINT | 状态 |

**dag_config JSON 结构：**

```json
{
  "nodes": [
    {
      "id": "node-1",
      "type": "START",
      "nextNodes": ["node-2"]
    },
    {
      "id": "node-2",
      "type": "EXECUTOR",
      "jobId": "job-xxx",
      "nextNodes": ["node-3"]
    },
    {
      "id": "node-3",
      "type": "END"
    }
  ],
  "edges": [
    {"source": "node-1", "target": "node-2"},
    {"source": "node-2", "target": "node-3"}
  ]
}
```

### 7. 标签系统

#### fluxion_tag

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 (自增) |
| ref_id | VARCHAR(64) | 引用对象 ID |
| ref_type | TINYINT | 引用类型: 1-Worker, 2-Job |
| tag_key | VARCHAR(64) | 标签键 |
| tag_value | VARCHAR(128) | 标签值 |
| created_at | DATETIME | 创建时间 |

### 8. 分布式锁

#### fluxion_lock

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(64) | 锁标识 |
| lock_until | DATETIME | 锁过期时间 |
| locked_at | DATETIME | 锁定时间 |
| locked_by | VARCHAR(64) | 锁定者 |

用于 Broker 集群间的任务调度协调。

### 9. 版本控制

#### fluxion_version

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT | 主键 |
| version | VARCHAR(32) | 版本号 |
| description | VARCHAR(512) | 描述 |
| applied_at | DATETIME | 应用时间 |

Flyway 迁移版本记录。

## 索引设计

### 高频查询索引

```sql
-- Worker 心跳查询
CREATE INDEX idx_worker_status_heartbeat ON fluxion_worker(status, last_heartbeat_at);

-- 调度查询
CREATE INDEX idx_schedule_next_trigger ON fluxion_schedule(status, next_trigger_time);

-- 执行记录查询
CREATE INDEX idx_execution_job_time ON fluxion_execution(job_id, created_at);
CREATE INDEX idx_execution_status ON fluxion_execution(status, trigger_at);

-- 执行器查询
CREATE INDEX idx_worker_executor_name ON fluxion_worker_executor(executor_name, worker_id);
```

## 分区策略

### 执行记录表分区

```sql
-- 按时间范围分区，保留最近 90 天
CREATE TABLE fluxion_execution (
    -- ...
    created_at DATETIME
) PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at)) (
    PARTITION p202501 VALUES LESS THAN (202502),
    PARTITION p202502 VALUES LESS THAN (202503),
    -- ...
);
```

## 数据清理策略

```sql
-- 定期清理历史数据
DELETE FROM fluxion_execution
WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY);

-- 归档 Worker 指标数据
DELETE FROM fluxion_worker_metric
WHERE reported_at < DATE_SUB(NOW(), INTERVAL 7 DAY);
```
