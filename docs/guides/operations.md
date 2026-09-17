# Fluxion 调度器运维操作手册

本文档提供 Fluxion 分布式任务调度平台的运维操作指南。所有命令、端口、心跳参数均以代码为准，可对照仓库验证。

> 本文遵循当前 Execution 调度模型（`fluxion_schedule` 产生 `fluxion_execution`，Broker 短租约领取 `PENDING` 实例后创建 Job 并原子迁移至 `RUNNING`）。术语以 [CONTEXT.md](../../CONTEXT.md) 为权威。

## 目录

1. [启动顺序](#启动顺序)
2. [数据库迁移](#数据库迁移)
3. [可观测性现状](#可观测性现状)
4. [Broker 故障恢复](#broker-故障恢复)
5. [故障排查命令](#故障排查命令)
6. [业务幂等性指南](#业务幂等性指南)

---

## 启动顺序

正确的服务启动顺序是确保系统稳定运行的基础：

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   MySQL     │───▶│   Broker    │───▶│   Worker    │
│  (数据库)    │    │ (调度服务)   │    │ (执行节点)   │
└─────────────┘    └─────────────┘    └─────────────┘
```

### 详细步骤

#### 1. 启动数据库（MySQL）

```bash
# 检查 MySQL 状态
systemctl status mysql

# 启动 MySQL
systemctl start mysql

# 验证连接
mysql -u root -p -e "SELECT 1"
```

**检查点**：
- 数据库端口 3306 可连接
- `fluxion` 数据库已创建
- Flyway 迁移表存在

#### 2. 启动 Broker（调度服务）

```bash
# 启动命令
java -jar fluxion-server-start.jar --spring.profiles.active=prod
```

**真实端口**（见 `fluxion-server-start/src/main/resources/application.yaml`）：
- HTTP 管理端口 `9786`：REST API（`/api/v1/*`）
- RPC 服务端口 `9785`：Worker↔Broker 通信

**健康检查**：

当前启动模块**未引入 Actuator**，无 `/actuator/health` 端点。验证 Broker 可用性只能通过真实业务端点：

```bash
# RPC 端口连通性（Worker 用）
telnet <broker-host> 9785

# HTTP 端口连通性 + 业务端点可用
curl -X POST http://localhost:9786/api/v1/worker/page
```

**检查点**：
- 端口 9786 / 9785 可连接
- 数据库连接池初始化完成
- Flyway 迁移执行无报错

#### 3. 启动 Worker（执行节点）

```bash
# 启动命令
java -jar fluxion-worker-demo.jar --spring.profiles.active=prod
```

Worker 启动后会向 Broker 注册并发心跳。真实心跳参数见 [Worker 下线判定](#worker-下线判定)。

**检查点**：
- Worker 成功注册到 Broker（日志可见注册成功）
- 心跳正常（每 3 秒一次）
- 执行器加载完成

---

## 数据库迁移

Fluxion 使用 Flyway 进行数据库版本管理。

### 自动迁移

应用启动时自动执行（`application.yaml`）：

```yaml
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 0
    enabled: true
```

### 手动迁移

```bash
# 查看当前版本
mvn flyway:info -pl fluxion-server/fluxion-server-start

# 执行迁移
mvn flyway:migrate -pl fluxion-server/fluxion-server-start

# 修复失败的迁移
mvn flyway:repair -pl fluxion-server/fluxion-server-start
```

### 迁移脚本位置与现状

```
fluxion-server/fluxion-server-start/src/main/resources/db/migration/
├── V20250101__init.sql                 # 初始建表（fluxion_schedule / fluxion_execution / fluxion_job / fluxion_worker …）
└── V20260728__add_job_fault_state.sql  # Job 容错字段：dispatch_attempt / lease_owner / lease_until / timeout_at / next_retry_at
```

**重要**：当前 `fluxion_schedule_delay` 表**已不存在**。调度实例与执行实例已合并为 `fluxion_execution`，它持有 `lease_owner / lease_until / execution_token / fire_attempt / next_fire_at / bucket / recovery_owner` 字段，并以 `uk_execution_trigger(trigger_id, trigger_at)` 作为唯一键。任何引用 `fluxion_schedule_delay` 的旧脚本或命令均失效。

---

## 可观测性现状

> ⚠️ 当前版本**未引入 Micrometer / Actuator / Prometheus**，仓库无任何 `fluxion.*` 监控指标注册，也无 `/actuator/metrics`、`/actuator/prometheus` 端点。所有可靠性动作（claim、reclaim、retry、timeout、worker offline）目前**只写日志**。

### 存量诊断接口（已实现）

`ObservationController` 提供只读快照，对齐现有 `@RestController` 风格（端口 9786）：

```
GET/POST /api/v1/observation/overview
```

代码链路：`ObservationController` → `ObservationService` → CQRS `ObservationOverviewQuery`（core 的 `ObservationQueryService` 聚合 MySQL 单点权威，无 Micrometer 依赖）。

响应字段（`ObservationOverviewView`，状态 key 见 [CONTEXT.md](../../CONTEXT.md)）：

```json
{
  "executions": {"pending": 12, "claimed": 2, "running": 4, "inited": 0,
                  "succeed": 300, "failed": 1, "skipped": 0, "misfired": 0, "invalid": 2,
                  "restarted": 0, "cancelled": 0, "paused": 0, "killing": 0},
  "executionBacklog": 3,
  "executionMisfireCandidates": 1,
  "executionReclaimableClaims": 0,
  "jobs": {"inited": 0, "restarted": 0, "running": 4, "retry_wait": 1,
            "succeed": 300, "failed": 1, "cancelled": 0, "terminated": 0, "paused": 0},
  "workers": {"online": 3, "offline": 1}
}
```

字段口径：
- `executions` / `jobs` / `workers`：状态分布，zero-fill 全部已知枚举值（`unknown` 除外），已删除行不计入
- `executionBacklog`：`status=pending 且 trigger_at <= now` 的积压数
- `executionMisfireCandidates`：`status=pending 且 now - trigger_at > 5s`（`MISFIRE_THRESHOLD_SECONDS`）的错过触发候选数
- `executionReclaimableClaims`：`status=claimed 且 lease_until <= now`，下一轮 `ExecutionsLoadCmd` 可重新领取的 Execution 数

回归测试：`ObservationOverviewMySqlTest`（h2-integration-test profile）。

### 速率型 SLI 暂缺

下列运维场景当前**只能靠捞日志**，待后续按"结构化日志聚合"补齐：

- 过去 N 分钟 claim 冲突 / 失败次数
- 过去 N 分钟 lease 过期回收次数
- dispatch 失败率
- misfire 触发频次

框架内部可靠性动作均已打日志（见 `WorkerChecker`、`JobLeaseRecoveryChecker`、`BrokerManger` 等），但无聚合链路。在新增累计计数列或接入日志聚合前，速率型告警**不可用**。

### 失效配置警告

`application.yaml` 中的 `fluxion.fault-tolerance` 整段（`retry / timeout / failover`）**未被任何 Java 代码读取**，是死配置。真实心跳超时、Worker 下线判定、重试间隔均由代码常量与 `fluxion.schedule.lease` / `fluxion.worker` 配置决定，见下文。**不要依据 `fluxion.fault-tolerance` 调参。**

---

## Broker 故障恢复

当 Broker 发生故障时，系统具备以下恢复能力。

### 调度租约与租约接管（Execution 模型）

每个 Broker 只处理自己 bucket 内的 Execution。真实参数来自代码常量与 Loader 周期（`ScheduleLeaseProperties` 是死配置，见下警告）：

| 参数 | 值 | 来源 |
|------|-----|------|
| claim 租约有效期 | 15 秒 | `ExecutionScheduleCommandService.CLAIM_LEASE_SECONDS` |
| 领取方式 | 条件 UPDATE `PENDING → CLAIMED`，一次性设 `lease_until = NOW(3) + 15s`，**不续租** | `ExecutionScheduleCommandService.claim` |
| 过期回收 | `reclaimExpiredClaims`：bucket 内 `CLAIMED` 且 `lease_until <= now` 可被重新 claim | 由 `ExecutionLoader` 每 1 分钟触发 `ExecutionsLoadCmd` |
| 触发点生成 | `ScheduleLoader` 每 5 分钟扫描 `fluxion_schedule`，预生成未来 10 分钟 | `ScheduleConstants.LOAD_INTERVAL` | 

租约比较与更新由 MySQL `NOW(3)` 完成，避免 Broker 时钟偏差。**接管延迟 = 15s 租约到期 + 最多 1 分钟等下一轮 `ExecutionsLoadCmd`**（不是旧模型的 20s）。

- `CLAIMED` 的 Execution 不做停机强制重置；Broker 停止后其租约自然到期（15s），随后任何 Broker 在下一轮 load 时重新 claim。
- `RUNNING` Execution 已清除调度租约，由每个 Job 自己的租约、超时检查与重试机制恢复（见 [execution-state.md](../architecture/execution-state.md)）。
- recovery 只处理当前 Broker 所属 bucket，使用条件更新取得 recovery lease，不覆盖原始 `worker_id`。

> ⚠️ `fluxion.schedule.lease` 前缀的 `ScheduleLeaseProperties`（duration/renewInterval/reclaimInterval）**没有任何 Java 代码读取其 getter**，是死配置类；调整它不生效。真实参数以上表为准。

业务执行器仍必须保证幂等。租约与 fencing 只能降低重复下发概率，**不提供 exactly-once 语义**。

### Worker 下线判定

真实参数（`WorkerRemoteConstant.HEARTBEAT_TIMEOUT_SECOND = 3`）：

| 参数 | 值 | 含义 |
|------|-----|------|
| Worker 心跳间隔 | 3 秒 | Worker 每 3 秒发一次心跳 |
| `WorkerChecker` 检查周期 | 3 秒 | Broker 每 3 秒扫描一次 |
| 下线判定窗口 | 6 秒 | 连续 6 秒（`HEARTBEAT_TIMEOUT_SECOND * 2`）未收到心跳即判定下线 |

```
Worker-A 心跳时序:
  T+0s    心跳 ✓
  T+3s    心跳 ✓
  T+6s    心跳丢失
  T+6s+   Broker 判定 Worker-A 下线
          → 查询该 Worker 上 RUNNING 的 Job（持久化 `fluxion_job`）
          → 对每个 Job 发 JobFailCmd，进入 Job 重试状态机
          → 下一次派发时 dispatch_attempt 递增
```

> ⚠️ operations.md 旧版写的"心跳超时 10 秒"、`fluxion.worker.heartbeat=5s` 均错误，请勿参照。

### 单 Broker 故障（多 Broker 部署）

```
Before:                    After:
┌─────────┐               ┌─────────┐
│Broker-1 │◀──故障         │Broker-2 │◀──接管
└────┬────┘               └────┬────┘
     │                           │
  Worker                      Worker
  自动重连                      已连接
```

**自动恢复流程**：
1. Worker 检测到 Broker-1 不可用，切换到 Broker-2
2. Broker-1 所属 bucket 内的 Execution 在租约到期后被 Broker-2 扫描接管
3. Broker-1 恢复后重新注册 bucket

### 单 Broker 故障（单 Broker 部署）

1. **任务调度暂停**：新 Execution 仍由 `ScheduleLoader` 预生成，但无人 claim，堆积为 `PENDING`
2. **恢复步骤**：
   ```bash
   systemctl restart fluxion-broker
   ```
3. **验证调度恢复**：见 [故障排查命令](#故障排查命令) 中"调度积压"查询。

---

## 故障排查命令

### 系统状态检查

```bash
# 1. 检查 Java 进程
jps -lvm | grep fluxion

# 2. 查看端口监听
netstat -tlnp | grep -E "9785|9786"

# 3. 检查资源使用
top -p $(pgrep -d',' -f fluxion)

# 4. 查看线程数
jstack <pid> | grep -c "java.lang.Thread.State"

# 5. 查看堆内存
jmap -heap <pid>

# 6. 查看 GC 情况
jstat -gcutil <pid> 1000 10
```

### 日志排查

可靠性动作的关键日志来源（grep 这些类名定位故障）：

| 类 | 关注点 |
|----|--------|
| `WorkerChecker` | Worker 下线判定与 Job 失败标记 |
| `JobLeaseRecoveryChecker` | Job lease 过期接管 |
| `JobLeaseRenewChecker` | Job lease 续租 |
| `JobRetryChecker` | 到期重试触发 |
| `JobUnRunChecker` | 未派发 Job 恢复 |
| `BrokerManger` | Broker 上下线 |
| `ScheduleLoader` | 预生成与历史补建 |

```bash
# 实时查看日志
tail -f /var/log/fluxion/broker.log

# 搜索错误
grep -E "ERROR|Exception" /var/log/fluxion/broker.log | tail -50

# 搜索特定调度
grep "triggerId=<id>" /var/log/fluxion/broker.log
```

### 数据库排查

> 全部以 `fluxion_execution` / `fluxion_job` / `fluxion_worker` 为准。`fluxion_schedule_delay` 已删除。

```bash
mysql -u root -p fluxion
```

#### 调度积压

```sql
-- PENDING 积压（已到期但未被领取）
SELECT COUNT(*) FROM fluxion_execution
WHERE status = 'pending' AND trigger_at <= NOW(3);

-- 近期按状态分布
SELECT status, COUNT(*) FROM fluxion_execution
WHERE updated_at > DATE_SUB(NOW(), INTERVAL 1 HOUR)
GROUP BY status;
```

#### misfire 候选

```sql
-- 超过 misfireThreshold（默认 5s）仍未领取的 PENDING
SELECT trigger_id, trigger_at, status, lease_until
FROM fluxion_execution
WHERE status = 'pending' AND trigger_at < DATE_SUB(NOW(3), INTERVAL 5 SECOND)
ORDER BY trigger_at
LIMIT 100;
```

#### 可回收租约

```sql
-- CLAIMED 但租约已过期，可被其他 Broker 重新 claim
SELECT execution_id, lease_owner, lease_until
FROM fluxion_execution
WHERE status = 'claimed' AND lease_until < NOW(3)
LIMIT 100;
```

#### Execution 状态分布

```sql
SELECT status, COUNT(*) FROM fluxion_execution GROUP BY status;
```

#### 长时间未结束的 RUNNING Execution

```sql
SELECT execution_id, trigger_id, state_updated_at, worker_id
FROM fluxion_execution
WHERE status = 'running'
  AND state_updated_at < DATE_SUB(NOW(3), INTERVAL 1 HOUR);
```

#### Job 状态分布

```sql
SELECT status, COUNT(*) FROM fluxion_job GROUP BY status;
```

#### 积压的 RETRY_WAIT Job

```sql
SELECT job_id, execution_id, status, next_retry_at
FROM fluxion_job
WHERE status = 'retry_wait' AND next_retry_at <= NOW(3)
ORDER BY next_retry_at
LIMIT 100;
```

#### Worker 注册状态

```sql
SELECT app_id, status, last_heartbeat_at FROM fluxion_worker;
```

### 网络排查

```bash
# 检查 Broker 端口连通性
telnet <broker-host> 9785   # RPC
telnet <broker-host> 9786   # HTTP

# 检查 Worker 到 Broker 连通性（真实 RPC 健康端点）
curl -v http://<broker-host>:9785/v1/worker/health

# 检查网络延迟
ping <worker-host>
```

### 线程排查

```bash
# 导出线程栈
jstack <pid> > thread_dump.txt

# 查找死锁
jstack <pid> | grep -i "deadlock\|blocked"

# 统计线程状态
jstack <pid> | grep "java.lang.Thread.State" | sort | uniq -c | sort -rn
```

### 常见故障场景

#### 场景1: Worker 无法注册到 Broker

**症状**：Worker 启动后日志显示注册失败

```bash
# 1. 检查 Broker 地址配置
grep -A3 "brokers" <worker>/application.yaml

# 2. 检查 Broker RPC 端口可达
telnet <broker-host> 9785

# 3. 查看 Worker 日志
tail -f /var/log/fluxion/worker.log | grep -i "register\|connect"

# 4. 确认 Broker 已连上 MySQL
grep -i "flyway\|datasource" /var/log/fluxion/broker.log | tail
```

#### 场景2: 任务有调度记录但未执行

**症状**：`fluxion_execution` 有 `pending` 记录，但无 `running`/终态

```bash
# 1. 是否有可用 Worker
mysql -e "SELECT status, COUNT(*) FROM fluxion_worker GROUP BY status;"

# 2. 是否积压（PENDING 且已到期）
mysql -e "SELECT COUNT(*) FROM fluxion_execution WHERE status='pending' AND trigger_at <= NOW(3);"

# 3. Broker 是否在 claim（日志）
grep -i "claim\|ExecutionScheduleCommand" /var/log/fluxion/broker.log | tail -30
```

#### 场景3: 重复执行诊断

**症状**：疑似同一执行被多次执行

```sql
-- Execution 按 (trigger_id, trigger_at) 唯一，不应有多条
SELECT trigger_id, trigger_at, COUNT(*) c
FROM fluxion_execution
WHERE created_at > DATE_SUB(NOW(), INTERVAL 1 HOUR)
GROUP BY trigger_id, trigger_at
HAVING c > 1;

-- Job 重复派发看 dispatch_attempt（>=2 表示发生过重试/重新派发）
SELECT job_id, execution_id, dispatch_attempt, status
FROM fluxion_job
WHERE dispatch_attempt >= 2
ORDER BY updated_at DESC
LIMIT 50;
```

> 框架保证 Execution 创建一次、Job 至少一次派发。`dispatch_attempt >= 2` 不一定代表 bug，也可能是 Worker 失联/超时触发的正常重试；需结合 `WorkerChecker` 日志判断。业务侧必须以 `jobId` 为幂等键防止外部副作用重复。

---

## 业务幂等性指南

### 框架 vs 业务责任边界

| 层 | 职责 | 当前能力 |
|----|------|----------|
| 调度（Execution） | 创建一次、领取 fencing | `(trigger_id, trigger_at)` 唯一键 + 两层租约 |
| 派发（Job） | 至少一次派发 | `jobId` 跨 Broker/Worker 稳定幂等键；`dispatch_attempt` 抑制同次派发重复 |
| 业务（Executor） | 最终幂等 | **必须自行保证**，框架不提供 exactly-once |

### 框架提供的（有限）防重

- 租约 + fencing：降低重复 claim / 重复 dispatch 概率，**不消除**
- `jobId + dispatch_attempt`：Worker 侧抑制同一次派发的重复接收
- 持久化 attempt：Broker 重启后不重发已确认完成的 Job

### 业务必须处理的重复场景

1. **租约边界竞争**：租约过期瞬间的并发 claim 可能产生两个 Broker 短暂并存
2. **网络分区**：Worker 完成但确认丢失，触发超时重试 → 同一 Job 再次执行
3. **Worker 下线重试**：`WorkerChecker` 判离线后对 RUNNING Job 发 `JobFailCmd`，新 Worker 接收视为新 Job

### 业务幂等实现建议

推荐以 `jobId`（跨 Broker/Worker 稳定）或 `executionId` 作为幂等键：

#### 方案1: 数据库唯一约束（推荐）

```java
@Override
public ExecuteResult execute(JobContext context) {
    String jobId = context.getJobId();  // 框架提供的稳定幂等键
    try {
        recordRepo.insertWithUniqueJobId(jobId, ...);  // job_id 唯一索引
        doWork();
        return ExecuteResult.success();
    } catch (DuplicateKeyException e) {
        log.info("[IDEMPOTENCY] duplicate jobId={}", jobId);
        return ExecuteResult.success();
    }
}
```

#### 方案2: 状态机幂等

```java
@Override
public ExecuteResult execute(JobContext context) {
    Order order = orderRepo.findByOrderNo(context.getParams().get("orderNo"));
    String target = context.getParams().get("targetState");
    if (target.equals(order.getState())) {
        return ExecuteResult.success();  // 目标态已达成
    }
    if (!isValidTransition(order.getState(), target)) {
        return ExecuteResult.failure("Invalid transition");
    }
    int updated = orderRepo.updateStateByVersion(...);  // 乐观锁
    return updated > 0 ? ExecuteResult.success()
                       : ExecuteResult.retry("Concurrent update");
}
```

### 幂等检查清单

- [ ] 是否有唯一业务键（订单号 / `jobId` / `executionId`）
- [ ] 是否用数据库唯一约束防重复插入
- [ ] 是否检查前置状态（幂等 ≠ 重复调用不报错）
- [ ] 超时是否可重入（长任务避免无限重试）
- [ ] 是否记录执行日志便于排查重复

---

## 附录

### 关键配置参考（真实生效项）

**Broker**（`fluxion-server-start/src/main/resources/application.yaml`）：

```yaml
server:
  port: 9786                    # HTTP 管理
fluxion:
  broker:
    host:                       # 留空则自动取本机
    port: 9785                  # RPC
    protocol: HTTP
  schedule:
    lease:                      # ScheduleLeaseProperties，真实生效
      duration: 15
      renewInterval: 10
      reclaimInterval: 5
```

**Worker**（`fluxion.worker` 前缀，心跳由 `WorkerRemoteConstant.HEARTBEAT_TIMEOUT_SECOND=3` 决定）：

```yaml
fluxion:
  worker:
    brokers:
      - http://broker-host:9785
    tags:
      - env=prod
```

### 失效配置（勿用）

`fluxion.fault-tolerance.{retry,timeout,failover}` 整段未绑定代码，调整无效。同理 `fluxion.schedule.lease`（`ScheduleLeaseProperties`）也为死配置。真实故障转移参数由 `ExecutionScheduleCommandService` 常量、`ScheduleConstants.LOAD_INTERVAL` 与 `WorkerRemoteConstant` 决定。

### FAQ

**Q: 调度延迟过大怎么办？**
A: (1) 检查 Broker 是否在正常 claim（日志 `ExecutionScheduleCommand`）；(2) 查积压：`SELECT COUNT(*) FROM fluxion_execution WHERE status='pending' AND trigger_at <= NOW(3)`；(3) 检查 `fluxion_worker` 在线数是否充足；(4) 检查 Broker 是否触发长时间 GC（`jstat -gcutil`）。

**Q: Worker 频繁离线？**
A: 真实下线窗口是 6 秒（3s 心跳 × 2，见 `WorkerRemoteConstant`）。检查网络抖动、Worker 进程 GC/负载。**不要调 `fluxion.fault-tolerance.failover.worker-offline-timeout`，它不生效。**

**Q: 数据库连接池耗尽？**
A: 增大 `spring.datasource.hikari.maximum-pool-size`；检查慢查询；关注 `ExecutionScheduleCommand` 事务内是否串行执行过久。
