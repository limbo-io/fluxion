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

package io.fluxion.worker.core.job;

import io.fluxion.remote.core.constants.ExecuteMode;
import io.fluxion.remote.core.constants.JobStatus;

/**
 * @author Devil
 * @since 2021/7/24
 */
public class Job {

    private String id;

    private ExecuteMode executeMode;

    /**
     * 执行器的名称
     */
    private String executorName;

    /**
     * 状态
     */
    private JobStatus status;


    private String result;

    private String errorMsg;

    public ExecuteMode getExecuteMode() {
        return executeMode;
    }

    public void setExecuteMode(ExecuteMode executeMode) {
        this.executeMode = executeMode;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExecutorName() {
        return executorName;
    }

    public void setExecutorName(String executorName) {
        this.executorName = executorName;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void success(String result) {
        this.result = result;
        setStatus(JobStatus.SUCCEED);
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void fail(String errorMsg) {
        this.errorMsg = errorMsg;
        setStatus(JobStatus.FAILED);
    }

}
