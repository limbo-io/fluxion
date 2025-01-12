/*
 * Copyright 2025-2030 Fluxion Team (https://github.com/Fluxion-io).
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

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Devil
 * @since 2023/11/21
 */
@Slf4j
public class WorkerRegistry {

    private static final Map<String, Worker> RUNNING_WORKER_MAP = new ConcurrentHashMap<>();

    /**
     * 心跳超时时间，毫秒
     */
    private final Duration heartbeatTimeout = Duration.ofSeconds(3);

    private final WorkerRepository workerRepository;

    public WorkerRegistry(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }

    public Collection<Worker> all() {
        return RUNNING_WORKER_MAP.values();
    }

}
