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

package io.fluxion.server.start.api.trigger.view;

import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.execution.ExecutionRefType;
import lombok.Data;


/**
 * @author Devil
 */
@Data
public class TriggerView {

    private String id;

    /**
     * 触发方式
     * @see Trigger.Type
     */
    private String type;

    private String refId;

    /**
     * @see ExecutionRefType
     */
    private String refType;

    /**
     * 描述
     */
    private String description;

    /**
     * 配置信息
     */
    private Trigger config;

    /**
     * 是否启动
     */
    private boolean enabled;
}
