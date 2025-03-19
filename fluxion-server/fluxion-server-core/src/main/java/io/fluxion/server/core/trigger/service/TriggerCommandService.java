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
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.core.schedule.Schedule;
import io.fluxion.server.core.schedule.cmd.ScheduleCreateCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDeleteCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleDisableCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleEnableCmd;
import io.fluxion.server.core.schedule.cmd.ScheduleUpdateCmd;
import io.fluxion.server.core.schedule.query.ScheduleByIdQuery;
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.TriggerConfig;
import io.fluxion.server.core.trigger.TriggerType;
import io.fluxion.server.core.trigger.cmd.TriggerCreateCmd;
import io.fluxion.server.core.trigger.cmd.TriggerDeleteCmd;
import io.fluxion.server.core.trigger.cmd.TriggerDisableCmd;
import io.fluxion.server.core.trigger.cmd.TriggerEnableCmd;
import io.fluxion.server.core.trigger.cmd.TriggerUpdateCmd;
import io.fluxion.server.core.trigger.config.ScheduleTriggerConfig;
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
public class TriggerCommandService {

    @Resource
    private TriggerEntityRepo triggerEntityRepo;

    @Resource
    private EntityManager entityManager;

    @CommandHandler
    public TriggerCreateCmd.Response handle(TriggerCreateCmd cmd) {
        Trigger trigger = cmd.getTrigger();
        triggerSaveCheck(trigger);
        String id = Cmd.send(new IDGenerateCmd(IDType.TRIGGER)).getId();
        TriggerEntity entity = new TriggerEntity();
        entity.setTriggerId(id);
        entity.setExecuteConfig(JacksonUtils.toJSONString(trigger.getExecuteConfig()));
        entity.setName(trigger.getName());
        entity.setDescription(trigger.getDescription());
        entity.setTriggerConfig(JacksonUtils.toJSONString(trigger.getTriggerConfig()));
        entity.setEnabled(false);
        triggerEntityRepo.saveAndFlush(entity);

        // 后续逻辑
        TriggerType type = trigger.getTriggerConfig().type();
        switch (type) {
            case SCHEDULE:
                ScheduleTriggerConfig scheduleTrigger = (ScheduleTriggerConfig) trigger.getTriggerConfig();
                ScheduleOption scheduleOption = scheduleTrigger.getScheduleOption();
                Schedule schedule = new Schedule();
                schedule.setEnabled(false);
                schedule.setId(id);
                schedule.setOption(scheduleOption);
                Cmd.send(new ScheduleCreateCmd(schedule));
                break;
        }

        return new TriggerCreateCmd.Response(id);
    }

    @CommandHandler
    public void handle(TriggerUpdateCmd cmd) {
        Trigger trigger = cmd.getTrigger();
        triggerSaveCheck(trigger);
        // todo 触发方式不能修改??? 否则 schedule等得先删后增
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<TriggerEntity> update = cb.createCriteriaUpdate(TriggerEntity.class);
        Root<TriggerEntity> root = update.from(TriggerEntity.class);

        update.set(Lambda.name(TriggerEntity::getExecuteConfig), JacksonUtils.toJSONString(trigger.getExecuteConfig()));
        update.set(Lambda.name(TriggerEntity::getDescription), StringUtils.defaultIfBlank(trigger.getDescription(), StringUtils.EMPTY));
        update.set(Lambda.name(TriggerEntity::getTriggerConfig), JacksonUtils.toJSONString(trigger.getTriggerConfig()));
        update.set(Lambda.name(TriggerEntity::getName), trigger.getName());

        update.where(cb.equal(root.get(Lambda.name(TriggerEntity::getTriggerId)), trigger.getId()));
        int rows = entityManager.createQuery(update).executeUpdate();
        if (rows <= 0) {
            return;
        }

        // 后续逻辑
        TriggerType type = trigger.getTriggerConfig().type();
        switch (type) {
            case SCHEDULE:
                ScheduleTriggerConfig scheduleTrigger = (ScheduleTriggerConfig) trigger.getTriggerConfig();
                ScheduleOption scheduleOption = scheduleTrigger.getScheduleOption();
                Schedule schedule = Query.query(new ScheduleByIdQuery(trigger.getId())).getSchedule();
                schedule.setOption(scheduleOption);
                Cmd.send(new ScheduleUpdateCmd(schedule));
                break;
        }
    }

    private void triggerSaveCheck(Trigger trigger) {
        TriggerConfig config = trigger.getTriggerConfig();
        if (config == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "config is null");
        }
//        if (trigger.getType() != config.type()) {
//            throw new PlatformException(ErrorCode.PARAM_ERROR, "config type not match");
//        }
        List<ValidateSuppressInfo> suppressInfos = config.validate();
        if (CollectionUtils.isNotEmpty(suppressInfos)) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, suppressInfos.get(0).getCode());
        }
    }


    @CommandHandler
    public void handle(TriggerEnableCmd cmd) {
        Trigger trigger = findByIdWithNullError(cmd.getId());
        triggerEntityRepo.updateEnable(cmd.getId(), true);
        // 后续逻辑
        TriggerType type = trigger.getTriggerConfig().type();
        switch (type) {
            case SCHEDULE:
                Cmd.send(new ScheduleEnableCmd(trigger.getId()));
                break;
        }
    }

    @CommandHandler
    public void handle(TriggerDisableCmd cmd) {
        Trigger trigger = findByIdWithNullError(cmd.getId());
        triggerEntityRepo.updateEnable(cmd.getId(), false);
        // 后续逻辑
        TriggerType type = trigger.getTriggerConfig().type();
        switch (type) {
            case SCHEDULE:
                Cmd.send(new ScheduleDisableCmd(trigger.getId()));
                break;
        }
    }

    @CommandHandler
    public void handle(TriggerDeleteCmd cmd) {
        Trigger trigger = findByIdWithNullError(cmd.getId());
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<TriggerEntity> update = cb.createCriteriaUpdate(TriggerEntity.class);
        Root<TriggerEntity> root = update.from(TriggerEntity.class);

        update.set(Lambda.name(TriggerEntity::isDeleted), true);

        update.where(cb.equal(root.get(Lambda.name(TriggerEntity::getTriggerId)), cmd.getId()));

        entityManager.createQuery(update).executeUpdate();

        // 后续逻辑
        TriggerType type = trigger.getTriggerConfig().type();
        switch (type) {
            case SCHEDULE:
                Cmd.send(new ScheduleDeleteCmd(trigger.getId()));
                break;
        }
    }

    private Trigger findByIdWithNullError(String triggerId) {
        Trigger trigger = Query.query(new TriggerByIdQuery(triggerId)).getTrigger();
        if (trigger == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "can't find trigger by id:" + triggerId);
        }
        return trigger;
    }

}
