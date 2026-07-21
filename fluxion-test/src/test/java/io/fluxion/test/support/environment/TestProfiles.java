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

package io.fluxion.test.support.environment;

import java.time.Duration;

/**
 * 【测试配置常量】
 * 
 * 集中管理测试相关的常量配置，便于统一调整和维护
 * 
 * @author Fluxion Test Framework
 */
public final class TestProfiles {

    private TestProfiles() {
        // 禁止实例化
    }

    // ===== 测试环境标识 =====
    
    /** Spring Profile 名称 */
    public static final String TEST_PROFILE = "test";
    
    /** 应用名称 */
    public static final String APP_NAME = "fluxion-test";

    // ===== 超时时间配置 =====
    
    /** 默认等待超时（短操作） */
    public static final Duration TIMEOUT_SHORT = Duration.ofSeconds(5);
    
    /** 默认等待超时（一般操作） */
    public static final Duration TIMEOUT_DEFAULT = Duration.ofSeconds(30);
    
    /** 默认等待超时（长操作） */
    public static final Duration TIMEOUT_LONG = Duration.ofSeconds(60);
    
    /** 轮询间隔 */
    public static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    // ===== 环境启动配置 =====
    
    /** 环境就绪最大等待时间 */
    public static final long ENV_READY_TIMEOUT_MS = 10000;
    
    /** Broker 启动后等待时间（确保调度器初始化完成） */
    public static final long BROKER_STARTUP_DELAY_MS = 3000;

    // ===== 测试数据配置 =====
    
    /** ID 前缀 - 调度 */
    public static final String ID_PREFIX_SCHEDULE = "SCH";
    
    /** ID 前缀 - 任务 */
    public static final String ID_PREFIX_JOB = "JOB";
    
    /** ID 前缀 - 执行 */
    public static final String ID_PREFIX_EXEC = "EXEC";
    
    /** ID 前缀 - Worker */
    public static final String ID_PREFIX_WORKER = "WRK";
}
