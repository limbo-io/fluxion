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

package io.fluxion.server.core.execution;

import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.flow.Flow;
import io.fluxion.server.core.flow.FlowNode;
import io.fluxion.server.infrastructure.schedule.scheduler.DelayTaskScheduler;
import io.fluxion.server.infrastructure.schedule.task.DelayTask;

import java.util.List;
import java.util.function.Consumer;

/**
 * Flow的一次执行 运行态
 *
 * @author Devil
 */
public class FlowExecution extends Execution {

    private Flow flow;

    /**
     * 执行某个节点
     * @param nodeId
     */
    public void execute(String nodeId) {
        // 判断前置节点执行状态，是否能执行当前节点
        List<FlowNode> parentNodes = flow.findPreNodes(nodeId);


        FlowNode currentNode = flow.findNode(nodeId);
//        Output output = currentNode.run(null);

        // 保存结果和output

        // 基于execution创建 task
        List<FlowNode> subNodes = flow.findSubNodes(nodeId);
        for (FlowNode subNode : subNodes) {
            execute(subNode.getId());
        }
    }

    @Override
    public void execute() {
        for (FlowNode origin : flow.getDag().origins()) {
            // todo 现在 start 节点没有逻辑  记录一下，触发后续节点
        }

        // todo 创建task 调用 executor 进行任务执行
        DelayTaskScheduler s = null;
//        s.schedule(new DelayTask("", TimeUtils.currentLocalDateTime(), new Consumer<DelayTask>() {
//            @Override
//            public void accept(DelayTask delayTask) {
//                // 下发 todo
//            }
//        }));
    }
}
