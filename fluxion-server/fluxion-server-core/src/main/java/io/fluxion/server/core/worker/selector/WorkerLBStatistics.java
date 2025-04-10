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

package io.fluxion.server.core.worker.selector;

import io.fluxion.remote.core.lb.LBServerStatistics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * @author Brozen
 * @since 2022-12-21
 */
@Data
@AllArgsConstructor
@Builder(builderClassName = "Builder")
public class WorkerLBStatistics implements LBServerStatistics {

    /**
     * Worker ID
     */
    private final String workerId;

    /**
     * 上次下发任务时间
     */
    private final Instant lastDispatchTaskAt;

    /**
     * 下发任务次数
     */
    private final int dispatchTimes;


    /**
     * {@inheritDoc}
     * @return
     */
    @Override
    public String serverId() {
        return workerId;
    }


    /**
     * {@inheritDoc}
     * @return
     */
    @Override
    public Instant latestAccessAt() {
        return lastDispatchTaskAt;
    }


    /**
     * {@inheritDoc}
     * @return
     */
    @Override
    public int accessTimes() {
        return dispatchTimes;
    }

}
