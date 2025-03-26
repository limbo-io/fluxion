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

package io.fluxion.server.infrastructure.version.service;

import io.fluxion.common.utils.UUIDUtils;
import io.fluxion.server.infrastructure.dao.entity.VersionEntity;
import io.fluxion.server.infrastructure.dao.repository.VersionEntityRepo;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import io.fluxion.server.infrastructure.version.cmd.VersionSaveCmd;
import io.fluxion.server.infrastructure.version.model.Version;
import io.fluxion.server.infrastructure.version.model.VersionGenerateType;
import io.fluxion.server.infrastructure.version.model.VersionRefType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import java.util.EnumMap;
import java.util.Map;

/**
 * @author Devil
 */
@Slf4j
@Service
public class VersionCommandService {

    @Resource
    private VersionEntityRepo versionEntityRepo;

    @Resource
    private EntityManager entityManager;

    private static final Map<VersionRefType, VersionGenerateType> REF_GENERATE_TYPES = new EnumMap<>(VersionRefType.class);

    static {
        REF_GENERATE_TYPES.put(VersionRefType.WORKFLOW, VersionGenerateType.INCR);
        REF_GENERATE_TYPES.put(VersionRefType.TRIGGER, VersionGenerateType.INCR);
    }

    @CommandHandler
    public VersionSaveCmd.Response handle(VersionSaveCmd cmd) {
        VersionEntity entity = new VersionEntity();
        Version.ID id = cmd.getId();
        String version = id.getVersion();
        if (StringUtils.isBlank(version)) {
            version = nextVersion(id);
        }
        VersionEntity.ID entityId = new VersionEntity.ID(id.getRefId(), id.getRefType().value, version);
        entity.setId(entityId);
        entity.setConfig(cmd.getConfig());
        versionEntityRepo.saveAndFlush(entity);
        return new VersionSaveCmd.Response(version);
    }

    private String nextVersion(Version.ID id) {
        VersionGenerateType generateType = REF_GENERATE_TYPES.get(id.getRefType());
        switch (generateType) {
            case INCR:
                return incrVersion(id.getRefId(), id.getRefType().name());
            case RANDOM:
                return randomVersion();
            default:
                throw new PlatformException(
                    ErrorCode.PARAM_ERROR,
                    "can't match refType:" + id.getRefType() + " generateType:" + generateType
                );
        }
    }

    private String incrVersion(String refId, String refType) {
        VersionEntity last = entityManager.createQuery("select e from VersionEntity e" +
                " where id.refId = :refId and id.refType = :refType " +
                " order by id.version desc ", VersionEntity.class
            )
            .setParameter("refId", refId)
            .setParameter("refType", refType)
            .setMaxResults(1)
            .getResultList().stream().findFirst().orElse(null);
        long version = 1L;
        if (last != null) {
            version = Long.parseLong(last.getId().getVersion()) + 1;
        }
        return String.valueOf(version);
    }

    private String randomVersion() {
        return UUIDUtils.randomID();
    }

}
