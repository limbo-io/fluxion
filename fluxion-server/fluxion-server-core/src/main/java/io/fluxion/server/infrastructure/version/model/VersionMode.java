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

package io.fluxion.server.infrastructure.version.model;

/**
 * @author Devil
 */
public enum VersionMode {
    /**
     * 不获取版本数据
     */
    NONE,
    /**
     * 获取运行版本
     */
    PUBLISH,
    /**
     * 获取草稿版本
     */
    DRAFT,
    /**
     * 运行版本优先，没有获取草稿
     */
    PUBLISH_FIRST,
    ;
}
