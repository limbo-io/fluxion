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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Devil
 */
public class LocalWorkerRepository implements WorkerRepository {
    // appId - workerId - Worker
    private final Map<String, Map<String, Worker>> APP_WORKERS = new ConcurrentHashMap<>();

    private final Map<String, Worker> WORKERS = new ConcurrentHashMap<>();


    @Override
    public void save(Worker worker) {
        Map<String, Worker> workers = APP_WORKERS.computeIfAbsent(worker.getAppId(), s -> new ConcurrentHashMap<>());
        workers.putIfAbsent(worker.getId(), worker);
        WORKERS.putIfAbsent(worker.getId(), worker);
    }

    @Override
    public Worker get(String id) {
        return WORKERS.get(id);
    }

    @Override
    public void delete(String id) {
        Worker removed = WORKERS.remove(id);
        if (removed != null && APP_WORKERS.containsKey(removed.getAppId())) {
            APP_WORKERS.get(removed.getAppId()).remove(id);
        }
    }

    @Override
    public List<Worker> allByApp(String appId) {
        Map<String, Worker> workers = APP_WORKERS.get(appId);
        if (workers == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(workers.values());
    }

}
