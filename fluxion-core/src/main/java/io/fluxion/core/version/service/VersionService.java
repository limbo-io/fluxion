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

package io.fluxion.core.version.service;

import io.fluxion.common.utils.UUIDUtils;
import io.fluxion.core.exception.ErrorCode;
import io.fluxion.core.dao.entity.VersionEntity;
import io.fluxion.core.dao.repository.VersionEntityRepo;
import io.fluxion.core.version.cmd.VersionCreateCmd;
import io.fluxion.core.version.cmd.VersionUpdateCmd;
import io.fluxion.core.version.model.Version;
import io.fluxion.core.version.model.VersionGenerateType;
import io.fluxion.core.version.model.VersionRefType;
import io.fluxion.core.version.query.VersionByIdQuery;
import io.fluxion.core.exception.PlatformException;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.EnumMap;
import java.util.Map;

/**
 * @author Devil
 */
@Slf4j
@Service
public class VersionService {

    @Resource
    private VersionEntityRepo versionEntityRepo;

    private static final Map<VersionRefType, VersionGenerateType> REF_GENERATE_TYPES = new EnumMap<>(VersionRefType.class);

    static {
        REF_GENERATE_TYPES.put(VersionRefType.FLOW, VersionGenerateType.INCR);
    }

    @QueryHandler
    public VersionByIdQuery.Response handle(VersionByIdQuery query) {
        VersionEntity entity = versionEntityRepo.findById(new VersionEntity.ID(query.getRefId(), query.getRefType().type, query.getVersion())).orElse(null);
        if (entity == null) {
            return null;
        }
        return new VersionByIdQuery.Response(to(entity));
    }

    @CommandHandler
    public VersionCreateCmd.Response handle(VersionCreateCmd cmd) {
        VersionEntity entity = new VersionEntity();
        String version = nextVersion(cmd);
        entity.setId(new VersionEntity.ID(cmd.getRefId(), cmd.getRefType().type, version));
        entity.setConfig(cmd.getConfig());
        versionEntityRepo.saveAndFlush(entity);
        return new VersionCreateCmd.Response(version);
    }

    @CommandHandler
    public VersionUpdateCmd.Response handle(VersionUpdateCmd cmd) {
        VersionEntity entity = versionEntityRepo.findById(new VersionEntity.ID(cmd.getRefId(), cmd.getRefType().type, cmd.getVersion())).orElse(null);
        if (entity == null) {
            throw new PlatformException(
                ErrorCode.PARAM_ERROR,
                "can't find version by refId:" + cmd.getRefId() + " refType:" + cmd.getRefType() + " version:" + cmd.getVersion()
            );
        }
        entity.setConfig(cmd.getConfig());
        versionEntityRepo.saveAndFlush(entity);
        return new VersionUpdateCmd.Response();
    }

    private String nextVersion(VersionCreateCmd cmd) {
        VersionGenerateType generateType = REF_GENERATE_TYPES.get(cmd.getRefType());
        switch (generateType) {
            case INCR:
                return incrVersion(cmd.getRefId(), cmd.getRefType().name());
            case RANDOM:
                return randomVersion();
            default:
                throw new PlatformException(
                    ErrorCode.PARAM_ERROR,
                    "can't match refType:" + cmd.getRefType() + " generateType:" + generateType
                );
        }
    }

    private String incrVersion(String refId, String refType) {
        VersionEntity last = versionEntityRepo.findLast(refId, refType);
        long version = 1L;
        if (last != null) {
            version = Long.parseLong(last.getId().getVersion()) + 1;
        }
        return String.valueOf(version);
    }

    private String randomVersion() {
        return UUIDUtils.randomID();
    }

    private Version to(VersionEntity entity) {
        Version version = new Version();
        version.setRefId(entity.getId().getRefId());
        version.setRefType(VersionRefType.parse(entity.getId().getRefType()));
        version.setVersion(entity.getId().getVersion());
        version.setConfig(entity.getConfig());
        return version;
    }

}
