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

package io.fluxion.platform.worker.executor;

import lombok.*;

/**
 * worker可用的执行器
 *
 * @author Brozen
 * @since 2021-07-01
 */
@Data
@Setter(AccessLevel.NONE)
@Builder(builderClassName = "Builder")
@NoArgsConstructor
@AllArgsConstructor
public class WorkerExecutor {

    /**
     * 执行器名称
     */
    private String name;

    /**
     * 执行器描述信息
     */
    private String description;

}
