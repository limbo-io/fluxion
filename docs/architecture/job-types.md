# 任务类型

Fluxion 支持三种任务类型，满足不同场景的需求。

## 任务类型总览

| 类型 | 说明 | 特点 | 适用场景 |
|------|------|------|----------|
| **Normal** | 普通任务 | 单节点执行 | 常规业务处理 |
| **Broadcast** | 广播任务 | 所有节点执行 | 集群配置同步 |
| **MapReduce** | 并行任务 | 分布式并行处理 | 大数据处理 |

## 普通任务 (Normal)

### 特点

- 在单个 Worker 节点上执行一次
- 支持负载均衡选择节点
- 最简单的任务类型

### 执行流程

```
Broker                              Worker
  │                                   │
  │──── 触发调度 ────────────────────▶│
  │                                   │
  │[选择 Worker 节点]                  │
  │  - 执行器匹配                       │
  │  - 标签过滤                        │
  │  - 负载均衡选择                     │
  │                                   │
  │──── 下发任务 ────────────────────▶│ [执行任务]
  │                                   │
  │◀─── 执行结果 ────────────────────│
  │                                   │
  │[更新任务状态]                      │
```

### Worker 端处理

普通任务使用 `BasicJobTracker` 进行跟踪：

```java
public class BasicJobTracker extends JobTracker {
    @Override
    protected void run() {
        // 直接执行
        executor.run(taskContext);

        // 上报成功
        jobSuccess(result);
    }
}
```

## 广播任务 (Broadcast)

### 特点

- 同时下发给所有符合条件的 Worker 节点
- 每个节点独立执行
- 所有子任务完成后才算整体完成

### 执行流程

```
Broker
  │
  │──── 触发广播任务
  │
  │[查找所有符合条件的 Worker]
  │  Worker-1, Worker-2, Worker-3
  │
  ├───▶ Worker-1: 执行子任务 ──▶ 上报结果
  ├───▶ Worker-2: 执行子任务 ──▶ 上报结果
  ├───▶ Worker-3: 执行子任务 ──▶ 上报结果
  │
  │[收集所有子任务结果]
  │
  │─ 全部成功 → 任务完成
  │─ 任一失败 → 任务失败（可重试）
```

### Worker 端处理

广播任务使用 `BroadcastJobTracker` 进行跟踪：

```java
public class BroadcastJobTracker extends JobTracker {
    @Override
    protected void run() {
        // 执行本地任务
        executor.run(taskContext);

        // 上报执行完成
        // 等待 Broker 确认所有节点完成
        jobSuccess(result);
    }
}
```

### 使用场景

1. **配置同步**
   ```java
   // 同步配置到所有节点
   @Component
   public class ConfigSyncExecutor implements Executor {
       @Override
       public void run(TaskContext context) {
           String config = (String) context.getParams().get("config");
           ConfigManager.refresh(config);
       }
   }
   ```

2. **缓存刷新**
   ```java
   // 刷新所有节点的本地缓存
   @Component
   public class CacheRefreshExecutor implements Executor {
       @Override
       public void run(TaskContext context) {
           String cacheKey = (String) context.getParams().get("key");
           CacheManager.invalidate(cacheKey);
       }
   }
   ```

3. **健康检查**
   ```java
   // 在 Worker 节点执行健康检查
   @Component
   public class HealthCheckExecutor implements Executor {
       @Override
       public void run(TaskContext context) {
           // 检查本地资源使用情况
           // 上报到监控系统
       }
   }
   ```

## MapReduce 任务

### 特点

- 支持分布式并行处理
- 三阶段执行：Map -> Run -> Reduce
- 支持结果聚合

### 执行流程

```
Phase 1: Map (拆分)
┌─────────┐
│  Broker  │───▶ 调用执行器的 map() 方法
└────┬────┘     将大任务拆分为多个子任务
     │
     ▼
┌─────────────────────────────────────┐
│  SubTask-1, SubTask-2, ..., SubTask-N │
└─────────────────────────────────────┘

Phase 2: Run (执行)
┌─────────┐
│  Broker  │───▶ 将子任务分发到多个 Worker
└────┬────┘
     │
     ├────▶ Worker-1: 执行 SubTask-1 ──▶ Result-1
     ├────▶ Worker-2: 执行 SubTask-2 ──▶ Result-2
     ├────▶ Worker-3: 执行 SubTask-3 ──▶ Result-3
     │          ...
     └────▶ Worker-N: 执行 SubTask-N ──▶ Result-N

Phase 3: Reduce (聚合)
┌─────────┐
│  Broker  │───▶ 收集所有子任务结果
└────┬────┘     调用执行器的 reduce() 方法
     │         聚合最终结果
     ▼
┌─────────┐
│  Result │
└─────────┘
```

### Worker 端处理

MapReduce 任务使用 `MapReduceJobTracker` 进行跟踪：

