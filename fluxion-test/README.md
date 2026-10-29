# Fluxion Test Module

测试分类、完整清单、运行前提和新增测试规范以 [测试指南](../docs/guides/TESTING_GUIDE.md) 为准。

## 固定目录

```text
src/test/java/
├── io/fluxion/test/
│   ├── unit/<业务模块>/          # 不启动 Spring、真实数据库或节点进程
│   ├── integration/
│   │   ├── database/             # Spring/数据库/事务/并发语义
│   │   └── cluster/              # SCH-001、WRK-001 等真实集群场景
│   │       ├── support/          # 集群生命周期、报告、事件等工具
│   │       └── node/             # 测试 Worker 启动器与执行器
│   └── support/                 # 共用测试装配
└── io/fluxion/server/core/execution/service/
    └── ExecutionScheduleClaimBridge.java  # 必须同生产类包名的测试访问桥
```

新增测试按实际依赖归类；目录与 Java package 一致。协议测试按是否启动服务放入上述类别；冒烟、回归、故障恢复属于运行集合或场景属性，不另建顶层目录，不复制用例。

## 执行入口

从仓库根目录运行，要求 JDK 21。一次只选择一个 profile。

```bash
# 默认与 regression-test 都只运行单元测试
mvn test -pl fluxion-test -am
mvn test -pl fluxion-test -am -Pregression-test

# H2 数据库集成测试，无需外部数据库
mvn test -pl fluxion-test -am -Ph2-integration-test

# MySQL 专属数据库语义测试，需要可用 MySQL
mvn test -pl fluxion-test -am -Pmysql-integration-test

# 真实集群：Podman MySQL + 2 Broker + 2 Worker
mvn test -pl fluxion-test -am -Pcluster-e2e

# 单独选择 Worker 强退恢复场景
mvn test -pl fluxion-test -am -Pcluster-e2e -Dtest=Wrk001Test -Dsurefire.failIfNoSpecifiedTests=false
```

迁移目录后先运行 `mvn clean -pl fluxion-test`，避免旧 package 的编译产物残留。原 profile 名称继续保留，现有 CI 命令不变。

集群测试当前实现 SCH-001 和 WRK-001 的首个本地恢复闭环；不代表 Broker 接管、迟到结果隔离或完整故障矩阵已经验证。场景定义见 [用例目录](../docs/guides/CLUSTER_E2E_CASES.md)，产物位于 `target/cluster-e2e/<run-id>/`。
