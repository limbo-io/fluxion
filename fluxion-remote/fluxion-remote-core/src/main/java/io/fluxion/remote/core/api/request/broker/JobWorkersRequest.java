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

package io.fluxion.remote.core.api.request.broker;

import io.fluxion.remote.core.api.Request;
import io.fluxion.remote.core.api.response.broker.JobWorkersResponse;

/**
 * @author Devil
 */
public class JobWorkersRequest implements Request<JobWorkersResponse> {

    private String jobId;

    private boolean filterResource = false;

    private boolean loadBalanceSelect = false;

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public boolean isFilterResource() {
        return filterResource;
    }

    public void setFilterResource(boolean filterResource) {
        this.filterResource = filterResource;
    }

    public boolean isLoadBalanceSelect() {
        return loadBalanceSelect;
    }

    public void setLoadBalanceSelect(boolean loadBalanceSelect) {
        this.loadBalanceSelect = loadBalanceSelect;
    }
}
