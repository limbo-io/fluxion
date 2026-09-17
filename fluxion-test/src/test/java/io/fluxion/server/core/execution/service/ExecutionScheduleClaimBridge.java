/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/Fluxion-io).
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

package io.fluxion.server.core.execution.service;

/**
 * 测试桥：与被测类同包，以包内可见性暴露 protected 的 claim 原语供并发测试直击。
 * <p>
 * 生产中同一 PENDING 行的跨 Broker 竞争仅出现在 bucket 移交窗口
 * （旧 Broker 尚在处理、新 Broker 已接管同一 bucket），claim 的条件 UPDATE 正是该竞争的仲裁者。
 * 本桥不做任何包装/改写，只转发调用。
 *
 * @author Devil
 */
public final class ExecutionScheduleClaimBridge {

    private ExecutionScheduleClaimBridge() {
    }

    public static boolean claim(ExecutionScheduleCommandService service, String executionId,
                                String brokerId, String token, boolean misfired) {
        return service.claim(executionId, brokerId, token, misfired);
    }
}