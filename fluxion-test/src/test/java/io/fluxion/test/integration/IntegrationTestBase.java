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

package io.fluxion.test.integration;

import io.fluxion.test.support.base.BaseIntegrationTest;
import lombok.extern.slf4j.Slf4j;

/**
 * 【集成测试基类 - 可靠性测试扩展】
 * 
 * 作用：为可靠性回归测试提供常用工具方法
 * 
 * 扩展能力：
 *   - Worker 模拟操作
 *   - Broker 故障模拟
 *   - 任务抢占验证
 *   - 执行状态检查
 * 
 * @author Fluxion Test Framework
 */
@Slf4j
public abstract class IntegrationTestBase extends BaseIntegrationTest {

    // 基础能力继承自 BaseIntegrationTest
    // 嵌入式环境操作通过 embeddedEnvironment 字段

}
