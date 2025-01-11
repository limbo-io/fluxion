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

package io.fluxion.server.api.flow.view;

import io.fluxion.core.flow.FlowConfig;
import lombok.Data;


/**
 * @author Devil
 */
@Data
public class FlowView {

    private String id;

    /**
     * 名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 运行版本
     */
    private String runVersion;

    /**
     * 草稿版本
     */
    private String draftVersion;

    /**
     * 配置信息
     */
    private FlowConfig config;
}
