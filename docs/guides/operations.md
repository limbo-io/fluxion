# Fluxion 调度器运维操作手册

本文档提供 Fluxion 分布式任务调度平台的运维操作指南，包括部署、监控、故障排查等内容。

## 目录

1. [启动顺序](#启动顺序)
2. [数据库迁移](#数据库迁移)
3. [滚动重启流程](#滚动重启流程)
4. [Broker 故障恢复](#broker-故障恢复)
5. [监控指标](#监控指标)
6. [告警阈值](#告警阈值)
7. [故障排查命令](#故障排查命令)

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

# 或使用脚本
./startup.sh broker
```

**健康检查**：
```bash
# HTTP 健康检查
curl http://localhost:9786/actuator/health

# 检查 Broker 服务端口
curl http://localhost:9785/v1/worker/health
```

**检查点**：
- HTTP 管理端口 9786 响应正常
- RPC 服务端口 9785 可连接
- 数据库连接池初始化完成

#### 3. 启动 Worker（执行节点）

```bash
# 启动命令
java -jar fluxion-worker-demo.jar --spring.profiles.active=prod

# 或使用脚本
./startup.sh worker
```

**健康检查**：
```bash
# 检查 Worker 注册状态
curl http://localhost:8084/actuator/health
```

**检查点**：
- Worker 成功注册到 Broker
- 心跳正常（2秒间隔）
- 执行器加载完成

---

## 数据库迁移

Fluxion 使用 Flyway 进行数据库版本管理。

### 自动迁移

应用启动时自动执行：

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

### 迁移脚本位置

```
fluxion-server/fluxion-server-start/src/main/resources/db/migration/
├── V20250101__init.sql                    # 初始版本
├── V20260714__add_schedule_delay_lease.sql      # 调度延迟租约
└── V20260714__add_execution_lease_and_attempt.sql # 执行租约和重试
```

---

## 滚动重启流程

在不影响任务执行的前提下，逐个重启服务节点。

### Broker 滚动重启

```bash
# 1. 摘流（ graceful shutdown 前的准备）
# 目前版本需要人工控制流量入口

# 2. 重启 Broker
kill -TERM <broker_pid>
# 等待进程优雅退出（最多 30 秒）

# 3. 启动新实例
java -jar fluxion-server-start.jar

# 4. 健康检查
curl http://localhost:9786/actuator/health

# 5. 恢复流量
```

**注意事项**：
- 多 Broker 部署时，确保至少一个 Broker 保持运行
- Worker 会自动重连到其他可用 Broker
- 调度中的任务可能短暂延迟

### Worker 滚动重启

```bash
# 1. 摘流（禁用任务分发）
# 修改 Worker 标签，使其不再匹配新任务

# 2. 等待当前任务完成
# 查看活跃执行计数
jmx_read fluxion.worker.activeExecutions

# 3. 重启 Worker
kill -TERM <worker_pid>
# 等待优雅退出（最多 60 秒）

# 4. 启动新实例
java -jar fluxion-worker-demo.jar

# 5. 验证注册
# 查看 Broker Worker 列表
curl http://localhost:9786/api/v1/workers
```

---

## Broker 故障恢复

当 Broker 发生故障时，系统具备以下恢复能力：

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
1. Worker 检测到 Broker-1 心跳超时（默认 10 秒）
2. Worker 切换到 Broker-2
3. 未完成的任务被 Broker-2 接管
4. Broker-1 恢复后，Worker 负载均衡到两个 Broker

### 单 Broker 故障（单 Broker 部署）

1. **任务调度暂停**
   - 新任务无法创建调度
   - 已调度任务保留在数据库中

2. **恢复步骤**
   ```bash
   # 1. 检查 Broker 状态
   systemctl status fluxion-broker
   
   # 2. 查看日志定位问题
   tail -f /var/log/fluxion/broker.log
   
   # 3. 重启 Broker
   systemctl restart fluxion-broker
   
   # 4. 验证调度恢复
   # 查看调度延迟队列
curl http://localhost:9786/actuator/metrics/fluxion.schedule.delay.queue.size
   ```

---

## 监控指标

### 核心性能指标

| 指标名 | 类型 | 说明 | 正常范围 |
|--------|------|------|----------|
| `fluxion.schedule.delay.claim.count` | Counter | 延迟任务抢占次数 | 随调度频率增长 |
| `fluxion.schedule.delay.expired.lease.count` | Counter | 租约过期回收次数 | 低（< 10/分钟） |
| `fluxion.execution.active.count` | Gauge | 当前活跃执行数 | < 队列容量的 80% |
| `fluxion.dispatch.success.rate` | Gauge | 分发成功率 | > 99% |
| `fluxion.dispatch.failure.rate` | Gauge | 分发失败率 | < 1% |
| `fluxion.dispatch.duplicate.suppression.count` | Counter | 重复分发抑制次数 | 低（正常场景） |
| `fluxion.worker.heartbeat.latency` | Timer | Worker 心跳延迟 | < 500ms |
| `fluxion.worker.offline.count` | Counter | Worker 离线次数 | 低（稳定环境） |

### 查看指标

```bash
# Prometheus 指标端点
curl http://localhost:9786/actuator/prometheus

# Micrometer 指标列表
curl http://localhost:9786/actuator/metrics

# 特定指标
curl http://localhost:9786/actuator/metrics/fluxion.execution.active.count
```

### 关键业务指标解释

#### 1. 延迟任务抢占（delay claim count）

```
含义：Broker 从延迟队列中抢占任务进行调度的次数
正常：与调度频率成正比
异常：持续增长但任务未执行 → 可能 Worker 不足
```

#### 2. 租约过期回收（expired lease count）

```
含义：任务租约到期后被回收重新调度的次数
正常：少量（偶尔的网络抖动）
异常：大量增长 → 可能 Worker 批量离线或执行超时
```

#### 3. 活跃执行数（active execution count）

```
含义：当前正在执行的任务数量
正常：在 Worker 容量范围内波动
异常：持续满载 → 需要扩容 Worker
```

#### 4. 重复分发抑制（duplicate suppression count）

```
含义：因任务已执行而拒绝重复分发的次数
正常：极低（网络重试场景）
异常：大量增长 → 可能存在重复调度 bug
```

---

## 告警阈值

### 关键告警规则

| 告警名称 | 条件 | 级别 | 处理建议 |
|----------|------|------|----------|
| Broker 离线 | 心跳检测失败 | P0 | 立即检查 Broker 进程和服务器状态 |
| Worker 批量离线 | 在线 Worker 数 < 80% | P0 | 检查网络、Worker 资源使用情况 |
| 任务积压 | 延迟队列深度 > 1000 | P1 | 扩容 Worker 或检查执行器性能 |
| 分发失败率高 | 失败率 > 5% | P1 | 检查 Worker 健康状态和容量 |
| 执行超时率高 | 租约过期 > 100/分钟 | P1 | 检查 Worker 负载和执行器性能 |
| 心跳延迟高 | P99 延迟 > 2s | P2 | 检查网络和 Broker 负载 |
| 数据库连接池耗尽 | 活跃连接 > 80% | P1 | 检查连接池配置和慢查询 |

### 告警模板（Prometheus AlertManager）

```yaml
groups:
  - name: fluxion-alerts
    rules:
      # Broker 离线告警
      - alert: FluxionBrokerDown
        expr: up{job="fluxion-broker"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Fluxion Broker 离线"
          description: "Broker {{ $labels.instance }} 已离线超过 1 分钟"

      # Worker 离线告警
      - alert: FluxionWorkerOffline
        expr: fluxion_worker_online_count < 1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Fluxion Worker 全部离线"
          description: "可用 Worker 数量为 0"

      # 任务积压告警
      - alert: FluxionTaskBacklog
        expr: fluxion_schedule_delay_queue_size > 1000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Fluxion 任务积压"
          description: "延迟队列深度 {{ $value }} 超过阈值 1000"

      # 分发失败告警
      - alert: FluxionDispatchFailure
        expr: rate(fluxion_dispatch_failure_count[5m]) > 0.05
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Fluxion 分发失败率高"
          description: "5分钟内分发失败率 {{ $value }}"
```

---

## 故障排查命令

### 系统状态检查

```bash
# 1. 检查 Java 进程
jps -lvm | grep fluxion

# 2. 查看端口监听
netstat -tlnp | grep -E "9785|9786|9787"

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

```bash
# 实时查看日志
tail -f /var/log/fluxion/broker.log

# 搜索错误日志
grep -E "ERROR|WARN|Exception" /var/log/fluxion/broker.log | tail -50

# 查看特定时间段
awk '$0 >= "2026-07-21 10:00:00" && $0 <= "2026-07-21 11:00:00"' broker.log

# 搜索特定调度 ID
grep "schedule-id=<id>" /var/log/fluxion/broker.log
```

### 数据库排查

```bash
# 连接 MySQL
mysql -u root -p fluxion

# 查看调度延迟队列积压
SELECT COUNT(*) FROM fluxion_schedule_delay WHERE dispatch_time <= NOW();

# 查看执行状态分布
SELECT status, COUNT(*) FROM fluxion_execution GROUP BY status;

# 查看 Worker 注册状态
SELECT app_name, status, last_heartbeat_at FROM fluxion_worker;

# 查看长时间运行的执行
SELECT * FROM fluxion_execution 
WHERE status = 'RUNNING' 
  AND start_time < DATE_SUB(NOW(), INTERVAL 1 HOUR);

# 查看租约过期任务
SELECT * FROM fluxion_schedule_delay 
WHERE lease_expires_at < NOW() AND claimed_by IS NOT NULL;
```

### 网络排查

```bash
# 检查 Broker 端口连通性
telnet localhost 9785
telnet localhost 9786

# 检查 Worker 到 Broker 连通性
curl -v http://broker-host:9785/v1/worker/health

# 检查网络延迟
ping <worker-host>
traceroute <broker-host>

# 抓包分析（Worker 注册）
tcpdump -i any port 9785 -w broker-worker.pcap
```

### 线程排查

```bash
# 导出线程栈
jstack <pid> > thread_dump.txt

# 查找死锁
jstack <pid> | grep -i "deadlock\|blocked"

# 统计线程状态
jstack <pid> | grep "java.lang.Thread.State" | sort | uniq -c | sort -rn

# 查看调度线程
jstack <pid> | grep -A 20 "schedule-delay"
```

### 常见故障场景

#### 场景1: Worker 无法注册到 Broker

**症状**：Worker 启动后日志显示注册失败

**排查步骤**：
```bash
# 1. 检查 Broker 地址配置
grep "brokers" /path/to/application.yaml

# 2. 检查 Broker 是否可访问
curl http://broker-host:9785/actuator/health

# 3. 检查防火墙
iptables -L | grep 9785

# 4. 查看 Worker 日志
tail -f /var/log/fluxion/worker.log | grep -i "register\|connect"
```

#### 场景2: 任务调度正常但无执行记录

**症状**：调度创建成功，但任务未执行

**排查步骤**：
```bash
# 1. 检查是否有可用 Worker
curl http://localhost:9786/api/v1/workers | jq '.[] | select(.status == "ONLINE")'

# 2. 检查过滤器是否匹配
# 查看调度配置的 tags 和 executor，对比 Worker 配置

# 3. 检查 Worker 容量
curl http://worker-host:8084/actuator/metrics/fluxion.worker.activeExecutions

# 4. 查看 Broker 分发日志
grep "dispatch" /var/log/fluxion/broker.log | tail -20
```

#### 场景3: 重复执行任务

**症状**：同一任务被多次执行

**排查步骤**：
```bash
# 1. 检查执行记录表
SELECT job_id, COUNT(*) as cnt 
FROM fluxion_execution 
WHERE created_at > DATE_SUB(NOW(), INTERVAL 1 HOUR)
GROUP BY job_id HAVING cnt > 1;

# 2. 查看重复分发抑制日志
grep "duplicate" /var/log/fluxion/worker.log

# 3. 检查 Broker 时钟同步
date; ssh broker-host date
```

---

## 附录

### 配置参考

**最小生产配置（Broker）**：
```yaml
server:
  port: 9786

spring:
  application:
    name: fluxion-broker
  datasource:
    url: jdbc:mysql://mysql-host:3306/fluxion?useUnicode=true&characterEncoding=UTF-8
    username: fluxion
    password: <secure_password>

fluxion:
  broker:
    port: 9785
    protocol: HTTP
```

**最小生产配置（Worker）**：
```yaml
server:
  port: 8084

fluxion:
  worker:
    brokers:
      - http://broker-host:9785
    port: 9787
    tags:
      - env=prod
```

### 常见问题 FAQ

**Q: 任务调度延迟过大怎么办？**
A: 检查以下几点：
1. Broker 服务器时间和 Worker 是否同步
2. 调度线程池是否足够（默认单线程）
3. 数据库查询性能（schedule_delay 表索引）

**Q: Worker 频繁离线？**
A: 排查方法：
1. 检查网络稳定性（心跳超时 10 秒）
2. 检查 Worker 负载（CPU、内存）
3. 增加心跳间隔：fluxion.worker.heartbeat=5s

**Q: 数据库连接池耗尽？**
A: 解决方案：
1. 增加连接池大小：spring.datasource.hikari.maximum-pool-size=50
2. 检查慢查询并优化索引
3. 减少长时间执行的任务

---

## 业务幂等性指南

### 框架提供的幂等保护

Fluxion 框架提供**尽力而为**的重复执行防护机制：

| 机制 | 保护级别 | 说明 |
|------|----------|------|
| **调度租约 (Schedule Lease)** | 单 Broker | 15秒租约期内独占调度权，过期后可被其他 Broker 接管 |
| **执行租约 (Execution Lease)** | 单 Broker | 5分钟租约，Broker 故障后其他节点通过条件更新接管 |
| **围栏检查 (Fencing)** | 执行前 | 使用 MySQL NOW(3) 验证租约有效性，防止"僵尸"执行 |
| **分发去重 (Dispatch Deduplication)** | Worker 侧 | 同一 execution_id 的重复分发被拒绝 |
| **尝试计数 (Attempt Tracking)** | 持久化 | 记录执行尝试次数，用于熔断和告警 |

### ⚠️ 业务必须处理的幂等性

**框架不能保证 100% 幂等**，以下场景可能导致重复执行：

1. **分布式时钟偏差**：Broker 间 NOW(3) 差异可能导致短暂窗口期的重复调度
2. **租约边界竞争**：租约过期瞬间的并发 claim 可能产生竞态
3. **网络分区**：Worker 完成执行但确认丢失，触发超时重试
4. **脑裂恢复**：Broker 网络分区解除后，短暂双主可能重复分发

### 业务幂等实现建议

#### 方案1: 数据库唯一约束 (推荐)

```java
@Service
public class PaymentExecutor implements JobExecutor {
    
    @Autowired
    private PaymentRecordRepository paymentRepo;
    
    @Override
    public ExecuteResult execute(JobContext context) {
        String executionId = context.getExecutionId();
        Map<String, Object> params = context.getParams();
        String orderNo = params.get("orderNo").toString();
        
        try {
            // 使用数据库唯一约束防止重复支付
            PaymentRecord record = new PaymentRecord();
            record.setExecutionId(executionId);  // 唯一索引
            record.setOrderNo(orderNo);
            record.setAmount(new BigDecimal(params.get("amount").toString()));
            record.setStatus("PROCESSING");
            record.setCreatedAt(LocalDateTime.now());
            
            paymentRepo.save(record);
            
            // 执行实际支付逻辑
            doPayment(orderNo, record.getAmount());
            
            record.setStatus("SUCCESS");
            paymentRepo.save(record);
            
            return ExecuteResult.success();
            
        } catch (DuplicateKeyException e) {
            // 重复执行，直接返回成功
            log.info("[IDEMPOTENCY] Duplicate execution detected: executionId={}", executionId);
            return ExecuteResult.success();
        }
    }
}
```

#### 方案2: Redis SETNX 分布式锁

```java
@Service
public class InventoryExecutor implements JobExecutor {
    
    @Autowired
    private StringRedisTemplate redis;
    
    @Override
    public ExecuteResult execute(JobContext context) {
        String executionId = context.getExecutionId();
        String lockKey = "inventory:deduct:" + context.getParams().get("skuId");
        
        // 使用 Redis SETNX 保证幂等
        Boolean locked = redis.opsForValue()
            .setIfAbsent(lockKey + ":" + executionId, "1", Duration.ofMinutes(10));
        
        if (!Boolean.TRUE.equals(locked)) {
            log.warn("[IDEMPOTENCY] Duplicate inventory deduction: executionId={}", executionId);
            return ExecuteResult.success(); // 已处理过
        }
        
        try {
            // 执行库存扣减
            deductInventory(context.getParams());
            return ExecuteResult.success();
        } catch (Exception e) {
            // 失败时删除锁，允许重试
            redis.delete(lockKey + ":" + executionId);
            throw e;
        }
    }
}
```

#### 方案3: 状态机幂等

```java
@Service
public class OrderStateMachineExecutor implements JobExecutor {
    
    @Autowired
    private OrderRepository orderRepo;
    
    @Override
    public ExecuteResult execute(JobContext context) {
        String orderNo = context.getParams().get("orderNo").toString();
        String targetState = context.getParams().get("targetState").toString();
        
        Order order = orderRepo.findByOrderNo(orderNo);
        
        // 状态机检查：目标状态是否已经达成
        if (order.getState().equals(targetState)) {
            log.info("[IDEMPOTENCY] Order already in target state: orderNo={}, state={}", 
                orderNo, targetState);
            return ExecuteResult.success();
        }
        
        // 状态流转合法性检查
        if (!isValidTransition(order.getState(), targetState)) {
            log.error("[IDEMPOTENCY] Invalid state transition: {} -> {}", 
                order.getState(), targetState);
            return ExecuteResult.failure("Invalid state transition");
        }
        
        // 执行状态更新（乐观锁防止并发）
        int updated = orderRepo.updateState(orderNo, targetState, order.getVersion());
        if (updated == 0) {
            // 被其他执行更新，重新查询状态
            order = orderRepo.findByOrderNo(orderNo);
            if (order.getState().equals(targetState)) {
                return ExecuteResult.success(); // 目标已达成
            }
            return ExecuteResult.retry("Concurrent update, retry");
        }
        
        return ExecuteResult.success();
    }
}
```

### 幂等性检查清单

业务实现 Executor 时，检查以下项目：

- [ ] **是否存在唯一业务键**：如订单号、用户ID+操作类型+日期等
- [ ] **是否使用数据库唯一约束**：防止重复插入
- [ ] **是否检查前置状态**：接口幂等不等于"重复调用不报错"
- [ ] **是否记录执行日志**：便于排查重复执行问题
- [ ] **超时是否可重入**：长时间任务避免因超时触发无限重试
- [ ] **是否清理过期幂等记录**：防止存储无限膨胀

### 框架与业务的职责边界

```
┌─────────────────────────────────────────────────────────────┐
│                    Fluxion 框架职责                          │
├─────────────────────────────────────────────────────────────┤
│ • 调度层面的尽力去重（租约机制）                              │
│ • 分发层面的去重（Worker 侧过滤）                             │
│ • 故障后的安全恢复（保守重试）                                │
│ • 提供 execution_id 用于业务幂等                              │
└─────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    业务实现职责                              │
├─────────────────────────────────────────────────────────────┤
│ • 最终状态的一致性（幂等或补偿）                              │
│ • 副作用的可重入性（数据库、外部API）                         │
│ • 状态机或唯一约束的正确性                                    │
│ • 执行完成后的幂等记录清理                                    │
└─────────────────────────────────────────────────────────────┘
```

---

## 附录

### 配置参考

**最小生产配置（Broker）**：
```yaml
server:
  port: 9786

spring:
  application:
    name: fluxion-broker
  datasource:
    url: jdbc:mysql://mysql-host:3306/fluxion?useUnicode=true&characterEncoding=UTF-8
    username: fluxion
    password: <secure_password>

fluxion:
  broker:
    port: 9785
    protocol: HTTP
```

**最小生产配置（Worker）**：
```yaml
server:
  port: 8084

fluxion:
  worker:
    brokers:
      - http://broker-host:9785
    port: 9787
    tags:
      - env=prod
```

### 常见问题 FAQ

**Q: 任务调度延迟过大怎么办？**
A: 检查以下几点：
1. Broker 服务器时间和 Worker 是否同步
2. 调度线程池是否足够（默认单线程）
3. 数据库查询性能（schedule_delay 表索引）

**Q: Worker 频繁离线？**
A: 排查方法：
1. 检查网络稳定性（心跳超时 10 秒）
2. 检查 Worker 负载（CPU、内存）
3. 增加心跳间隔：fluxion.worker.heartbeat=5s

**Q: 数据库连接池耗尽？**
A: 解决方案：
1. 增加连接池大小：spring.datasource.hikari.maximum-pool-size=50
2. 检查慢查询并优化索引
3. 减少长时间执行的任务

---

*文档版本: 2026-07-21*
*适用版本: Fluxion 1.x*
