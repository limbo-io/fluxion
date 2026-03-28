# 任务调度流程

本文档详细说明 Fluxion 的任务调度流程和机制。

## 调度架构

```
┌─────────────────────────────────────────────────────────────────┐
│                           Scheduler                             │
│                         (调度引擎)                               │
└─────────────────────┬───────────────────────────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        ▼             ▼             ▼
┌──────────────┐ ┌─────────────┐ ┌─────────────┐
│  Schedule    │ │   Trigger   │ │   Delayed   │
│  (周期调度)   │ │   (触发器)   │ │   (延迟执行) │
└──────┬───────┘ └──────┬──────┘ └──────┬──────┘
       │                │               │
       ▼                ▼               ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Execution Engine                         │
│                        (执行引擎)                                │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Worker Dispatcher                         │
│                       (Worker 分发器)                            │
└─────────────────────────────────────────────────────────────────┘
```

## 调度类型

### 1. Fixed Rate (固定速率)

固定时间间隔触发，不管任务是否执行完成。

```
Trigger:     │     │     │     │     │
             0s   30s   60s   90s  120s
             │     │     │     │     │
Task:        [====][====][====][====][====]
                   ↑
            即使上次未结束，
            也按时触发下一次
```

**适用场景：**
- 定期数据同步
- 定时检查任务
- 不要求任务顺序执行

**配置：**
```java
ScheduleTriggerConfig config = new ScheduleTriggerConfig();
config.setType(ScheduleType.FIXED_RATE);
config.setIntervalSeconds(30);  // 每 30 秒触发
```

### 2. Fixed Delay (固定延迟)

任务执行完成后，等待固定时间再触发下一次。

```
Trigger:     │          │               │
             0s        45s             90s
             │          │               │
Task:        [==== 30s ==][=== 15s ===][====]
                         ↑
            从上次任务结束后
            开始计算间隔时间
```

**适用场景：**
- 要求任务顺序执行
- 避免任务堆积
- 控制执行频率

**配置：**
```java
ScheduleTriggerConfig config = new ScheduleTriggerConfig();
config.setType(ScheduleType.FIXED_DELAY);
config.setIntervalSeconds(30);  // 任务完成后 30 秒触发
```

### 3. CRON 表达式

基于 Quartz CRON 表达式触发。

```
示例: 0 0 9 * * ?  (每天上午 9 点)

秒 分 时 日 月 周 年
0  0  9  *  *  ?  *

      │
      ▼
    09:00:00 ──────▶ 触发任务
```

**适用场景：**
- 复杂的定时需求
- 规定时间点执行

**配置：**
```java
ScheduleTriggerConfig config = new ScheduleTriggerConfig();
config.setType(ScheduleType.CRON);
config.setCron("0 0 9 * * ?");  // 每天 9 点
```

## 调度计算

### ScheduleCalculator 接口

```java
public interface ScheduleCalculator {
    /**
     * 计算下一次调度时间
     *
     * @param baseTime     基准时间
     * @param scheduleConfig 调度配置
     * @return 下一次调度时间，为 null 表示不再调度
     */
    LocalDateTime calculate(LocalDateTime baseTime, Object scheduleConfig);
}
```

### 计算工厂

```java
public class ScheduleCalculatorFactory {
    public static ScheduleCalculator getCalculator(ScheduleType type) {
        return switch (type) {
            case FIXED_RATE -> new FixedRateScheduleCalculator();
            case FIXED_DELAY -> new FixedDelayScheduleCalculator();
            case CRON -> new CronScheduleCalculator();
            case NEVER -> new NeverScheduleCalculator();
        };
    }
}
```

## 调度执行流程

### 完整流程

```
┌─────────────┐
│  调度触发    │◀───────────────────────────────────────┐
│  (Trigger)  │                                        │
└──────┬──────┘                                        │
       │                                               │
       ▼                                               │
┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │
│   获取任务   │───▶│   创建执行   │───▶│   选择节点   │  │
│   配置      │    │   实例      │    │  (Worker)   │  │
└─────────────┘    └─────────────┘    └──────┬──────┘  │
                                              │         │
                                              ▼         │
                                     ┌─────────────┐    │
                                     │   下发任务   │    │
                                     │  Worker     │    │
                                     └──────┬──────┘    │
                                            │           │
                                            ▼           │
                                     ┌─────────────┐    │
                                     │   任务执行   │    │
                                     │  (Executor) │    │
                                     └──────┬──────┘    │
                                            │           │
                                            ▼           │
                                     ┌─────────────┐    │
                                     │   结果上报   │────┘
                                     │  (Report)   │ (计算下次调度)
                                     └─────────────┘
```

### 详细步骤

1. **触发调度**
   - 定时任务轮询待调度的任务
   - 触发时间到达的任务被选中

2. **创建执行实例**
   - 根据任务配置生成执行记录
   - 写入数据库持久化
   - 唯一 ID 标识本次执行

3. **Worker 选择**
   - 执行节点过滤
   - 应用负载均衡策略
   - 选出目标 Worker

