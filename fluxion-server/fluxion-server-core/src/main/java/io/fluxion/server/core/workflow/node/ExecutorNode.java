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

package io.fluxion.server.core.workflow.node;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.fluxion.server.core.executor.config.ExecutorConfig;
import io.fluxion.server.core.workflow.WorkflowConstants;
import io.fluxion.server.infrastructure.validata.ValidateSuppressInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 执行器节点
 *
 * @author Devil
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName(WorkflowNode.Type.EXECUTOR)
public class ExecutorNode extends WorkflowNode {

    private ExecutorConfig executor;

    @Override
    public List<ValidateSuppressInfo> validate() {
        if (executor == null) {
            return Collections.singletonList(new ValidateSuppressInfo(WorkflowConstants.EXECUTOR_IS_EMPTY));
        }
        return new ArrayList<>(executor.validate());
    }
}
