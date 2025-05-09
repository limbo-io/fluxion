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

package io.fluxion.server.core.workflow;

/**
 * @author Devil
 */
public interface WorkflowConstants {

    int FLOW_NODE_MAX_SIZE = 100;

    // ========== 校验code ==========
    String FLOW_NODE_EMPTY = "flow_node_empty";

    String FLOW_NODE_OVER_LIMIT = "flow_node_over_limit";

    String FLOW_START_NODES_IS_EMPTY = "flow_start_nodes_is_empty";

    String FLOW_START_NODES_HAS_ERROR_TYPE_NODE = "flow_start_nodes_has_error_type_node";

    String FLOW_END_NODES_IS_EMPTY = "flow_end_nodes_is_empty";

    String FLOW_END_NODES_HAS_ERROR_TYPE_NODE = "flow_end_nodes_has_error_type_node";

    String FLOW_HAS_CYCLIC = "flow_has_cyclic";

    String FLOW_NODE_START_END_LIMIT = "flow_node_start_end_limit";

    String TRIGGER_IS_EMPTY = "trigger_is_empty";

    String SCHEDULE_CONFIG_ERROR = "schedule_config_error";

    String EXECUTOR_IS_EMPTY = "executor_is_empty";

    String EXECUTOR_NAME_IS_EMPTY = "executor_name_is_empty";

    String SCRIPT_IS_EMPTY = "script_is_empty";
}
