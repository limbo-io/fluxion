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

package io.fluxion.server.core.app.handler;

import io.fluxion.remote.core.cluster.Node;
import io.fluxion.server.core.app.App;
import io.fluxion.server.core.app.cmd.AppRegisterCmd;
import io.fluxion.server.core.app.query.AppByIdQuery;
import io.fluxion.server.core.cluster.ClusterContext;
import io.fluxion.server.core.cluster.NodeManger;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.dao.entity.AppEntity;
import io.fluxion.server.infrastructure.dao.repository.AppEntityRepo;
import io.fluxion.server.infrastructure.id.cmd.IDGenerateCmd;
import io.fluxion.server.infrastructure.id.data.IDType;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@Component
public class AppQueryHandler {

    @Resource
    private AppEntityRepo appEntityRepo;

    @Resource
    private NodeManger nodeManger;

    @CommandHandler
    public AppByIdQuery.Response handle(AppByIdQuery query) {
        AppEntity entity = appEntityRepo.findById(query.getAppId()).orElse(null);
        if (entity == null) {
            return new AppByIdQuery.Response(null);
        }
        Node node = nodeManger.get(entity.getBrokerId());
        App app = AppEntityConverter.convert(entity, node);
        return new AppByIdQuery.Response(app);
    }

}
