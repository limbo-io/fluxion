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

package io.fluxion.server.core.job.cmd;

import io.fluxion.server.infrastructure.cqrs.ICmd;
import io.fluxion.server.infrastructure.exception.ErrorCode;
import io.fluxion.server.infrastructure.exception.PlatformException;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;

/**
 * @author Devil
 */
@Getter
public class JobStartCmd implements ICmd<Boolean> {

    private final String jobId;

    private final LocalDateTime reportAt;

    private String workerAddress;

    public JobStartCmd(String jobId, LocalDateTime reportAt) {
        this.jobId = jobId;
        this.reportAt = reportAt;
    }

    public JobStartCmd(String jobId, LocalDateTime reportAt, String workerAddress) {
        if (StringUtils.isBlank(workerAddress)) {
            throw new PlatformException(ErrorCode.PARAM_ERROR, "[TaskStartCmd] jobId:" + jobId +" workerAddress is blank");
        }
        this.jobId = jobId;
        this.reportAt = reportAt;
        this.workerAddress = workerAddress;
    }
}
