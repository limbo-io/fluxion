# Cmd 用途注释 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为缺少类级用途说明的 server-core 命令类补充准确的中文 Javadoc，提升 CQRS 命令的可读性。

**Architecture:** 仅在类声明前加入一行中文 Javadoc，不调整命令字段、构造器、注解或服务处理逻辑。按 App、Broker、Execution、Job、Schedule、Trigger、Worker、Workflow 和基础设施命令目录统一覆盖。

**Tech Stack:** Java、Lombok、Maven。

---

### Task 1: 补充 Cmd 类级用途说明

**Files:**
- Modify: `fluxion-server/fluxion-server-core/src/main/java/io/fluxion/server/{core,infrastructure}/**/cmd/*Cmd.java`

- [ ] **Step 1: 为缺少用途说明的 Cmd 添加中文 Javadoc**

在现有 import 与第一个 Lombok/Spring 注解之间插入用途注释；例如：

```java
/** 创建执行实例并返回执行标识。 */
@Getter
@AllArgsConstructor
public class ExecutionCreateCmd implements ICommand<ExecutionCreateCmd.Response> {
```

仅添加与类名和字段语义一致的说明，不改变已有的英文用途 Javadoc，也不改动其他代码。

- [ ] **Step 2: 编译 server 模块**

Run: `mvn -pl fluxion-server/fluxion-server-core -am -DskipTests compile`

Expected: `BUILD SUCCESS`。

- [ ] **Step 3: 复核变更范围**

Run: `git diff --check && git diff -- fluxion-server/fluxion-server-core/src/main/java/**/cmd`

Expected: 无空白错误；diff 仅包含 Cmd 类级中文注释。
