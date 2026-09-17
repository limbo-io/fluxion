/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/Fluxion-io).
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

package io.fluxion.server.start.service;

import io.fluxion.server.core.observation.ObservationOverviewQuery;
import io.fluxion.server.start.api.observation.view.ObservationOverviewView;
import io.limbo.cqrs.core.queryhandling.Query;
import org.springframework.stereotype.Service;

/**
 * 存量观测：只读、直接聚合 MySQL 单点权威，无 Micrometer 依赖。
 *
 * @author Devil
 */
@Service
public class ObservationService {

    public ObservationOverviewView overview() {
        ObservationOverviewQuery.Response response = Query.query(new ObservationOverviewQuery());
        ObservationOverviewView view = new ObservationOverviewView();
        view.setExecutions(response.getExecutions());
        view.setExecutionBacklog(response.getExecutionBacklog());
        view.setExecutionMisfireCandidates(response.getExecutionMisfireCandidates());
        view.setExecutionReclaimableClaims(response.getExecutionReclaimableClaims());
        view.setJobs(response.getJobs());
        view.setWorkers(response.getWorkers());
        return view;
    }
}