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

package io.fluxion.server.core.execution.handler;

import io.fluxion.common.thread.CommonThreadPool;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.core.execution.Executable;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.ExecutionStatus;
import io.fluxion.server.core.execution.cmd.ExecutionCreateCmd;
import io.fluxion.server.core.execution.cmd.ExecutionRunCmd;
import io.fluxion.server.core.flow.Flow;
import io.fluxion.server.core.flow.FlowConfig;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import io.fluxion.server.infrastructure.dao.entity.FlowEntity;
import io.fluxion.server.infrastructure.dao.repository.ExecutionEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.FlowEntityRepo;
import io.fluxion.server.infrastructure.dao.repository.TaskEntityRepo;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import io.fluxion.server.infrastructure.version.model.Version;
import io.fluxion.server.infrastructure.version.model.VersionRefType;
import io.fluxion.server.infrastructure.version.query.VersionByIdQuery;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@Service
public class ExecutionCommandService {

    @Resource
    private ExecutionEntityRepo executionEntityRepo;

    @Resource
    private TaskEntityRepo taskEntityRepo;

    @Resource
    private FlowEntityRepo flowEntityRepo;

    @CommandHandler
    public ExecutionCreateCmd.Response handle(ExecutionCreateCmd cmd) {
        Executable executable = null;
        switch (cmd.getRefType()) {
            case FLOW:
                executable = flow(cmd.getRefId());
                break;
            case EXECUTOR:
                break;
        }
        if (executable == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "executable not found by refId:" + cmd.getRefId() + " refT");
        }
        // 判断是否已经创建
        ExecutionEntity entity = executionEntityRepo.findByRefIdAndRefTypeAndTriggerAt(cmd.getRefId(), cmd.getRefType().value, cmd.getTriggerAt());
        if (entity == null) {
            entity = new ExecutionEntity();
            entity.setExecutionId(Cmd.send(new IDGenerateCmd(IDType.EXECUTION)).getId());
            entity.setRefId(cmd.getRefId());
            entity.setRefType(cmd.getRefType().value);
            entity.setTriggerAt(cmd.getTriggerAt());
            entity.setStatus(ExecutionStatus.CREATED.value);
            executionEntityRepo.saveAndFlush(entity);
        }
        Execution execution = new Execution(entity.getExecutionId(), executable, ExecutionStatus.parse(entity.getStatus()));
        return new ExecutionCreateCmd.Response(execution);
    }

    private Flow flow(String id) {
        FlowEntity flowEntity = flowEntityRepo.findByFlowIdAndDeleted(id, false)
            .orElseThrow(() -> new PlatformException(ErrorCode.PARAM_ERROR, "flow not found id:" + id));
        if (StringUtils.isBlank(flowEntity.getRunVersion())) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "flow does not have runVersion id:" + id);
        }
        Version version = Query.query(VersionByIdQuery.builder()
            .refId(flowEntity.getFlowId())
            .refType(VersionRefType.FLOW)
            .version(flowEntity.getRunVersion())
            .build()
        ).getVersion();
        if (version == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "flow does not find runVersion id:" + id + " version:" + flowEntity.getRunVersion());

        }
        FlowConfig flowConfig = JacksonUtils.toType(version.getConfig(), FlowConfig.class);
        return Flow.of(flowEntity.getFlowId(), flowConfig);
    }

    @CommandHandler
    public ExecutionRunCmd.Response handle(ExecutionRunCmd cmd) {
        // todo @d 校验是否由当前节点触发
        Execution execution = cmd.getExecution();
        CommonThreadPool.IO.submit(execution::execute);
        return new ExecutionRunCmd.Response();
    }

}
