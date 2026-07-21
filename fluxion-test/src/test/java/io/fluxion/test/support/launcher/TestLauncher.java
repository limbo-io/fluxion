/*
 * Copyright 2025-2030 Limbo Team (https://github.com/limbo-io).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxion.test.support.launcher;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 【测试启动器】
 * 
 * 作用：提供便捷的测试运行入口，支持按套件运行特定类型的测试
 * 
 * 设计目的：
 *   - 快速运行特定类型的测试（如只运行容错相关测试）
 *   - 生成 Maven 命令方便复制执行
 *   - 作为 IDE 运行的替代入口
 * 
 * 支持的测试套件：
 *   - scheduling    : 调度相关测试（调度计算、触发器）
 *   - execution     : 执行相关测试（任务执行、分发）
 *   - fault         : 容错相关测试（重试、超时、熔断）
 *   - workflow      : 工作流相关测试（DAG、依赖）
 *   - all-unit      : 所有单元测试
 *   - all-int       : 所有集成测试
 *   - all-e2e       : 所有端到端测试
 * 
 * 使用方式：
 * <pre>
 *   // 列出所有套件
 *   TestLauncher.main(new String[]{"list"});
 *   
 *   // 运行指定套件
 *   TestLauncher.runSuite("fault");
 *   
 *   // 从命令行运行
 *   // mvn exec:java -Dexec.mainClass="io.fluxion.test.support.launcher.TestLauncher" -Dexec.args="fault"
 * </pre>
 * 
 * 注意：当前版本生成 Maven 命令而非直接执行，实际执行请复制命令到终端
 * 
 * @author Devil
 */
@Slf4j
public class TestLauncher {

    /**
     * 测试套件定义
     * 
     * key: 套件名称
     * value: 测试类名通配符数组
     */
    private static final Map<String, String[]> TEST_SUITES = new HashMap<>();

    static {
        // ===== 专项测试套件 =====
        
        TEST_SUITES.put("scheduling", new String[]{
            "*ScheduleCalculatorTest",
            "*ScheduleTriggerTest",
            "*CronScheduleTest"
        });

        TEST_SUITES.put("execution", new String[]{
            "*ExecutorIntegrationTest",
            "*JobExecutionTest",
            "*WorkerDispatchTest"
        });

        TEST_SUITES.put("fault", new String[]{
            "*Retry*Test",
            "*Failover*Test",
            "*Timeout*Test",
            "*CircuitBreaker*Test",
            "*FaultTolerance*Test"
        });

        TEST_SUITES.put("workflow", new String[]{
            "*WorkflowTest",
            "*DAGExecutionTest"
        });

        // ===== 分层测试套件 =====
        
        TEST_SUITES.put("all-unit", new String[]{
            "io.fluxion.test.unit.**.*Test"
        });

        TEST_SUITES.put("all-int", new String[]{
            "io.fluxion.test.integration.**.*Test"
        });

        TEST_SUITES.put("all-e2e", new String[]{
            "io.fluxion.test.e2e.**.*Test"
        });
        
        // 执行全部测试
        TEST_SUITES.put("all", new String[]{
            "io.fluxion.test.**.*Test"
        });
    }

    /**
     * 运行指定测试类
     * 
     * 用途：快速获取运行单个测试类的 Maven 命令
     * 
     * @param testClass 测试类
     */
    public static void run(Class<?> testClass) {
        log.info("[TestLauncher] 运行测试: {}", testClass.getName());
        System.out.println("执行命令:");
        System.out.println("  mvn test -Dtest=" + testClass.getSimpleName());
    }

    /**
     * 运行测试套件
     * 
     * 查找套件定义，输出生成的 Maven 命令
     * 
     * @param suiteName 套件名称 (scheduling, execution, fault, workflow, all-unit, all-int, all-e2e, all)
     */
    public static void runSuite(String suiteName) {
        String[] patterns = TEST_SUITES.get(suiteName);
        if (patterns == null) {
           System.err.println("[TestLauncher] ❌ 未知测试套件: " + suiteName);
            System.out.println();
            listSuites();
            return;
        }

        log.info("[TestLauncher] 运行测试套件: {}", suiteName);
        
        // 构建测试模式字符串
        String patternString = String.join(",", patterns);

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║  测试套件: " + padRight(suiteName, 43) + "  ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║  包含模式:                                                   ║");
        for (String pattern : patterns) {
            System.out.println("║    * " + padRight(pattern, 51) + "  ║");
        }
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.println("║  执行命令:                                                   ║");
        System.out.println("║    mvn test -Dtest=" + padRight(patternString, 35) + "  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    /**
     * 列出所有可用的测试套件
     */
    public static void listSuites() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                     可用测试套件列表                        ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        
        System.out.println("║ 【专项测试】                                                  ║");
        System.out.println("║   scheduling  - 调度相关（Cron、FixedRate、FixedDelay）      ║");
        System.out.println("║   execution   - 执行相关（分发、Worker选择、状态流转）       ║");
        System.out.println("║   fault       - 容错相关（重试、超时、熔断）                 ║");
        System.out.println("║   workflow    - 工作流相关（DAG、节点依赖）                   ║");
        System.out.println("║                                                              ║");
        System.out.println("║ 【分层测试】                                                  ║");
        System.out.println("║   all-unit    - 所有单元测试                                  ║");
        System.out.println("║   all-int     - 所有集成测试                                  ║");
        System.out.println("║   all-e2e     - 所有端到端测试                                ║");
        System.out.println("║   all         - 全部测试                                      ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("使用方法: TestLauncher.runSuite(\"suite-name\");");
    }

    /**
     * 获取运行套件的 Maven 命令
     * 
     * @param suiteName 套件名称
     * @return Maven 命令，套件不存在返回 null
     */
    public static String getMavenCommand(String suiteName) {
        String[] patterns = TEST_SUITES.get(suiteName);
        if (patterns == null) {
            return null;
        }
        return "mvn test -Dtest=" + String.join(",", patterns);
    }

    /**
     * 主入口
     * 
     * 参数：
     *   - 无参数或 "list": 列出所有套件
     *   - 其他: 作为套件名称运行
     */
    public static void main(String[] args) {
        if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
            listSuites();
            return;
        }

        runSuite(args[0]);
    }
    
    // ===== 工具方法 =====
    
    /**
     * 字符串右填充
     */
    private static String padRight(String s, int length) {
        if (s.length() >= length) {
            return s.substring(0, length);
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < length) {
            sb.append(' ');
        }
        return sb.toString();
    }
}
