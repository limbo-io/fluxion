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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.fluxion.common.utils.json.JacksonTypeIdResolver;
import io.fluxion.server.core.execution.ExecutionRefType;
import io.fluxion.server.infrastructure.validata.ValidatableConfig;
import lombok.Data;

/**
 * 触发某个对象执行，创建Execution
 *
 * @author Devil
 */
@Data
public class Trigger {

    private String id;

    private String refId;

    private ExecutionRefType refType;

    private Config config;

    /**
     * 描述
     */
    private String description;

    private boolean enabled;

    public interface Type {
        String SCHEDULE = "schedule";
        String DELAY = "delay";
        String WEBHOOK = "webhook"; // event ?? todo @d later
    }

    /**
     * Trigger 配置态
     *
     * @author Devil
     */
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.CLASS,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true
    )
    @JsonTypeIdResolver(JacksonTypeIdResolver.class)
    @Data
    public static abstract class Config implements ValidatableConfig {
        /**
         * 触发方式
         *
         * @see Trigger.Type
         */
        private String type;

    }

}
