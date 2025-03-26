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

package io.fluxion.server.core.workflow.service;

import io.fluxion.common.utils.Lambda;
import io.fluxion.server.core.workflow.WorkflowConfig;
import io.fluxion.server.core.workflow.cmd.WorkflowCreateCmd;
import io.fluxion.server.core.workflow.cmd.WorkflowDeleteCmd;
import io.fluxion.server.core.workflow.cmd.WorkflowDraftCmd;
import io.fluxion.server.core.workflow.cmd.WorkflowPublishCmd;
import io.fluxion.server.core.workflow.cmd.WorkflowUpdateCmd;
import io.fluxion.server.core.workflow.converter.WorkflowEntityConverter;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.dao.entity.WorkflowEntity;
import io.fluxion.server.infrastructure.dao.repository.WorkflowEntityRepo;
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
public class WorkflowCommandService {

    @Resource
    private WorkflowEntityRepo workflowEntityRepo;

    @Resource
    private EntityManager entityManager;

    @CommandHandler
    public WorkflowCreateCmd.Response handle(WorkflowCreateCmd cmd) {
        String id = Cmd.send(new IDGenerateCmd(IDType.WORKFLOW)).getId();
        WorkflowEntity workflowEntity = new WorkflowEntity();
        workflowEntity.setWorkflowId(id);
        workflowEntity.setName(cmd.getName());
        workflowEntity.setDescription(cmd.getDescription());
        workflowEntityRepo.saveAndFlush(workflowEntity);
        return new WorkflowCreateCmd.Response(id);
    }

    @CommandHandler
    public WorkflowDraftCmd.Response handle(WorkflowDraftCmd cmd) {
        WorkflowEntity entity = workflowEntityRepo.findById(cmd.getId()).orElseThrow(
            PlatformException.supplier(ErrorCode.PARAM_ERROR, "can't find flow by id:" + cmd.getId())
        );
        String config = WorkflowEntityConverter.config(cmd.getConfig());
        if (StringUtils.isBlank(entity.getDraftVersion())) {
            String version = Cmd.send(new VersionSaveCmd(WorkflowEntityConverter.versionId(entity.getWorkflowId()), config)).getVersion();
            entity.setDraftVersion(version);
            workflowEntityRepo.saveAndFlush(entity);
        } else {
            Cmd.send(new VersionSaveCmd(WorkflowEntityConverter.versionId(entity.getWorkflowId(), entity.getDraftVersion()), config));
        }
        return new WorkflowDraftCmd.Response(entity.getDraftVersion());
    }

    @CommandHandler
    public WorkflowPublishCmd.Response handle(WorkflowPublishCmd cmd) {
        WorkflowEntity entity = workflowEntityRepo.findById(cmd.getId()).orElseThrow(
            PlatformException.supplier(ErrorCode.PARAM_ERROR, "can't find flow by id:" + cmd.getId())
        );
        WorkflowConfig config = cmd.getConfig();
        List<ValidateSuppressInfo> validateSuppressInfos = config.validate();
        if (CollectionUtils.isNotEmpty(validateSuppressInfos)) {
            return new WorkflowPublishCmd.Response(null, validateSuppressInfos);
        }
        String configJson = WorkflowEntityConverter.config(cmd.getConfig());
        String publishVersion = Cmd.send(new VersionSaveCmd(WorkflowEntityConverter.versionId(entity.getWorkflowId(), entity.getDraftVersion()), configJson)).getVersion();
        entity.setPublishVersion(publishVersion);
        entity.setDraftVersion(StringUtils.EMPTY);
        workflowEntityRepo.saveAndFlush(entity);
        return new WorkflowPublishCmd.Response(publishVersion, null);
    }

    @CommandHandler
    public void handle(WorkflowUpdateCmd cmd) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<WorkflowEntity> update = cb.createCriteriaUpdate(WorkflowEntity.class);
        Root<WorkflowEntity> root = update.from(WorkflowEntity.class);

        update.set(Lambda.name(WorkflowEntity::getName), cmd.getName());
        update.set(Lambda.name(WorkflowEntity::getDescription), cmd.getDescription());

        update.where(cb.equal(root.get(Lambda.name(WorkflowEntity::getWorkflowId)), cmd.getId()));

        entityManager.createQuery(update).executeUpdate();
    }

    @CommandHandler
    public void handle(WorkflowDeleteCmd cmd) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<WorkflowEntity> update = cb.createCriteriaUpdate(WorkflowEntity.class);
        Root<WorkflowEntity> root = update.from(WorkflowEntity.class);

        update.set(Lambda.name(WorkflowEntity::isDeleted), true);

        update.where(cb.equal(root.get(Lambda.name(WorkflowEntity::getWorkflowId)), cmd.getId()));

        entityManager.createQuery(update).executeUpdate();
    }

}
