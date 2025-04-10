/*
 * Copyright 2025-2030 Limbo Team (https://github.com/limbo-io).
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

package io.fluxion.test.core.flow;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.type.TypeReference;
import io.fluxion.common.utils.Lambda;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.core.executor.config.ExecutorConfig;
import io.fluxion.server.core.workflow.Workflow;
import io.fluxion.server.core.workflow.WorkflowConfig;
import io.fluxion.server.core.workflow.node.WorkflowNode;
import io.fluxion.server.infrastructure.dao.entity.WorkflowEntity;
import org.junit.jupiter.api.Test;
import org.reflections.Reflections;

/**
 * @author Devil
 */
class WorkflowTest {

    @Test
    void testJson() {
        String json = "{\"nodes\":[{\"id\":\"cb18777d-b31a-4b92-a56a-fcbfe8e1bce8_node\",\"type\":\"trigger\",\"name\":\"触发器\",\"subNodeIds\":[],\"extension\":{\"position\":{\"x\":-145.33333333333331,\"y\":55.66666666666667}},\"triggers\":[{\"name\":\"v\",\"type\":\"schedule\",\"scheduleOption\":{\"scheduleType\":\"fixed_rate\"}}]},{\"id\":\"17ec77b4-27f0-4f66-9202-fbd88353452c_node\",\"type\":\"executor\",\"name\":\"执行器\",\"subNodeIds\":[\"cb18777d-b31a-4b92-a56a-fcbfe8e1bce8_node\"],\"extension\":{\"position\":{\"x\":364.66666666666674,\"y\":105.04166666666669}},\"executor\":{\"type\":\"custom_executor\",\"name\":\"v\"}}]}";
        JacksonUtils.toType(json, WorkflowConfig.class);
        System.out.println(11);
    }

    @Test
    void testFlowConfig() {
        String json = "{\"name\":\"f-n\",\"nodes\":[{\"type\":\"executor\",\"name\":\"n-1\",\"executor\":\"exxxx\", \"extension\":{\"position\":{\"x\":50,\"y\":50}}}]}";
        String arrJson = "[{\"name\":\"f-n\",\"nodes\":[{\"type\":\"executor\",\"name\":\"n-1\",\"executor\":\"exxxx\", \"extension\":{\"position\":{\"x\":50,\"y\":50}}}]}]";
        Workflow workflow = JacksonUtils.toType(json, new TypeReference<Workflow>() {});
//        System.out.println(JacksonUtils.toJSONString(flow));
//        flow = JacksonUtils.toType(json, Flow.class);
//        System.out.println(JacksonUtils.toJSONString(flow));
//        List<Flow> flows = JacksonUtils.toType(arrJson, new TypeReference<List<Flow>>() {});
//        System.out.println(JacksonUtils.toJSONString(flows));

        json = "{\n" +
            "  \"nodes\" : [ {\n" +
            "    \"extension\" : {\n" +
            "      \"position\" : {\n" +
            "        \"x\" : 50,\n" +
            "        \"y\" : 50\n" +
            "      }\n" +
            "    }\n" +
            "  } ]\n" +
            "}";
        workflow = JacksonUtils.toType(json, Workflow.class);
        System.out.println(JacksonUtils.toJSONString(workflow));
    }

    @Test
    void testRef() {
        Reflections reflections = new Reflections();
        System.out.println(reflections.getSubTypesOf(WorkflowNode.class).size());
        System.out.println(reflections.getSubTypesOf(ExecutorConfig.class).size());
        System.out.println(reflections.getTypesAnnotatedWith(JsonTypeName.class).size());
        System.out.println(Lambda.name(WorkflowEntity::getDraftVersion));
        System.out.println(Lambda.name(WorkflowEntity::getDraftVersion));
    }
}
