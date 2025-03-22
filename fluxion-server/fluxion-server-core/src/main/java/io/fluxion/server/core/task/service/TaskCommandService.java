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

package io.fluxion.server.core.task.service;

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.task.Task;
import io.fluxion.server.core.task.TaskStatus;
import io.fluxion.server.core.task.cmd.TaskDispatchedCmd;
import io.fluxion.server.core.task.cmd.TaskFailCmd;
import io.fluxion.server.core.task.cmd.TaskReportCmd;
import io.fluxion.server.core.task.cmd.TaskRunCmd;
import io.fluxion.server.core.task.cmd.TaskStartCmd;
import io.fluxion.server.core.task.cmd.TaskSuccessCmd;
import io.fluxion.server.core.task.cmd.TasksCreateCmd;
import io.fluxion.server.core.task.runner.TaskRunner;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.dao.entity.TaskEntity;
import io.fluxion.server.infrastructure.dao.repository.TaskEntityRepo;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Slf4j
@Component
public class TaskCommandService {

    @Resource
    private TaskEntityRepo taskEntityRepo;

    @Resource
    private List<TaskRunner> taskRunners;

    @Resource
    private EntityManager entityManager;

    @CommandHandler
    public void handle(TasksCreateCmd cmd) {
        List<Task> tasks = new ArrayList<>();
        for (Task task : cmd.getTasks()) {
            // 判断是否已经创建
            TaskEntity entity = taskEntityRepo.findByExecutionIdAndRefIdAndTaskType(task.getRefId(), task.getRefId(), task.type().value);
            if (entity == null) {
                tasks.add(task);
            }
        }
        if (CollectionUtils.isEmpty(tasks)) {
            return;
        }
        List<TaskEntity> entities = tasks.stream().map(task -> {
            String id = Cmd.send(new IDGenerateCmd(IDType.TASK)).getId();
            TaskEntity entity = new TaskEntity();
            entity.setTaskId(id);
            entity.setExecutionId(task.getExecutionId());
            entity.setTriggerAt(task.getTriggerAt());
            entity.setStatus(task.getStatus().value);
            entity.setTaskType(task.type().value);
            entity.setRefId(task.getRefId());
            entity.setRetryTimes(task.getRetryTimes());
            return entity;
        }).collect(Collectors.toList());
        taskEntityRepo.saveAllAndFlush(entities);
    }

    @CommandHandler
    public void handle(TaskRunCmd cmd) {
        Task task = cmd.getTask();
        TaskRunner taskRunner = taskRunners.stream().filter(r -> r.type() == task.type()).findFirst().orElse(null);
        if (taskRunner == null) {
            log.warn("[TaskRunCmd] can't find type:{}", task.type());
            return;
        }
        taskRunner.run(task);
    }

    @CommandHandler
    public boolean handle(TaskStartCmd cmd) {
        int updated = entityManager.createQuery("update TaskEntity " +
                "set status = :newStatus, startAt = :startAt " +
                "where taskId = :taskId and status = :oldStatus and workerAddress = :workerAddress"
            )
            .setParameter("newStatus", TaskStatus.RUNNING.value)
            .setParameter("startAt", TimeUtils.currentLocalDateTime())
            .setParameter("taskId", cmd.getTaskId())
            .setParameter("oldStatus", TaskStatus.QUEUED.value)
            .setParameter("workerAddress", cmd.getWorkerAddress())
            .executeUpdate();
        return updated > 0;
    }

    @CommandHandler
    public boolean handle(TaskDispatchedCmd cmd) {
        int updated = entityManager.createQuery("update TaskEntity " +
                "set status = :newStatus, workerAddress = :workerAddress " +
                "where taskId = :taskId and status = :oldStatus"
            )
            .setParameter("newStatus", TaskStatus.QUEUED.value)
            .setParameter("taskId", cmd.getTaskId())
            .setParameter("oldStatus", TaskStatus.CREATED.value)
            .setParameter("workerAddress", cmd.getWorkerAddress())
            .executeUpdate();
        return updated > 0;
    }

