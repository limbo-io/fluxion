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

package io.fluxion.server.core.cluster;

import io.fluxion.server.core.broker.Broker;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @author Devil
 * @date 2025/1/10
 */
@Component
public class ClusterContext implements ApplicationContextAware {

    private static String currentNodeId;

    public static String currentNodeId() {
        return currentNodeId;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Broker broker = applicationContext.getBean(Broker.class);
        currentNodeId = broker.getRpcBaseURL().toString();
    }
}
