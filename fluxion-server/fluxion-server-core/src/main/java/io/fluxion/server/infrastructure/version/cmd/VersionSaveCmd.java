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

package io.fluxion.server.infrastructure.version.cmd;

import io.fluxion.server.infrastructure.cqrs.ICmd;
import io.fluxion.server.infrastructure.version.model.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Devil
 */
@Getter
@AllArgsConstructor
public class VersionSaveCmd implements ICmd<VersionSaveCmd.Response> {

    /**
     * version为空新增
     * version有值修改
     */
    private final Version.ID id;

    private final String config;

    @Getter
    @AllArgsConstructor
    public static class Response {
        private String version;
    }
}
