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

package io.fluxion.server.core.task.runner;

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.remote.core.api.request.worker.TaskDispatchRequest;
import io.fluxion.remote.core.constants.WorkerRemoteConstant;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.execution.cmd.ExecutableFailCmd;
import io.fluxion.server.core.executor.option.DispatchOption;
import io.fluxion.server.core.task.ExecutorTask;
import io.fluxion.server.core.task.Task;
import io.fluxion.server.core.task.TaskType;
import io.fluxion.server.core.task.cmd.TaskDispatchedCmd;
import io.fluxion.server.core.worker.Worker;
import io.fluxion.server.core.worker.WorkerRepository;
import io.fluxion.server.core.worker.dispatch.WorkerFilter;
import io.fluxion.server.core.worker.selector.WorkerSelectInvocation;
import io.fluxion.server.core.worker.selector.WorkerSelector;
import io.fluxion.server.core.worker.selector.WorkerSelectorFactory;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Component
public class ExecutorTaskRunner extends TaskRunner {

    private static final WorkerSelectorFactory workerSelectorFactory = new WorkerSelectorFactory();

    @Resource
    private WorkerRepository workerRepository;

    @Override
    public TaskType type() {
        return TaskType.EXECUTOR;
    }

    @Override
    public void run(Task task) {
        ExecutorTask executorTask = (ExecutorTask) task;
        Map<String, Object> attributes = new HashMap<>();
        List<Worker> workers = workerRepository.allByApp(executorTask.getAppId()).stream()
            .filter(Worker::isEnabled)
            .collect(Collectors.toList());
        DispatchOption dispatchOption = executorTask.getDispatchOption();
        // 广播的应该不关心资源大小 在配置的时候直接处理
        WorkerFilter workerFilter = new WorkerFilter(workers)
            .filterExecutor(executorTask.getExecutorName())
            .filterTags(dispatchOption.getTagFilters())
            .filterResources(dispatchOption.getCpuRequirement(), dispatchOption.getRamRequirement());

        WorkerSelectInvocation invocation = new WorkerSelectInvocation(executorTask.getExecutorName(), attributes);
        WorkerSelector workerSelector = workerSelectorFactory.newSelector(dispatchOption.getLoadBalanceType());
        Worker worker = workerSelector.select(invocation, workerFilter.get());
        boolean dispatched = false;
        if (worker != null) {
            // 远程调用处理任务
            TaskDispatchRequest request = new TaskDispatchRequest();
            request.setTaskId(task.getTaskId());
            request.setBrokerAddress(BrokerContext.broker().id());
            request.setExecutorName(executorTask.getExecutorName());
            request.setExecuteMode(executorTask.getExecuteMode().mode);
            // call
            dispatched = BrokerContext.call(
                WorkerRemoteConstant.API_TASK_DISPATCH, worker.getHost(), worker.getPort(), request
            );
        }

        String workerAddress = worker == null ? null : worker.getAddress();
        if (dispatched) {
            Cmd.send(new TaskDispatchedCmd(
                task.getTaskId(),
                workerAddress
            ));
        } else {
            Cmd.send(new ExecutableFailCmd(
                task.getTaskId(),
                workerAddress,
                TimeUtils.currentLocalDateTime(),
                "dispatch fail worker:" + workerAddress
            ));
        }

    }


}
