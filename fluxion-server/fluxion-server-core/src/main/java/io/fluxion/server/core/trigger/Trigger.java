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

package io.fluxion.server.core.trigger;

import io.fluxion.server.core.execution.ExecuteConfig;
import io.fluxion.server.core.execution.config.ExecutorExecuteConfig;
import io.fluxion.server.core.execution.config.WorkflowExecuteConfig;
import lombok.Data;

/**
 * 触发某个对象执行，创建Execution
 *
 * @author Devil
 */
@Data
public class Trigger {

    private String id;

    private String version;

    private String name;

    private String description;

    private TriggerConfig config;

    private boolean enabled;

    private boolean published;

    public String executableId() {
        ExecuteConfig executeConfig = config.getExecuteConfig();
        if (executeConfig == null) {
            return null;
        } else if (executeConfig instanceof WorkflowExecuteConfig) {
            WorkflowExecuteConfig workflowExecuteConfig = (WorkflowExecuteConfig) executeConfig;
            return workflowExecuteConfig.getWorkflowId();
        } else if (executeConfig instanceof ExecutorExecuteConfig) {
            return id;
        } else {
            return null;
        }
    }

}
