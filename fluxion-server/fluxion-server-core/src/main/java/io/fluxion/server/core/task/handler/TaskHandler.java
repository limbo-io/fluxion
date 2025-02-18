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

package io.fluxion.server.core.task.handler;

import io.fluxion.common.thread.CommonThreadPool;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.task.Task;
import io.fluxion.server.core.task.cmd.TaskRunCmd;
import io.fluxion.server.core.task.cmd.TasksCreateCmd;
import io.fluxion.server.core.task.cmd.TasksScheduleCmd;
import io.fluxion.server.core.task.runner.TaskRunner;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.dao.entity.TaskEntity;
import io.fluxion.server.infrastructure.dao.repository.TaskEntityRepo;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import io.fluxion.server.infrastructure.schedule.schedule.DelayedTaskScheduler;
import io.fluxion.server.infrastructure.schedule.task.DelayedTaskFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Slf4j
@Component
public class TaskHandler {

    @Resource
    private TaskEntityRepo taskEntityRepo;

    @Resource
    private List<TaskRunner> taskRunners;

    @CommandHandler
    public void handle(TasksCreateCmd cmd) {
        List<Task> tasks = new ArrayList<>();
        for (Task task : cmd.getTasks()) {
            // 判断是否已经创建
            TaskEntity entity = taskEntityRepo.findByExecutionIdAndRefIdAndType(task.getRefId(), task.getRefId(), task.getType().value);
            if (entity == null) {
                tasks.add(task);
            }
        }
        if (CollectionUtils.isEmpty(tasks)) {
            return;
        }
        LocalDateTime now = TimeUtils.currentLocalDateTime();
        List<TaskEntity> entities = tasks.stream().map(task -> {
            String id = Cmd.send(new IDGenerateCmd(IDType.TASK)).getId();
            TaskEntity entity = new TaskEntity();
            entity.setTaskId(id);
            entity.setExecutionId(task.getExecutionId());
            entity.setTriggerAt(now);
            entity.setType(task.getType().value);
            return entity;
        }).collect(Collectors.toList());
        taskEntityRepo.saveAllAndFlush(entities);
    }

    @CommandHandler
    public void handle(TasksScheduleCmd cmd) {
        for (Task task : cmd.getTasks()) {
            DelayedTaskScheduler delayedTaskScheduler = BrokerContext.broker().delayedTaskScheduler();
            delayedTaskScheduler.schedule(DelayedTaskFactory.create(
                scheduleId(task),
                cmd.getTriggerAt(),
                delayedTask -> CommonThreadPool.IO.submit(() -> Cmd.send(new TaskRunCmd(task)))
            ));
        }
    }

    private String scheduleId(Task task) {
        return "t_" + task.getTaskId();
    }

    @CommandHandler
    public void handle(TaskRunCmd cmd) {
        Task task = cmd.getTask();
        TaskRunner taskRunner = taskRunners.stream().filter(r -> r.type() == task.getType()).findFirst().orElse(null);
        if (taskRunner == null) {
            log.warn("[TaskRunCmd] can't find type:{}", task.getType());
            return;
        }
        taskRunner.run(task);
    }

}