4. **任务下发**
   - 将任务信息发送到选中的 Worker
   - Worker 确认接收

5. **任务执行**
   - Worker 创建 JobTracker
   - 执行器执行业务逻辑
   - 上报执行状态

6. **结果处理**
   - 接收 Worker 上报的结果
   - 更新执行记录
   - 计算下次调度时间（周期任务）

## 触发器类型

### 1. ScheduleTrigger (调度触发器)

基于时间规则自动触发。

```java
public class ScheduleTriggerConfig {
    private ScheduleType type;           // FIXED_RATE / FIXED_DELAY / CRON
    private String cron;                 // CRON 表达式
    private Long intervalSeconds;        // 间隔秒数
    private LocalDateTime startTime;     // 开始时间
    private LocalDateTime endTime;       // 结束时间
}
```

### 2. WebhookTrigger (Webhook 触发器)

通过 HTTP 请求触发。

```java
public class WebhookTriggerConfig {
    private String webhookId;            // Webhook 标识
    private String secret;               // 签名密钥
    private Map<String, String> headers; // 请求头要求
}
```

**调用方式：**
```bash
curl -X POST https://broker/api/webhook/{webhookId} \
  -H "Content-Type: application/json" \
  -H "X-Signature: {signature}" \
  -d '{"param1": "value1"}'
```

### 3. DelayTrigger (延迟触发器)

延迟一定时间后执行。

```java
public class DelayTriggerConfig {
    private Long delaySeconds;           // 延迟秒数
    private LocalDateTime triggerAt;     // 指定触发时间
}
```

**使用场景：**
- 延时消息
- 超时处理
- 定时提醒

## 延迟任务调度

### DelayedTask 实现

```java
public class DelayedTask implements Comparable<DelayedTask> {
    private String taskId;
    private LocalDateTime triggerTime;
    private Runnable task;

    @Override
    public int compareTo(DelayedTask other) {
        return this.triggerTime.compareTo(other.triggerTime);
    }
}
```

使用优先队列管理延迟任务，由独立线程轮询执行。

### 调度流程

```
┌────────────────┐
│  提交延迟任务   │
│ (delay 60s)   │
└───────┬────────┘
        │
        ▼
┌────────────────┐
│   优先队列      │
│   (Min-Heap)   │
└───────┬────────┘
        │
        │ ◄── 每秒轮询
        ▼
┌────────────────┐     ┌─────────────┐
│ 时间到达？      │──是─▶│ 取出执行任务 │
│ (triggerTime   │     └─────────────┘
│  <= now)       │
└───────┬────────┘
        │否
        │
        └───────▶ 继续等待
```

## 任务状态管理

### 状态流转

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│  WAITING │───▶│ PENDING  │───▶│ RUNNING  │───▶│ SUCCEED  │
│ (待触发)  │    │ (待调度)  │    │ (执行中)  │    │ (成功)   │
└──────────┘    └──────────┘    └────┬─────┘    └──────────┘
                                     │
                                     ▼
                               ┌──────────┐
                               │  FAILED  │
                               │ (失败)    │
                               └────┬─────┘
                                    │
                                    │ (配置重试)
                                    └────────────▶ PENDING
```

### 状态持久化

所有状态变更都持久化到数据库，保证可靠性：

```sql
-- 更新执行状态
UPDATE fluxion_execution
SET status = 'RUNNING',
    worker_id = ?,
    start_at = NOW()
WHERE id = ?;
```

## 容错机制

### 1. 超时处理

```java
if (taskStartTime.plus(timeoutSeconds, ChronoUnit.SECONDS).isBefore(now)) {
    // 任务执行超时
    markExpired(executionId);
    // 触发重试逻辑
    retryIfNeeded(executionId);
}
```

### 2. 失败重试

```java
if (execution.getRetryTimes() < maxRetryTimes) {
    // 延迟重试
    scheduleRetry(execution, retryIntervalSeconds);
} else {
    // 标记最终失败
    markFailed(executionId);
}
```

### 3.  Worker 失联处理

```java
if (workerLastHeartbeat.isBefore(now.minus(heartbeatTimeout, ChronoUnit.SECONDS))) {
    // Worker 失联
    offlineWorker(workerId);
    // 重新分配执行中的任务
    reassignRunningTasks(workerId);
}
```

## 集群调度

### 多 Broker 协调

```
         ┌──────────┐
         │  MySQL   │
         │ (分布式锁) │
         └────┬─────┘
              │
    ┌─────────┼─────────┐
    │         │         │
    ▼         ▼         ▼
┌───────┐ ┌───────┐ ┌───────┐
│Broker1│ │Broker2│ │Broker3│
│(Active)│ │(Active)│ │(Active)│
└────┬──┘ └────┬──┘ └────┬──┘
     │         │         │
     └─────────┼─────────┘
               │
               ▼
        ┌───────────────┐
        │  Worker 集群   │
        └───────────────┘
```

使用数据库锁保证同一时刻只有一个 Broker 调度特定任务。
