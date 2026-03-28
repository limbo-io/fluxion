# 执行器模式 (Executor Pattern)

执行器是 Fluxion 中任务执行的核心抽象，业务方通过实现执行器接口来定义具体的业务逻辑。

## 核心接口

### Executor

```java
public interface Executor {
    /**
     * 运行执行器
     *
     * @param context 任务执行上下文
     */
    void run(TaskContext context);

    /**
     * 执行器名称，默认为执行器类的类全名
     */
    default String name() {
        return this.getClass().getName();
    }
}
```

**设计要点：**
- 单一职责：每个执行器只做一件事
- 简单接口：仅需实现 `run` 方法
- 默认命名：使用类全名作为默认名称
- 线程安全：执行器实例需保证线程安全

### TaskContext

任务执行上下文，封装任务执行所需的所有信息。

```java
public class TaskContext {
    private String jobId;           // 作业 ID
    private String taskId;          // 任务 ID
    private String executorName;    // 执行器名称
    private Map<String, Object> params;  // 任务参数

    /**
     * 获取任务参数
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * 获取子任务参数（MapReduce 场景）
     */
    public List<Map<String, Object>> getSubTasks() {
        // ...
    }
}
```

## 执行器类型

### 普通执行器

最基本的执行器，适用于单节点执行场景。

```java
@Component
public class HelloExecutor implements Executor {
    @Override
    public void run(TaskContext context) {
        String name = (String) context.getParams().get("name");
        System.out.println("Hello, " + name + "!");
    }

    @Override
    public String name() {
        return "helloExecutor";
    }
}
```

### MapReduce 执行器

支持分布式并行处理的执行器。

```java
@Component
public class DataProcessExecutor implements MapReduceExecutor {
    /**
     * 分发阶段：将任务拆分为子任务
     */
    @Override
    public List<Map<String, Object>> map(TaskContext context) {
        List<Map<String, Object>> subTasks = new ArrayList<>();
        // 将大数据集拆分为多个小任务
        for (int i = 0; i < 10; i++) {
            Map<String, Object> subTask = new HashMap<>();
            subTask.put("shardIndex", i);
            subTasks.add(subTask);
        }
        return subTasks;
    }

    /**
     * 执行阶段：执行单个子任务
     */
    @Override
    public String run(Map<String, Object> subTask) {
        int shardIndex = (int) subTask.get("shardIndex");
        // 处理数据分片
        return "Processed shard: " + shardIndex;
    }

    /**
     * 聚合阶段：合并所有子任务结果
     */
    @Override
    public String reduce(List<String> results) {
        // 汇总所有子任务结果
        return "Total processed: " + results.size();
    }
}
```

**执行流程：**

```
Broker                              Worker
  │                                    │
  │──── 下发 MapReduce 任务 ──────────▶│
  │                                    │ [执行 map() 拆分子任务]
  │                                    │
  │◀─── 返回子任务列表 ────────────────│
  │                                    │
  │[Broker 分发子任务到多个 Worker]      │
  │                                    │
  │──── 下发子任务 1 ─────────────────▶│ [执行 run()] ──▶ 返回结果
  │──── 下发子任务 2 ─────────────────▶│ [执行 run()] ──▶ 返回结果
  │──── 下发子任务 3 ─────────────────▶│ [执行 run()] ──▶ 返回结果
  │                                    │
  │[收集所有子任务结果]                  │
  │                                    │
  │──── 下发 Reduce 任务 ─────────────▶│ [执行 reduce()] ─▶ 最终结果
```

## Worker 执行器发现

### Spring 组件扫描

Worker 启动时会自动扫描并注册所有执行器：

```java
@Component
public class ExecutorMethodProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof Executor) {
            Executor executor = (Executor) bean;
            registerExecutor(executor.name(), executor);
        }
        return bean;
    }
}
```

### 注册流程

```
┌────────────────┐    ┌────────────────┐    ┌────────────────┐
│   Spring 容器   │───▶│  扫描 @Component│───▶│ 识别 Executor  │
│   启动         │    │   注解类        │    │   接口实现     │
└────────────────┘    └────────────────┘    └───────┬────────┘
                                                    │
                                                    ▼
┌────────────────┐    ┌────────────────┐    ┌────────────────┐
│  Worker 启动   │◀───│  注册执行器信息  │◀───│  发送注册事件   │
│   完成         │    │   到 Broker     │    │   ExecutorScannedEvent
└────────────────┘    └────────────────┘    └────────────────┘
```

## 执行器配置

### 任务配置

创建任务时指定执行器：

```java
// 使用执行器名称引用
ExecutorExecuteConfig config = new ExecutorExecuteConfig();
config.setExecutorName("io.fluxion.demo.executor.HelloExecutor");
// 或
config.setExecutorName("helloExecutor");  // 如果有自定义 name()

// 设置任务参数
config.setParams(Map.of("name", "World"));
```

### 超时配置

```java
OvertimeOption overtime = new OvertimeOption();
overtime.setTimeoutSeconds(300);  // 5 分钟超时
config.setOvertimeOption(overtime);
```

### 重试配置

```java
RetryOption retry = new RetryOption();
retry.setRetryType(RetryType.ON_FAILURE);  // 失败时重试
retry.setMaxRetryTimes(3);                 // 最多重试 3 次
retry.setRetryIntervalSeconds(30);         // 重试间隔 30 秒
config.setRetryOption(retry);
```

## 最佳实践

### 1. 幂等性设计

执行器可能被重复调用，需要保证幂等性：

```java
@Component
public class IdempotentExecutor implements Executor {
    @Autowired
    private IdempotencyService idempotencyService;

    @Override
    public void run(TaskContext context) {
        String taskId = context.getTaskId();

        // 检查是否已执行
        if (idempotencyService.isExecuted(taskId)) {
            return;  // 已执行，直接返回
        }

        // 执行业务逻辑
        doBusiness();

        // 标记为已执行
        idempotencyService.markExecuted(taskId);
    }
}
```

### 2. 异常处理

执行器应妥善处理异常，避免影响 Worker 稳定性：

```java
@Component
public class RobustExecutor implements Executor {
    @Override
    public void run(TaskContext context) {
        try {
            doWork();
        } catch (BusinessException e) {
            // 业务异常，记录日志
            log.error("Business error: {}", e.getMessage());
            throw e;  // 抛出让框架处理
        } catch (Exception e) {
            // 未知异常，包装后抛出
            log.error("Unexpected error", e);
            throw new ExecutorException("Execution failed", e);
        }
    }
}
```

### 3. 资源管理

正确管理资源，避免泄漏：

```java
@Component
public class ResourceAwareExecutor implements Executor {
    @Override
    public void run(TaskContext context) {
        // 使用 try-with-resources
        try (Connection conn = dataSource.getConnection();
             InputStream is = fetchData()) {
            processData(conn, is);
        } catch (IOException | SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
```

### 4. 执行器命名

建议使用有意义的名称：

```java
// 好的命名
@Override
public String name() {
    return "orderProcessingExecutor";
}

// 避免使用无意义的名称
@Override
public String name() {
    return "executor1";  // 不推荐
}
```

## 注意事项

1. **线程安全**：执行器实例被多线程调用，需保证线程安全
2. **无状态设计**：避免在执行器中维护状态，使用外部存储
3. **快速失败**：发现问题立即抛出异常，不要长时间阻塞
4. **日志记录**：记录关键执行信息，便于排查问题
5. **参数校验**：对输入参数进行合法性检查
