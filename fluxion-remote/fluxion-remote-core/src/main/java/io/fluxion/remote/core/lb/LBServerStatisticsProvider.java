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

package io.fluxion.remote.core.lb;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * @author Brozen
 */
public interface LBServerStatisticsProvider<T extends LBServerStatistics> {

    /**
     * 查询 {@link LBServer} 的统计信息。
     *
     * @param serverIds 服务 ID 结合
     * @param interval  查询的统计信息时长
     */
    List<T> getStatistics(Set<String> serverIds, Duration interval);


}
