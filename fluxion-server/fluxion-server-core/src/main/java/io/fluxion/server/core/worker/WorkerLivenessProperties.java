/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/fluxion-io).
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

package io.fluxion.server.core.worker;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Worker 存活检查配置。
 *
 * @author Devil
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fluxion.worker.liveness")
public class WorkerLivenessProperties {

    /** Worker 下线检查周期，单位：秒。 */
    private int checkIntervalSeconds = 3;

    /** Broker 未收到心跳多久后将 Worker 标记为下线，单位：秒。 */
    private int offlineTimeoutSeconds = 6;

    @PostConstruct
    public void validate() {
        if (checkIntervalSeconds <= 0 || offlineTimeoutSeconds <= 0) {
            throw new IllegalStateException("Worker liveness intervals must be positive");
        }
        if (checkIntervalSeconds > offlineTimeoutSeconds) {
            throw new IllegalStateException("Worker liveness check interval must not exceed offline timeout");
        }
    }
}
