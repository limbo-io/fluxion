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

package io.fluxion.platform.trigger;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.fluxion.platform.flow.ValidatableConfig;
import io.fluxion.common.utils.json.JacksonTypeIdResolver;
import lombok.Data;

/**
 * 触发某个对象执行，创建Execution
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
public abstract class Trigger implements ValidatableConfig {
    /**
     * 触发方式
     * @see Type
     */
    private String type;

    public interface Type {
        String SCHEDULE = "schedule";
        String WEBHOOK = "webhook"; // event ?? todo
    }

    public interface RefType {
        String FLOW = "flow";
        String EXECUTOR = "executor";
    }

}
