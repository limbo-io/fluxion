/*
 * Copyright 2025-2030 limbo-io Team (https://github.com/limbo-io).
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

import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import io.fluxion.server.start.api.execution.view.ExecutionView;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
public class ExecutionConverter {

    public static ExecutionView toView(ExecutionEntity entity) {
        if (entity == null) {
            return null;
        }
        ExecutionView executionView = new ExecutionView();
        executionView.setExecutionId(entity.getExecutionId());
        executionView.setTriggerId(entity.getTriggerId());
        executionView.setTriggerType(entity.getTriggerType());
        executionView.setExecutableType(entity.getExecutableType());
        executionView.setExecutableId(entity.getExecutableId());
        executionView.setExecutableVersion(entity.getExecutableVersion());
        executionView.setStatus(entity.getStatus());
        executionView.setTriggerAt(entity.getTriggerAt());
        executionView.setStartAt(entity.getStartAt());
        executionView.setEndAt(entity.getEndAt());
        return executionView;
    }

    public static List<ExecutionView> toView(List<ExecutionEntity> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }
        return entities.stream().map(ExecutionConverter::toView).collect(Collectors.toList());
    }
}
