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

package io.fluxion.server.start.api.trigger.request;

import io.fluxion.server.core.execution.ExecuteConfig;
import io.fluxion.server.core.execution.ExecutableType;
import io.fluxion.server.core.trigger.TriggerConfig;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @author Devil
 */
@Data
public class TriggerUpdateRequest {

    private String id;

    @NotBlank
    private String name;

    private TriggerConfig triggerConfig;

    private ExecuteConfig executeConfig;

    private String description;

}
