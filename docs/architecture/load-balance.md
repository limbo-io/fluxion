# 负载均衡策略

Fluxion 提供多种负载均衡策略，用于在多个 Worker 节点之间分发任务。

## 策略总览

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| Random | 随机选择 | 无状态任务，节点性能相近 |
| RoundRobin | 轮询 | 均匀分配，简单场景 |
| LFU | 最不经常使用 | 避免热点节点 |
| LRU | 最近最少使用 | 均衡节点负载 |
| ConsistentHash | 一致性哈希 | 相同参数路由到同一节点 |
| Appoint | 指定节点 | 特定任务绑定节点 |

## 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    WorkerSelector                           │
│                   (Worker 选择器)                            │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    LBStrategy                               │
│              (负载均衡策略接口)                               │
└───────────────────────────┬─────────────────────────────────┘
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│   Random     │   │RoundRobin    │   │ConsistentHash│
│  (随机)       │   │   (轮询)      │   │ (一致性哈希)  │
└──────────────┘   └──────────────┘   └──────────────┘
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│     LFU      │   │     LRU      │   │   Appoint    │
│(最不经常用)   │   │(最近最少用)   │   │   (指定)      │
└──────────────┘   └──────────────┘   └──────────────┘
```

## 核心接口

### LBStrategy

```java
public interface LBStrategy<S extends LBServer> {
    /**
     * 选择一个服务
     *
     * @param servers    被负载的服务列表
     * @param invocation 本次调用的上下文信息
     * @return 选择的服务，可能为 null
     */
    S select(List<S> servers, Invocation invocation);
}
```

### Invocation 上下文

```java
public class Invocation {
    private String targetPath;              // 目标路径
    private Map<String, Object> attachments; // 附加信息
}
```

Invocation 携带调用的上下文信息，策略可以基于这些信息做出选择决策。例如，一致性哈希策略使用 attachment 中的参数计算哈希值。

## 策略实现

### RandomLBStrategy (随机)

```java
public class RandomLBStrategy<S extends LBServer> extends AbstractLBStrategy<S> {
    private final Random random = new Random();

    @Override
    public S select(List<S> servers, Invocation invocation) {
        int size = servers.size();
        if (size == 0) return null;
        if (size == 1) return servers.get(0);
        return servers.get(random.nextInt(size));
    }
}
```

**特点：**
- 实现简单，性能最高
- 任务均匀随机分布
- 无状态，不需要统计信息

**适用场景：**
- 任务之间无依赖
- Worker 节点性能相近
- 不需要数据局部性

### RoundRobinLBStrategy (轮询)

```java
public class RoundRobinLBStrategy<S extends LBServer> extends AbstractLBStrategy<S> {
    private final AtomicInteger counter = new AtomicInteger(0);

    @Override
    public S select(List<S> servers, Invocation invocation) {
        int size = servers.size();
        if (size == 0) return null;
        if (size == 1) return servers.get(0);
        return servers.get(Math.abs(counter.getAndIncrement() % size));
    }
}
```

**特点：**
- 按顺序循环选择
- 任务分配均匀
- 原子计数器保证线程安全

**适用场景：**
- 需要均匀分配任务
- 任务执行时间相近
- 简单场景首选

### LFULBStrategy (最不经常使用)

```java
public class LFULBStrategy<S extends LBServer> extends AbstractLBStrategy<S> {
    @Override
    public S select(List<S> servers, Invocation invocation) {
        // 选择时间窗口内接收任务最少的节点
        return servers.stream()
            .min(Comparator.comparingLong(s -> s.statistics().accessCount()))
            .orElse(null);
    }
}
```

**特点：**
- 基于历史访问频率
- 避免热点节点
- 适合长周期统计

**适用场景：**
- 节点性能有差异
- 需要平衡长期负载
- 避免某些节点过载

### LRULBStrategy (最近最少使用)

```java
public class LRULBStrategy<S extends LBServer> extends AbstractLBStrategy<S> {
    @Override
    public S select(List<S> servers, Invocation invocation) {
        // 选择最长时间没有接收任务的节点
        return servers.stream()
            .min(Comparator.comparingLong(s -> s.statistics().lastAccessTime()))
            .orElse(null);
    }
}
```

**特点：**
- 基于最近访问时间
- 优先选择空闲节点
- 响应式负载均衡

**适用场景：**
- 任务执行时间差异大
- 需要快速响应负载变化
- 短期负载均衡

### ConsistentHashLBStrategy (一致性哈希)

```java
public class ConsistentHashLBStrategy<S extends LBServer> extends AbstractLBStrategy<S> {
    @Override
    public S select(List<S> servers, Invocation invocation) {
        // 基于任务参数计算哈希值
        Object key = invocation.attachment("hashKey");
        int hash = key.hashCode();
        // 使用一致性哈希环选择节点
        // ...
    }
}
```

**特点：**
- 相同参数总是路由到同一节点
- 节点增减只影响局部映射
- 需要 Invocation 提供 hashKey

**适用场景：**
- 需要数据局部性
- 缓存亲和性要求
- 相同参数任务聚合

### AppointLBStrategy (指定节点)

```java
public class AppointLBStrategy<S extends LBServer> extends AbstractLBStrategy<S> {
    @Override
    public S select(List<S> servers, Invocation invocation) {
        // 从 invocation 获取指定节点
        String appointWorkerId = (String) invocation.attachment("appointWorkerId");
        return servers.stream()
            .filter(s -> s.id().equals(appointWorkerId))
            .findFirst()
            .orElse(null);
    }
}
```

**特点：**
- 按指定节点派发
- 可用于调试或特殊任务
- fallback 到其他策略

**适用场景：**
- 任务绑定特定节点
- 调试和测试
- 特定资源依赖

## 节点过滤

在选择节点之前，Broker 会先进行节点过滤：

### 过滤条件

```
1. Executor 匹配
   - Worker 必须包含任务对应的执行器

