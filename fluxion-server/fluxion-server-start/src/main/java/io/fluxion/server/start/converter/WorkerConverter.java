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

package io.fluxion.server.start.converter;

import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.start.api.worker.view.WorkerView;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
public class WorkerConverter {

    public static List<WorkerView> toView(List<Worker> workers) {
        if (CollectionUtils.isEmpty(workers)) {
            return Collections.emptyList();
        }
        return workers.stream().map(WorkerConverter::toView).collect(Collectors.toList());
    }

    public static WorkerView toView(Worker worker) {
        WorkerView workerView = new WorkerView();
        workerView.setAppId(worker.getAppId());
        workerView.setProtocol(worker.getProtocol());
        workerView.setHost(worker.getHost());
        workerView.setPort(worker.getPort());
        workerView.setExecutors(worker.getExecutors());
        workerView.setTags(worker.getTags());
        workerView.setMetric(worker.getMetric());
        workerView.setStatus(worker.getStatus());
        return workerView;
    }

}
