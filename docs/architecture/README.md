# Fluxion 架构文档

本文档目录包含 Fluxion 分布式任务调度平台的完整架构设计说明。

## 文档导航

| 文档 | 说明 |
|------|------|
| [overview.md](./overview.md) | 系统整体架构概述 |
| [modules.md](./modules.md) | 模块结构与依赖关系 |
| [remote.md](./remote.md) | 远程通信层设计 |
| [load-balance.md](./load-balance.md) | 负载均衡策略详解 |
| [executor.md](./executor.md) | 执行器模式设计 |
| [job-types.md](./job-types.md) | 任务类型说明 |
| [scheduling.md](./scheduling.md) | 任务调度流程 |
| [data-model.md](./data-model.md) | 数据模型设计 |

## 快速开始

如果你是第一次接触 Fluxion，建议按以下顺序阅读：

1. [overview.md](./overview.md) - 了解系统整体架构
2. [modules.md](./modules.md) - 了解项目模块划分
3. [executor.md](./executor.md) - 了解如何开发执行器
4. [job-types.md](./job-types.md) - 了解任务类型选择
5. [data-model.md](./data-model.md) - 了解数据存储设计

## 适用读者

- **架构师**：查看整体设计思路
- **后端开发**：了解模块设计和开发规范
- **运维人员**：了解部署结构和数据流
- **新成员**：快速熟悉系统全貌

## 架构概览

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Console   │────▶│   Broker    │────▶│   Worker    │
│  (Vue Web)  │     │ (调度服务器) │     │ (执行节点)   │
└─────────────┘     └─────────────┘     └─────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │   MySQL     │
                    │ (持久化存储) │
                    └─────────────┘
```

## 核心特性

- **分布式调度**：支持多 Broker、多 Worker 集群部署
- **负载均衡**：6 种内置负载均衡策略
- **智能路由**：基于执行器、标签、容量的节点过滤
- **多样任务**：普通、广播、MapReduce 三种任务类型
- **灵活调度**：FixedRate、FixedDelay、CRON 调度类型
- **高可用**：Worker 故障自动切换，任务重试机制
