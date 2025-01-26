/*
 * Copyright 2024-2030 fluxion-io Team (https://github.com/fluxion-io).
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

package io.fluxion.server.core.task.runner;

import io.fluxion.server.core.executor.option.DispatchOption;
import io.fluxion.server.core.task.Task;
import io.fluxion.server.core.task.TaskType;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.WorkerRegistry;
import io.fluxion.server.core.worker.dispatch.WorkerFilter;
import io.fluxion.server.core.worker.selector.WorkerSelectInvocation;
import io.fluxion.server.core.worker.selector.WorkerSelector;
import io.fluxion.server.core.worker.selector.WorkerSelectorFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Component
public class ExecutorTaskRunner extends TaskRunner {

    @Resource
    private WorkerSelectorFactory workerSelectorFactory;

    @Resource
    private WorkerRegistry workerRegistry;

    @Override
    public TaskType type() {
        return TaskType.EXECUTOR;
    }

    @Override
    public void run(Task task) {
        Map<String, Object> attributes = null;
        List<Worker> workers = workerRegistry.all().stream()
            .filter(Worker::isEnabled)
            .collect(Collectors.toList());
        DispatchOption dispatchOption = null;
        // 广播的应该不关心资源大小 在配置的时候直接处理 todo @pq 过滤worker任务队列已满的
        WorkerFilter workerFilter = new WorkerFilter(workers)
//                .filterExecutor(getName()) // todo @pq 本来是根据执行器名称的，需要根据类型处理
            .filterTags(dispatchOption.getTagFilters())
            .filterResources(dispatchOption.getCpuRequirement(), dispatchOption.getRamRequirement());

        WorkerSelectInvocation invocation = null; // new WorkerSelectInvocation(getName(), attributes); // todo @pq 本来是根据执行器名称的，需要根据类型处理
        WorkerSelector workerSelector = workerSelectorFactory.newSelector(dispatchOption.getLoadBalanceType());
        Worker worker = workerSelector.select(invocation, workerFilter.get());

        // 远程调用处理任务
        worker.dispatch(task);
    }


}