2. 标签匹配
   - Worker 必须满足任务的标签要求
   - 支持 k=v 形式的键值对过滤

3. 资源限制检查
   - 队列必须有可用空间 (availableQueueNum > 0)
   - CPU负载必须不超过最大允许值 (cpuLoad <= maxCpuLoad)
   - 可用内存必须满足最小要求 (freeMemory >= minFreeMemory)
```

### 资源过滤语义

在 `DispatchOption` 中配置资源限制：

```java
DispatchOption option = new DispatchOption();
option.setMaxCpuLoad(80.0);     // 最大允许CPU负载（百分比）
option.setMinFreeMemory(512L);  // 最小可用内存（MB）
```

**Worker资源检查规则：**

| 资源指标 | 过滤条件 | 说明 |
|---------|---------|------|
| 队列空间 | `availableQueueNum > 0` | 必须有可用队列槽位 |
| CPU负载 | `cpuLoad <= maxCpuLoad` | Worker当前负载不超过最大允许值 |
| 可用内存 | `freeMemory >= minFreeMemory` | Worker可用内存满足最小要求 |

**注意：**
- `maxCpuLoad <= 0` 表示不限制CPU
- `minFreeMemory <= 0` 表示不限制内存
- 边界值处理：`cpuLoad == maxCpuLoad` 是允许的（通过）

### 过滤流程

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  所有在线    │───▶│  Executor   │───▶│    标签     │───▶│   容量检查   │
│  Worker 节点 │    │    过滤     │    │    过滤     │    │             │
└─────────────┘    └─────────────┘    └─────────────┘    └──────┬──────┘
                                                                │
                                                                ▼
                                                        ┌─────────────┐
                                                        │  候选节点列表 │
                                                        └──────┬──────┘
                                                               │
                                                               ▼
                                                        ┌─────────────┐
                                                        │  LBStrategy │
                                                        │   选择节点    │
                                                        └─────────────┘
```

## 配置方式

### Server 端配置

```yaml
fluxion:
  broker:
    load-balance-strategy: ROUND_ROBIN  # 默认策略
```

可选值：`RANDOM`, `ROUND_ROBIN`, `LFU`, `LRU`, `CONSISTENT_HASH`, `APPOINT`

### 任务级配置

可以在创建任务时指定特定策略：

```java
DispatchOption option = new DispatchOption();
option.setLoadBalanceType(LoadBalanceType.CONSISTENT_HASH);
option.setAppointWorkerId("worker-001");  // 用于 APPOINT 策略
```

## 统计信息

### LBServerStatistics

```java
public interface LBServerStatistics {
    /**
     * 访问次数
     */
    long accessCount();

    /**
     * 最后访问时间
     */
    long lastAccessTime();

    /**
     * 平均响应时间
     */
    long averageResponseTime();
}
```

统计数据用于 LFU、LRU 等策略的决策依据。
