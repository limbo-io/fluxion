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

package io.fluxion.server.core.flow.handler;

import io.fluxion.common.utils.Lambda;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.core.flow.FlowConfig;
import io.fluxion.server.core.flow.cmd.*;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.dao.entity.FlowEntity;
import io.fluxion.server.infrastructure.dao.repository.FlowEntityRepo;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import io.fluxion.server.infrastructure.validata.ValidateSuppressInfo;
import io.fluxion.server.infrastructure.version.cmd.VersionCreateCmd;
import io.fluxion.server.infrastructure.version.cmd.VersionUpdateCmd;
import io.fluxion.server.infrastructure.version.model.VersionRefType;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.criteria.Root;
import java.util.List;

/**
 * @author Devil
 */
@Service
public class FlowCommandService {

    @Resource
    private FlowEntityRepo flowEntityRepo;

    @Resource
    private EntityManager entityManager;

    @CommandHandler
    public FlowCreateCmd.Response handle(FlowCreateCmd cmd) {
        String flowId = Cmd.send(new IDGenerateCmd(IDType.FLOW)).getId();

        String versionId = Cmd.send(new VersionCreateCmd(flowId, VersionRefType.FLOW, null)).getVersion();

        FlowEntity flowEntity = new FlowEntity();
        flowEntity.setFlowId(flowId);
        flowEntity.setName(cmd.getName());
        flowEntity.setDescription(cmd.getDescription());
        flowEntity.setDraftVersion(versionId);
        flowEntityRepo.saveAndFlush(flowEntity);
        return new FlowCreateCmd.Response(flowId);
    }

    @CommandHandler
    public FlowDraftCmd.Response handle(FlowDraftCmd cmd) {
        FlowEntity flowEntity = flowEntityRepo.findById(cmd.getId()).orElseThrow(
            PlatformException.supplier(ErrorCode.PARAM_ERROR, "can't find flow by id:" + cmd.getId())
        );
        String configJson = JacksonUtils.toJSONString(cmd.getConfig());
        if (StringUtils.isBlank(flowEntity.getDraftVersion())) {
            String version = Cmd.send(new VersionCreateCmd(flowEntity.getFlowId(), VersionRefType.FLOW, configJson)).getVersion();
            flowEntity.setDraftVersion(version);
            flowEntityRepo.saveAndFlush(flowEntity);
        } else {
            Cmd.send(new VersionUpdateCmd(flowEntity.getFlowId(), VersionRefType.FLOW, flowEntity.getDraftVersion(), configJson));
        }
        return new FlowDraftCmd.Response(flowEntity.getDraftVersion());
    }

    @CommandHandler
    public FlowPublishCmd.Response handle(FlowPublishCmd cmd) {
        FlowEntity flowEntity = flowEntityRepo.findById(cmd.getId()).orElseThrow(
            PlatformException.supplier(ErrorCode.PARAM_ERROR, "can't find flow by id:" + cmd.getId())
        );
        if (StringUtils.isBlank(flowEntity.getDraftVersion())) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "not find draft when publish by id:" + cmd.getId());
        }
        FlowConfig config = cmd.getConfig();
        List<ValidateSuppressInfo> validateSuppressInfos = config.validate();
        if (CollectionUtils.isNotEmpty(validateSuppressInfos)) {
            return new FlowPublishCmd.Response(null, validateSuppressInfos);
        }
        String configJson = JacksonUtils.toJSONString(config);
        Cmd.send(new VersionUpdateCmd(flowEntity.getFlowId(), VersionRefType.FLOW, flowEntity.getDraftVersion(), configJson));
        String runVersion = flowEntity.getDraftVersion();
        flowEntity.setRunVersion(runVersion);
        flowEntity.setDraftVersion(StringUtils.EMPTY);
        flowEntityRepo.saveAndFlush(flowEntity);
        return new FlowPublishCmd.Response(runVersion, null);
    }

    @CommandHandler
    public void handle(FlowUpdateCmd cmd) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<FlowEntity> update = cb.createCriteriaUpdate(FlowEntity.class);
        Root<FlowEntity> root = update.from(FlowEntity.class);

        update.set(Lambda.name(FlowEntity::getName), cmd.getName());
        update.set(Lambda.name(FlowEntity::getDescription), cmd.getDescription());

        update.where(cb.equal(root.get(Lambda.name(FlowEntity::getFlowId)), cmd.getId()));

        entityManager.createQuery(update).executeUpdate();
    }

    @CommandHandler
    public void handle(FlowDeleteCmd cmd) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<FlowEntity> update = cb.createCriteriaUpdate(FlowEntity.class);
        Root<FlowEntity> root = update.from(FlowEntity.class);

        update.set(Lambda.name(FlowEntity::isDeleted), true);

        update.where(cb.equal(root.get(Lambda.name(FlowEntity::getFlowId)), cmd.getId()));

        entityManager.createQuery(update).executeUpdate();
    }

}
