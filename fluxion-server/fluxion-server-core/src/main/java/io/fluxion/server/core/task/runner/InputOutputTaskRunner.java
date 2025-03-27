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
import io.fluxion.server.core.execution.cmd.ExecutableFailCmd;
import io.fluxion.server.core.execution.cmd.ExecutableSuccessCmd;
import io.fluxion.server.core.task.InputOutputTask;
import io.fluxion.server.core.task.Task;
import io.fluxion.server.core.task.TaskType;
import io.fluxion.server.core.task.cmd.TaskStartCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author Devil
 */
@Slf4j
@Component
public class InputOutputTaskRunner extends TaskRunner {

    @Override
    public TaskType type() {
        return TaskType.INPUT_OUTPUT;
    }

    @Override
    public void run(Task task) {
        InputOutputTask inputOutputTask = (InputOutputTask) task;
        Boolean started = Cmd.send(new TaskStartCmd(inputOutputTask.getTaskId(), TimeUtils.currentLocalDateTime()));
        if (!started) {
            return;
        }
        try {
            if (log.isDebugEnabled()) {
                log.debug("InputOutputTaskRunner taskId:{}", task.getTaskId());
            }
            Cmd.send(new ExecutableSuccessCmd(
                task.getTaskId(),
                TimeUtils.currentLocalDateTime()
            ));
        } catch (Exception e) {
            log.error("InputOutputTaskRunner error", e);
            Cmd.send(new ExecutableFailCmd(
                task.getTaskId(),
                TimeUtils.currentLocalDateTime(),
                e.getMessage()
            ));
        }
    }
}
