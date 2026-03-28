# 模块结构

Fluxion 采用 Maven 多模块架构，各模块职责清晰、依赖关系明确。

## 模块总览

```
fluxion/
├── fluxion-common/              # 公共工具类
├── fluxion-remote/              # RPC 通信层
│   ├── fluxion-remote-core/     # 核心抽象
│   ├── fluxion-remote-netty/    # Netty 实现
│   └── fluxion-remote-vertx/    # Vert.x 实现
├── fluxion-server/              # Broker 模块
│   ├── fluxion-server-core/     # 核心服务逻辑
│   └── fluxion-server-start/    # Spring Boot 应用入口
├── fluxion-worker/              # Worker 模块
│   ├── fluxion-worker-core/     # 核心执行逻辑
│   ├── fluxion-worker-spring-boot-starter/  # 自动配置
│   └── fluxion-worker-demo/     # 示例代码
└── fluxion-test/                # 集成测试
```

## 详细说明

### fluxion-common

**职责：** 提供公共工具类和通用常量

**主要包结构：**
```
io.fluxion.common
├── constants/       # 全局常量定义
├── thread/          # 线程池相关工具
└── utils/           # 通用工具类
```

### fluxion-remote

通信层的模块化设计，支持多种底层通信框架。

#### fluxion-remote-core

**职责：** 定义通信层核心抽象，所有通信实现的基础

**核心接口：**
- `Client`: RPC 客户端抽象，定义请求发送协议
- `ClientServer`: 服务端抽象，管理服务生命周期
- `LBStrategy`: 负载均衡策略接口
- `LBServer`: 负载服务器抽象

**主要包结构：**
```
io.fluxion.remote.core
├── api/             # API 请求/响应定义
│   ├── dto/         # 数据传输对象
│   ├── request/     # 请求对象
│   └── response/    # 响应对象
├── client/          # 客户端抽象
│   └── server/      # 服务端抽象
├── cluster/         # 集群节点抽象
├── lb/              # 负载均衡核心
│   ├── strategies/  # 负载均衡策略实现
│   └── repository/  # 服务存储
└── heartbeat/       # 心跳机制
```

#### fluxion-remote-netty

**职责：** 基于 Netty 的通信实现

**核心类：**
- `NettyHttpClientServer`: Netty HTTP 服务端实现
- `NettyClientServerFactory`: Netty 服务端工厂

#### fluxion-remote-vertx

**职责：** 基于 Vert.x 的通信实现（可选）

### fluxion-server

Broker 服务端模块，负责任务调度和管理。

#### fluxion-server-core

**职责：** Broker 核心业务能力实现

**主要包结构：**
```
io.fluxion.server
├── core/                    # 核心业务逻辑
│   ├── execution/          # 执行管理
│   ├── executor/           # 执行器配置
│   ├── job/                # 作业管理
│   ├── schedule/           # 调度管理
│   ├── trigger/            # 触发器管理
│   ├── worker/             # Worker 管理
│   │   ├── dispatch/       # 分发逻辑
│   │   ├── executor/       # Worker 执行器
│   │   ├── metric/         # 指标监控
│   │   └── selector/       # Worker 选择策略
│   └── workflow/           # 工作流管理
│       └── node/           # 工作流节点
├── infrastructure/         # 基础设施层
│   ├── concurrent/         # 并发工具
│   ├── dag/                # DAG 处理
│   ├── dao/                # 数据访问
│   │   ├── entity/         # 实体类
│   │   └── repository/     # Repository 接口
│   ├── id/                 # ID 生成
│   ├── lock/               # 分布式锁
│   ├── schedule/           # 调度计算
│   └── tag/                # 标签管理
├── modules/                # 功能模块
│   ├── broker/             # Broker 管理
│   ├── job/                # 作业处理
│   ├── trigger/            # 触发处理
│   └── worker/             # Worker 处理
└── Interfaces/             # 接口层
    └── controller/         # REST API
```

#### fluxion-server-start

**职责：** Spring Boot 应用启动入口

**主要资源：**
- `db/migration/`: Flyway 数据库迁移脚本
- `application.yml`: 应用配置

### fluxion-worker

Worker 客户端模块，负责任务执行。

#### fluxion-worker-core

**职责：** Worker 核心执行逻辑

**主要包结构：**
```
io.fluxion.worker.core
├── context/          # 上下文管理
├── discovery/        # 服务发现
├── executor/         # 执行器接口
│   └── MapReduceExecutor.java
├── job/              # 作业管理
│   └── tracker/      # 作业跟踪器
├── persistence/      # 本地持久化（H2）
├── remote/           # 远程调用
├── task/             # 任务管理
│   ├── context/      # 任务上下文
│   ├── repository/   # 任务存储
│   └── tracker/      # 任务跟踪
└── WorkerContext.java # Worker 上下文
```

**核心接口：**
- `Executor`: 执行器接口，业务逻辑实现此接口
- `JobTracker`: 作业执行跟踪器
- `TaskRepository`: 任务存储接口

#### fluxion-worker-spring-boot-starter

**职责：** Spring Boot 自动配置，简化 Worker 集成

**核心类：**
- `FluxionWorkerAutoConfiguration`: 自动配置入口
- `SpringDelegatedWorker`: Spring 委托 Worker 实现
- `ExecutorMethodProcessor`: 执行器扫描处理器

**配置属性：**
```java
fluxion.worker.appName      // 应用名
fluxion.worker.protocol     // 通信协议
fluxion.worker.host         // Worker 地址
fluxion.worker.port         // Worker 端口
fluxion.worker.brokers      // Broker 地址列表
fluxion.worker.heartbeat    // 心跳间隔
fluxion.worker.tags         // 节点标签
```

#### fluxion-worker-demo

**职责：** Worker 使用示例

**示例执行器：**
- `HelloExecutor`: 简单执行器示例
- `MapReduceDemoExecutor`: MapReduce 执行器示例

### fluxion-test

**职责：** 集成测试模块

**特点：**
- 使用嵌入式环境进行测试
- 无需外部依赖（MySQL、独立 Worker）
- 自动测试数据清理

## 模块依赖关系

```
fluxion-server-start
    ↓
fluxion-server-core → fluxion-remote-netty
    ↓                       ↓
fluxion-remote-core ← fluxion-common

fluxion-worker-spring-boot-starter → fluxion-worker-core
                                            ↓
                                    fluxion-remote-core
                                            ↓
                                    fluxion-common

fluxion-test → fluxion-server-core
           → fluxion-worker-core
```
