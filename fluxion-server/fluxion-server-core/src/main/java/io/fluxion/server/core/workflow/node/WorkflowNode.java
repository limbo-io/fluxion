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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.fluxion.common.utils.json.JacksonTypeIdResolver;
import io.fluxion.server.core.executor.option.OvertimeOption;
import io.fluxion.server.core.executor.option.RetryOption;
import io.fluxion.server.infrastructure.dag.DAGNode;
import io.fluxion.server.infrastructure.validata.ValidatableConfig;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
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
@NoArgsConstructor
public abstract class WorkflowNode implements DAGNode, ValidatableConfig {

    /**
     * 类型
     *
     * @see Type
     */
    private String type;

    /**
     * 唯一标识
     */
    private String id;
    /**
     * 名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 重试参数
     */
    private RetryOption retryOption = new RetryOption();

    /**
     * 超时参数
     */
    private OvertimeOption overtimeOption;

    /**
     * 执行失败是否继续
     * true  会继续执行后续作业
     * false 终止环节
     */
    private boolean skipWhenFail = false;

    /**
     * 扩展信息 目前只给前端使用
     */
    private Map<String, Object> extension = new HashMap<>();

    @Override
    public String id() {
        return id;
    }

    public interface Type {
        String START = "start";
        String EXECUTOR = "executor";
        String END = "end";
    }

}
