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

package io.fluxion.server.core.flow.service;

import io.fluxion.common.utils.Lambda;
import io.fluxion.server.core.flow.FlowConfig;
import io.fluxion.server.core.flow.cmd.FlowCreateCmd;
import io.fluxion.server.core.flow.cmd.FlowDeleteCmd;
import io.fluxion.server.core.flow.cmd.FlowDraftCmd;
import io.fluxion.server.core.flow.cmd.FlowPublishCmd;
import io.fluxion.server.core.flow.cmd.FlowUpdateCmd;
import io.fluxion.server.core.flow.converter.FlowEntityConverter;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.dao.entity.FlowEntity;
import io.fluxion.server.infrastructure.dao.repository.FlowEntityRepo;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import io.fluxion.server.infrastructure.validata.ValidateSuppressInfo;
import io.fluxion.server.infrastructure.version.cmd.VersionSaveCmd;
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
        FlowEntity flowEntity = new FlowEntity();
        flowEntity.setFlowId(flowId);
        flowEntity.setName(cmd.getName());
        flowEntity.setDescription(cmd.getDescription());
        flowEntityRepo.saveAndFlush(flowEntity);
        return new FlowCreateCmd.Response(flowId);
    }

    @CommandHandler
    public FlowDraftCmd.Response handle(FlowDraftCmd cmd) {
        FlowEntity entity = flowEntityRepo.findById(cmd.getId()).orElseThrow(
            PlatformException.supplier(ErrorCode.PARAM_ERROR, "can't find flow by id:" + cmd.getId())
        );
        String config = FlowEntityConverter.config(cmd.getConfig());
        if (StringUtils.isBlank(entity.getDraftVersion())) {
            String version = Cmd.send(new VersionSaveCmd(FlowEntityConverter.versionId(entity.getFlowId()), config)).getVersion();
            entity.setDraftVersion(version);
            flowEntityRepo.saveAndFlush(entity);
        } else {
            Cmd.send(new VersionSaveCmd(FlowEntityConverter.versionId(entity.getFlowId(), entity.getDraftVersion()), config));
        }
        return new FlowDraftCmd.Response(entity.getDraftVersion());
    }

    @CommandHandler
    public FlowPublishCmd.Response handle(FlowPublishCmd cmd) {
        FlowEntity entity = flowEntityRepo.findById(cmd.getId()).orElseThrow(
            PlatformException.supplier(ErrorCode.PARAM_ERROR, "can't find flow by id:" + cmd.getId())
        );
        FlowConfig config = cmd.getConfig();
        List<ValidateSuppressInfo> validateSuppressInfos = config.validate();
        if (CollectionUtils.isNotEmpty(validateSuppressInfos)) {
            return new FlowPublishCmd.Response(null, validateSuppressInfos);
        }
        String configJson = FlowEntityConverter.config(cmd.getConfig());
        String publishVersion = Cmd.send(new VersionSaveCmd(FlowEntityConverter.versionId(entity.getFlowId(), entity.getDraftVersion()), configJson)).getVersion();
        entity.setPublishVersion(publishVersion);
        entity.setDraftVersion(StringUtils.EMPTY);
        flowEntityRepo.saveAndFlush(entity);
        return new FlowPublishCmd.Response(publishVersion, null);
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
