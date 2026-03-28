# 远程通信层设计

Fluxion 采用分层设计的通信架构，支持多种底层实现（HTTP/Netty），保证通信层的可扩展性。

## 架构分层

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                        │
│              (Server Core / Worker Core)                    │
└───────────────────────────┬─────────────────────────────────┘
                            │ Client.call(request)
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Client Abstraction                       │
│              (LBClient / RetryableClient)                   │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   Client Implementation                     │
│                  (OKHttpClient / Netty)                     │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP / Netty
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Server Abstraction                       │
│                (ClientServer / Handler)                     │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                   Server Implementation                     │
│               (NettyHttpClientServer)                       │
└─────────────────────────────────────────────────────────────┘
```

## 核心抽象

### Client 接口

```java
public interface Client {
    /**
     * 发送请求并获取响应
     */
    <R> Response<R> call(URL url, Request<R> request);

    /**
     * 客户端使用的协议
     */
    Protocol protocol();
}
```

**职责：** 定义 RPC 客户端的基本能力，所有远程调用都基于此接口。

### ClientServer 接口

```java
public interface ClientServer {
    /**
     * 启动服务
     */
    void start();

    /**
     * 停止服务
     */
    void stop();

    /**
     * 注册请求处理器
     */
    void registerHandler(ClientHandler<?> handler);
}
```

**职责：** 定义 RPC 服务端的生命周期管理和请求处理。

### ClientHandler 接口

```java
public interface ClientHandler<T> {
    /**
     * 处理请求
     */
    Response<T> handle(Request<T> request);

    /**
     * 获取处理的请求路径
     */
    String path();
}
```

**职责：** 处理特定路径的请求，实现业务逻辑。

## 负载均衡客户端

### LBClient

基于负载均衡策略的客户端，自动选择目标节点。

```java
public class LBClient implements Client {
    // 从服务列表中选择节点
    // 支持多种负载均衡策略
}
```

### RetryableClient

带重试机制的客户端，处理临时网络故障。

```java
public class RetryableClient implements Client {
    // 失败自动重试
    // 可配置重试次数和间隔
}
```

### RetryableLBClient

组合负载均衡和重试功能的客户端。

## 通信协议

### HTTP 协议 (默认)

**特点：**
- 基于标准 HTTP/1.1
- 使用 JSON 序列化
- 易于调试和监控
- 兼容性最好

**实现：**
- 客户端: `OKHttpClient`
- 服务端: `NettyHttpClientServer`

### Netty RPC 协议 (可选)

**特点：**
- 基于 Netty 的高性能实现
- 自定义二进制协议
- 更低的延迟和更高的吞吐量

**实现：**
- `NettyClientServerFactory` 创建服务端
- `NettyHttpClientServer` 处理连接

## API 规范

### 请求格式

```java
public class Request<T> {
    private String path;        // 请求路径
    private T data;             // 请求数据
    private Map<String, String> headers;  // 请求头
}
```

### 响应格式

```java
public class Response<T> {
    private boolean success;    // 是否成功
    private T data;             // 响应数据
    private String errorMsg;    // 错误信息
    private int code;           // 状态码
}
```

## Broker 与 Worker 通信

### Worker → Broker 的 API

| 接口 | 路径 | 说明 |
|------|------|------|
| WorkerRegister | /broker/worker/register | Worker 注册 |
| WorkerHeartbeat | /broker/worker/heartbeat | 心跳上报 |
| JobReport | /broker/job/report | 作业状态上报 |
| JobStateTransition | /broker/job/state/transition | 作业状态变更 |

### Broker → Worker 的 API

| 接口 | 路径 | 说明 |
|------|------|------|
| TaskDispatch | /worker/task/dispatch | 任务下发 |
| TaskReport | /worker/task/report | 任务状态查询 |
| TaskStateTransition | /worker/task/state/transition | 任务状态变更 |

## 心跳机制

### HeartbeatPacemaker

负责维护与远程节点的心跳连接。

```java
public class HeartbeatPacemaker {
    // 定期发送心跳请求
    // 检测节点存活状态
    // 触发失效节点清理
}
```

### 心跳流程

```
Worker                    Broker
  |                         |
  │──── HeartbeatRequest ──▶│
  │                         │ [更新节点状态]
  │◀─── HeartbeatResponse ──│
  │                         │
  │    [每 2 秒重复]        │
```

## 节点选择策略

### LBStrategy 接口

```java
public interface LBStrategy<S extends LBServer> {
    /**
     * 从服务列表中选择一个服务
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
    private String targetPath;     // 目标路径
    private Map<String, Object> attachments;  // 附加信息
    // 可用于一致性哈希等场景
}
```
