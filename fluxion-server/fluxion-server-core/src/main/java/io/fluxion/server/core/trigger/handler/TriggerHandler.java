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

package io.fluxion.server.core.trigger.handler;

import io.fluxion.common.utils.Lambda;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.core.trigger.TriggerConfig;
import io.fluxion.server.core.trigger.cmd.*;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Event;
import io.fluxion.server.infrastructure.dao.entity.TriggerEntity;
import io.fluxion.server.infrastructure.dao.repository.TriggerEntityRepo;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import io.fluxion.server.infrastructure.validata.ValidateSuppressInfo;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;
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
@Component
public class TriggerHandler {

    @Resource
    private TriggerEntityRepo triggerEntityRepo;

    @Resource
    private EntityManager entityManager;

    @CommandHandler
    public TriggerCreateCmd.Response handle(TriggerCreateCmd cmd) {
        String id = Cmd.send(new IDGenerateCmd(IDType.TRIGGER)).getId();
        TriggerEntity entity = new TriggerEntity();
        entity.setTriggerId(id);
        entity.setType(cmd.getType());
        entity.setRefId(cmd.getRefId());
        entity.setRefType(cmd.getRefType().value);
        entity.setDescription(cmd.getDescription());
        entity.setEnabled(false);
        triggerEntityRepo.saveAndFlush(entity);
        return new TriggerCreateCmd.Response(id);
    }

    @CommandHandler
    public void handle(TriggerUpdateCmd cmd) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<TriggerEntity> update = cb.createCriteriaUpdate(TriggerEntity.class);
        Root<TriggerEntity> root = update.from(TriggerEntity.class);

        update.set(Lambda.name(TriggerEntity::getDescription), StringUtils.defaultIfBlank(cmd.getDescription(), StringUtils.EMPTY));

        update.where(cb.equal(root.get(Lambda.name(TriggerEntity::getTriggerId)), cmd.getId()));
        entityManager.createQuery(update).executeUpdate();
    }

    @CommandHandler
    public void handle(TriggerPublishCmd cmd) {
        TriggerConfig config = cmd.getConfig();
        if (cmd.getConfig() == null) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "config is null");
        }
        List<ValidateSuppressInfo> suppressInfos = config.validate();
        if (CollectionUtils.isNotEmpty(suppressInfos)) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, suppressInfos.get(0).getCode());
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<TriggerEntity> update = cb.createCriteriaUpdate(TriggerEntity.class);
        Root<TriggerEntity> root = update.from(TriggerEntity.class);

        update.set(Lambda.name(TriggerEntity::getConfig), JacksonUtils.toJSONString(config));

        update.where(cb.equal(root.get(Lambda.name(TriggerEntity::getTriggerId)), cmd.getId()));
        entityManager.createQuery(update).executeUpdate();
        // 发送事件
        Event.publish(cmd);
    }


    @CommandHandler
    public void handle(TriggerEnableCmd cmd) {
        TriggerEntity entity = triggerEntityRepo.findById(cmd.getId()).orElseThrow(
            PlatformException.supplier(ErrorCode.PARAM_ERROR, "can't find trigger by id:" + cmd.getId())
        );
        entity.setEnabled(true);
        triggerEntityRepo.saveAndFlush(entity);
        // 发布事件
        Event.publish(cmd);
    }

    @CommandHandler
    public void handle(TriggerDisableCmd cmd) {
        TriggerEntity entity = triggerEntityRepo.findById(cmd.getId()).orElseThrow(
            PlatformException.supplier(ErrorCode.PARAM_ERROR, "can't find trigger by id:" + cmd.getId())
        );
        entity.setEnabled(false);
        triggerEntityRepo.saveAndFlush(entity);
        // 发布事件
        Event.publish(cmd);
    }

    @CommandHandler
    public void handle(TriggerDeleteCmd cmd) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaUpdate<TriggerEntity> update = cb.createCriteriaUpdate(TriggerEntity.class);
        Root<TriggerEntity> root = update.from(TriggerEntity.class);

        update.set(Lambda.name(TriggerEntity::isDeleted), true);

        update.where(cb.equal(root.get(Lambda.name(TriggerEntity::getTriggerId)), cmd.getId()));

        entityManager.createQuery(update).executeUpdate();

        // todo 发送事件 触发器删除时候要继续删除对应数据
    }

}