```java
public class MapReduceJobTracker extends JobTracker {
    @Override
    protected void run() {
        if (isMapPhase()) {
            // Map 阶段：拆分子任务
            List<Map<String, Object>> subTasks =
                ((MapReduceExecutor) executor).map(taskContext);
            // 上报子任务列表
            reportSubTasks(subTasks);
        } else if (isRunPhase()) {
            // Run 阶段：执行单个子任务
            String result = ((MapReduceExecutor) executor).run(subTaskParams);
            jobSuccess(result);
        } else if (isReducePhase()) {
            // Reduce 阶段：聚合结果
            List<String> subResults = fetchSubResults();
            String finalResult = ((MapReduceExecutor) executor).reduce(subResults);
            jobSuccess(finalResult);
        }
    }
}
```

### 使用场景

1. **大数据处理**
   ```java
   @Component
   public class LogAnalysisExecutor implements MapReduceExecutor {
       @Override
       public List<Map<String, Object>> map(TaskContext context) {
           // 按日期拆分日志分析任务
           List<Map<String, Object>> shards = new ArrayList<>();
           for (String date : getDateRange()) {
               Map<String, Object> shard = new HashMap<>();
               shard.put("date", date);
               shards.add(shard);
           }
           return shards;
       }

       @Override
       public String run(Map<String, Object> shard) {
           String date = (String) shard.get("date");
           // 分析某天日志
           return analyzeLogs(date);
       }

       @Override
       public String reduce(List<String> results) {
           // 汇总统计结果
           return aggregateResults(results);
       }
   }
   ```

2. **批量数据处理**
   ```java
   @Component
   public class DataMigrationExecutor implements MapReduceExecutor {
       @Override
       public List<Map<String, Object>> map(TaskContext context) {
           // 按 ID 范围拆分子任务
           List<Map<String, Object>> shards = new ArrayList<>();
           for (int i = 0; i < 10; i++) {
               Map<String, Object> shard = new HashMap<>();
               shard.put("startId", i * 10000);
               shard.put("endId", (i + 1) * 10000);
               shards.add(shard);
           }
           return shards;
       }

       @Override
       public String run(Map<String, Object> shard) {
           // 处理一个 ID 范围的数据
           migrateDataRange(shard);
           return "Migrated: " + shard.get("startId") + " - " + shard.get("endId");
       }

       @Override
       public String reduce(List<String> results) {
           return "Total migrated: " + results.size() + " batches";
       }
   }
   ```

## 任务状态流转

### 状态定义

```java
public enum JobStatus {
    PENDING,      // 等待调度
    SCHEDULING,   // 调度中
    DISPATCHING,  // 分发中
    RUNNING,      // 执行中
    SUCCEED,      // 执行成功
    FAILED;       // 执行失败
}
```

### 状态流转图

```
                    ┌─────────────┐
         ┌─────────│   PENDING   │──────────┐
         │         │  (等待调度)  │          │
         │         └──────┬──────┘          │
         │                │ 调度触发          │
         │                ▼                 │
         │         ┌─────────────┐          │
         │         │ SCHEDULING  │          │
         │         │  (调度中)    │          │
         │         └──────┬──────┘          │
         │                │ 选择 Worker      │
         │                ▼                 │
         │         ┌─────────────┐          │
         ├────────▶│ DISPATCHING │          │
重试/重调度          │  (分发中)    │          │
         │         └──────┬──────┘          │
         │                │ 下发成功          │
         │                ▼                 │
         │         ┌─────────────┐          │
         │         │   RUNNING   │          │
         │         │  (执行中)    │          │
         │         └──────┬──────┘          │
         │           ┌────┴────┐            │
         │           │         │            │
         │           ▼         ▼            │
         │    ┌─────────┐ ┌─────────┐       │
         └────│ SUCCEED │ │  FAILED │◀──────┘
              │(执行成功) │ │(执行失败) │
              └─────────┘ └─────────┘
```

## 对比总结

| 特性 | Normal | Broadcast | MapReduce |
|------|--------|-----------|-----------|
| 执行节点数 | 1 | N (所有匹配节点) | N (动态分配) |
| 结果聚合 | 直接返回 | 需等待全部完成 | 自动 reduce |
| 适用场景 | 一般任务 | 集群操作 | 大数据处理 |
| 复杂度 | 低 | 中 | 高 |
| 失败处理 | 单点重试 | 子任务重试 | 子任务重试 |

## 选择建议

1. **使用 Normal 类型**：
   - 单节点即可完成的任务
   - 不需要特殊并行处理
   - 追求最简单实现

2. **使用 Broadcast 类型**：
   - 需要在所有节点执行相同操作
   - 配置同步、缓存刷新等场景
   - 集群级别的操作

3. **使用 MapReduce 类型**：
   - 数据量大，需要分布式处理
   - 任务可以拆分为独立子任务
   - 需要结果聚合
