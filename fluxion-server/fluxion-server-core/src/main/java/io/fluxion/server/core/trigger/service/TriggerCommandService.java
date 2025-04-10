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

package io.fluxion.server.core.trigger.service;

import io.fluxion.common.utils.Lambda;
import io.fluxion.server.core.schedule.cmd.ScheduleDeleteCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDisableCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleEnableCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleSaveCmd;
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.TriggerConfig;
import io.fluxion.server.core.trigger.TriggerType;
import io.fluxion.server.core.trigger.cmd.TriggerCreateCmd;
import io.fluxion.server.core.trigger.cmd.TriggerDeleteCmd;
import io.fluxion.server.core.trigger.cmd.TriggerDisableCmd;
import io.fluxion.server.core.trigger.cmd.TriggerDraftCmd;
import io.fluxion.server.core.trigger.cmd.TriggerEnableCmd;
import io.fluxion.server.core.trigger.cmd.TriggerPublishCmd;
import io.fluxion.server.core.trigger.cmd.TriggerUpdateCmd;
import io.fluxion.server.core.trigger.config.ScheduleTriggerConfig;
import io.fluxion.server.core.trigger.converter.TriggerEntityConverter;
import io.fluxion.server.core.trigger.query.TriggerByIdQuery;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.TriggerEntity;
import io.fluxion.server.infrastructure.dao.repository.TriggerEntityRepo;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
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
import javax.transaction.Transactional;
import java.util.List;

/**
 * @author Devil
 */
@Service
public class TriggerCommandService {

    @Resource
    private TriggerEntityRepo triggerEntityRepo;

    @Resource
    private EntityManager entityManager;

    @Transactional
    @CommandHandler
    public TriggerCreateCmd.Response handle(TriggerCreateCmd cmd) {
        String id = Cmd.send(new IDGenerateCmd(IDType.TRIGGER)).getId();
        TriggerEntity entity = new TriggerEntity();
        entity.setTriggerId(id);
        entity.setName(cmd.getName());
        entity.setDescription(cmd.getDescription());
        entity.setEnabled(false);
        triggerEntityRepo.saveAndFlush(entity);
        return new TriggerCreateCmd.Response(id);
    }

    @Transactional
    @CommandHandler
    public void handle(TriggerUpdateCmd cmd) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<TriggerEntity> update = cb.createCriteriaUpdate(TriggerEntity.class);
        Root<TriggerEntity> root = update.from(TriggerEntity.class);

        update.set(Lambda.name(TriggerEntity::getDescription), StringUtils.defaultIfBlank(cmd.getDescription(), StringUtils.EMPTY));
        update.set(Lambda.name(TriggerEntity::getName), cmd.getName());

        update.where(cb.equal(root.get(Lambda.name(TriggerEntity::getTriggerId)), cmd.getId()));
        entityManager.createQuery(update).executeUpdate();
    }

    @Transactional
    @CommandHandler
    public void handle(TriggerDraftCmd cmd) {
        TriggerEntity entity = triggerEntityRepo.findById(cmd.getId()).orElseThrow(
            PlatformException.supplier(ErrorCode.PARAM_ERROR, "can't find trigger by id:" + cmd.getId())
        );
        String config = TriggerEntityConverter.config(cmd.getTriggerConfig());
        if (StringUtils.isBlank(entity.getDraftVersion())) {
            String version = Cmd.send(new VersionSaveCmd(TriggerEntityConverter.versionId(entity.getTriggerId()), config)).getVersion();
            entity.setDraftVersion(version);
            triggerEntityRepo.saveAndFlush(entity);
        } else {
            Cmd.send(new VersionSaveCmd(TriggerEntityConverter.versionId(entity.getTriggerId(), entity.getDraftVersion()), config));
        }
    }

    @Transactional
    @CommandHandler
    public void handle(TriggerPublishCmd cmd) {
        TriggerConfig config = cmd.getTriggerConfig();
        if (config == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "config is null");
        }
        List<ValidateSuppressInfo> suppressInfos = config.validate();
        if (CollectionUtils.isNotEmpty(suppressInfos)) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, suppressInfos.get(0).getCode());
        }
        TriggerEntity entity = triggerEntityRepo.findById(cmd.getId()).orElseThrow(
            PlatformException.supplier(ErrorCode.PARAM_ERROR, "can't find trigger by id:" + cmd.getId())
        );
        String configJson = TriggerEntityConverter.config(cmd.getTriggerConfig());
        String publishVersion = Cmd.send(new VersionSaveCmd(TriggerEntityConverter.versionId(entity.getTriggerId(), entity.getDraftVersion()), configJson)).getVersion();
        entity.setPublishVersion(publishVersion);
        entity.setDraftVersion(StringUtils.EMPTY);
        triggerEntityRepo.saveAndFlush(entity);

        // 后续逻辑
        TriggerType type = config.type();
        switch (type) {
            case SCHEDULE:
                ScheduleTriggerConfig scheduleTrigger = (ScheduleTriggerConfig) config;
                ScheduleOption scheduleOption = scheduleTrigger.getScheduleOption();
                Cmd.send(new ScheduleSaveCmd(cmd.getId(), scheduleOption));
                break;
        }
    }

    @Transactional
    @CommandHandler
    public void handle(TriggerEnableCmd cmd) {
        Trigger trigger = findByIdWithNullError(cmd.getId(), true);
        triggerEntityRepo.updateEnable(cmd.getId(), true);
        // 后续逻辑
        TriggerType type = trigger.getConfig().type();
        switch (type) {
            case SCHEDULE:
                Cmd.send(new ScheduleEnableCmd(trigger.getId()));
                break;
        }
    }

    @Transactional
    @CommandHandler
    public void handle(TriggerDisableCmd cmd) {
        Trigger trigger = findByIdWithNullError(cmd.getId(), true);
        triggerEntityRepo.updateEnable(cmd.getId(), false);
        // 后续逻辑
        TriggerType type = trigger.getConfig().type();
        switch (type) {
            case SCHEDULE:
                Cmd.send(new ScheduleDisableCmd(trigger.getId()));
                break;
        }
    }

    @Transactional
    @CommandHandler
    public void handle(TriggerDeleteCmd cmd) {
        Trigger trigger = findByIdWithNullError(cmd.getId(), false);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<TriggerEntity> update = cb.createCriteriaUpdate(TriggerEntity.class);
        Root<TriggerEntity> root = update.from(TriggerEntity.class);

        update.set(Lambda.name(TriggerEntity::isDeleted), true);

        update.where(cb.equal(root.get(Lambda.name(TriggerEntity::getTriggerId)), cmd.getId()));

        entityManager.createQuery(update).executeUpdate();

        // 后续逻辑
        TriggerType type = trigger.getConfig().type();
        switch (type) {
            case SCHEDULE:
                Cmd.send(new ScheduleDeleteCmd(trigger.getId()));
                break;
        }
    }

    private Trigger findByIdWithNullError(String triggerId, boolean checkPublished) {
        Trigger trigger = Query.query(new TriggerByIdQuery(triggerId)).getTrigger();
        if (trigger == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "can't find trigger by id:" + triggerId);
        }
        if (checkPublished && !trigger.isPublished()) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "trigger id:" + triggerId + " not published");
        }
        return trigger;
    }

}
