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

package io.fluxion.core.executor;

import io.fluxion.core.executor.data.DispatchOption;
import io.fluxion.core.output.Output;
import io.fluxion.core.task.Task;
import io.fluxion.core.worker.Worker;
import io.fluxion.core.worker.WorkerRegistry;
import io.fluxion.core.worker.dispatch.WorkerFilter;
import io.fluxion.core.worker.selector.WorkerSelectInvocation;
import io.fluxion.core.worker.selector.WorkerSelector;
import io.fluxion.core.worker.selector.WorkerSelectorFactory;
import io.fluxion.core.worker.selector.WorkerStatisticsRepository;
import io.fluxion.remote.lb.LBServer;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
public class RemoteExecutorProxy implements LBServer {

    private WorkerSelectorFactory workerSelectorFactory;

    private WorkerStatisticsRepository workerStatisticsRepository;

    private WorkerRegistry workerRegistry;

    public Output execute(Task task) {
        Map<String, Object> attributes = null;
        List<Worker> workers = workerRegistry.all().stream()
                .filter(Worker::isEnabled)
                .collect(Collectors.toList());
        DispatchOption dispatchOption = null;
        // 广播的应该不关心资源大小 在配置的时候直接处理 todo 过滤worker任务队列已满的
        WorkerFilter workerFilter = new WorkerFilter(workers)
//                .filterExecutor(getName()) // todo 本来是根据执行器名称的，需要根据类型处理
                .filterTags(dispatchOption.getTagFilters())
                .filterResources(dispatchOption.getCpuRequirement(), dispatchOption.getRamRequirement());

        WorkerSelectInvocation invocation = null; // new WorkerSelectInvocation(getName(), attributes); // todo 本来是根据执行器名称的，需要根据类型处理
        WorkerSelector workerSelector = workerSelectorFactory.newSelector(dispatchOption.getLoadBalanceType());
        Worker select = workerSelector.select(invocation, workerFilter.get());
        if (select != null) {
            workerStatisticsRepository.record(select);
        }

        // 远程调用处理任务 todo

        return null;
    }

    @Override
    public String serverId() {
        return "";
    }

    @Override
    public boolean isAlive() {
        return false;
    }

    @Override
    public URL url() {
        return null;
    }
}
