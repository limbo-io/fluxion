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

package io.fluxion.server.core.app.service;

import io.fluxion.server.core.app.cmd.AppSaveCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.dao.entity.AppEntity;
import io.fluxion.server.infrastructure.dao.repository.AppEntityRepo;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@Slf4j
@Service
public class AppCommandService {

    @Resource
    private AppEntityRepo appEntityRepo;

    @CommandHandler
    public AppSaveCmd.Response handle(AppSaveCmd cmd) {
        AppEntity entity = appEntityRepo.findByAppName(cmd.getAppName()).orElse(null);
        if (entity == null) {
            String appId = Cmd.send(new IDGenerateCmd(IDType.APP)).getId();
            entity = new AppEntity();
            entity.setAppName(cmd.getAppName());
            entity.setAppId(appId);
            appEntityRepo.saveAndFlush(entity);
        }
        return new AppSaveCmd.Response(entity.getAppId());
    }

}