    @CommandHandler
    public boolean handle(TaskReportCmd cmd) {
        int updated = entityManager.createQuery("update TaskEntity " +
                "set lastReportAt = :lastReportAt " +
                "where taskId = :taskId and status = :oldStatus and lastReportAt < :lastReportAt " +
                "and workerAddress = :workerAddress"
            )
            .setParameter("lastReportAt", cmd.getReportAt())
            .setParameter("taskId", cmd.getTaskId())
            .setParameter("oldStatus", TaskStatus.RUNNING.value)
            .setParameter("workerAddress", cmd.getWorkerAddress())
            .executeUpdate();
        return updated > 0;
    }

    @CommandHandler
    public boolean handle(TaskSuccessCmd cmd) {
        TaskEntity entity = taskEntityRepo.findById(cmd.getTaskId()).orElse(null);
        if (!taskFinishCheck(cmd.getTaskId(), cmd.getWorkerAddress(), entity)) {
            return false;
        }
        int updated = entityManager.createQuery("update TaskEntity " +
                "set lastReportAt = :lastReportAt, status = :newStatus, endAt = :endAt " +
                "where taskId = :taskId and status = :oldStatus and lastReportAt < :lastReportAt " +
                "and workerAddress = :workerAddress"
            )
            .setParameter("lastReportAt", cmd.getEndAt())
            .setParameter("endAt", cmd.getEndAt())
            .setParameter("taskId", cmd.getTaskId())
            .setParameter("oldStatus", TaskStatus.RUNNING.value)
            .setParameter("newStatus", TaskStatus.SUCCEED.value)
            .setParameter("workerAddress", cmd.getWorkerAddress())
            .executeUpdate();
        if (updated <= 0) {
            log.warn("TaskSuccessCmd update fail taskId:{}", cmd.getTaskId());
            return false;
        }
        return true;
    }

    @CommandHandler
    public boolean handle(TaskFailCmd cmd) {
        TaskEntity entity = taskEntityRepo.findById(cmd.getTaskId()).orElse(null);
        if (!taskFinishCheck(cmd.getTaskId(), cmd.getWorkerAddress(), entity)) {
            return false;
        }
        int updated = entityManager.createQuery("update TaskEntity " +
                "set lastReportAt = :lastReportAt, status = :newStatus, endAt = :endAt " +
                "where taskId = :taskId and status = :oldStatus and lastReportAt < :lastReportAt " +
                "and workerAddress = :workerAddress"
            )
            .setParameter("lastReportAt", cmd.getEndAt())
            .setParameter("endAt", cmd.getEndAt())
            .setParameter("taskId", cmd.getTaskId())
            .setParameter("oldStatus", TaskStatus.RUNNING.value)
            .setParameter("newStatus", TaskStatus.FAILED.value)
            .setParameter("workerAddress", cmd.getWorkerAddress())
            .executeUpdate();
        if (updated <= 0) {
            log.warn("TaskFailCmd update fail taskId:{}", cmd.getTaskId());
            return false;
        }
        return true;
    }

    private boolean taskFinishCheck(String taskId, String workerAddress, TaskEntity entity) {
        if (entity == null) {
            log.warn("TaskFinishCheck not found task id:{}", taskId);
            return false;
        }
        if (!Objects.equals(entity.getWorkerAddress(), workerAddress)) {
            log.warn("TaskFinishCheck workerAddress not match taskId:{} workerAddress:{} requestAddress:{}",
                taskId, entity.getWorkerAddress(), workerAddress
            );
            return false;
        }
        if (TaskStatus.parse(entity.getStatus()) != TaskStatus.RUNNING) {
            log.warn("TaskFinishCheck not running taskId:{} status:{}", taskId, TaskStatus.parse(entity.getStatus()));
            return false;
        }
        return true;
    }

}
