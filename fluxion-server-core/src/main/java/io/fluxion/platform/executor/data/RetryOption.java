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

package io.fluxion.platform.executor.data;

import lombok.*;

/**
 * 重试参数
 *
 * @author KaiFengCai
 * @since 2023/2/3
 */
@Data
@Setter(AccessLevel.NONE)
@NoArgsConstructor
@AllArgsConstructor
@Builder(builderClassName = "Builder", toBuilder = true)
public class RetryOption {

    /**
     * 重试次数
     */
    private Integer retry = 0;

    /**
     * 重试间隔 秒
     */
    private Integer retryInterval = 0;

    /**
     * 重试方式
     */
    private String retryType = RetryType.ALL.getType();

}
