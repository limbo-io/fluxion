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

package io.fluxion.remote.core.lb.strategies;


import io.fluxion.remote.core.lb.Invocation;
import io.fluxion.remote.core.lb.LBServer;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Brozen
 */
public class RandomLBStrategy<S extends LBServer> extends AbstractLBStrategy<S> {

    @Override
    protected S doSelect(List<S> servers, Invocation invocation) {
        return servers.get(ThreadLocalRandom.current().nextInt(servers.size()));
    }
}
