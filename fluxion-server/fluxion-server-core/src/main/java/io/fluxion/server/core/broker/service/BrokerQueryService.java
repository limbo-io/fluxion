/*
 * Copyright 2025-2030 limbo-io Team (https://github.com/limbo-io).
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

package io.fluxion.server.core.broker.service;

import io.fluxion.server.core.broker.BrokerManger;
import io.fluxion.server.core.broker.BrokerNode;
import io.fluxion.server.core.broker.query.BrokersQuery;
import org.apache.commons.collections4.CollectionUtils;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * @author Devil
 */
@Service
public class BrokerQueryService {

    @Resource
    private BrokerManger brokerManger;

    @QueryHandler
    public BrokersQuery.Response handle(BrokersQuery query) {
        List<BrokerNode> brokers = brokerManger.allAlive();
        String version = brokerManger.version();
        if (CollectionUtils.isEmpty(brokers)) {
            brokers = Collections.emptyList();
        }
        return new BrokersQuery.Response(version, brokers);
    }
}
