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

package io.fluxion.remote.lb;

import java.time.Instant;

/**
 *
 *
 * @author Brozen
 */
public interface LBServerStatistics {

    /**
     * 当前服务的唯一 ID
     */
    String getServerId();

    /**
     * 获取最近一次使用时间。如没有被使用过，则应返回 {@link Instant#EPOCH}
     */
    Instant getLatestAccessAt();

    /**
     * 获取访问次数
     */
    int getAccessTimes();

}
