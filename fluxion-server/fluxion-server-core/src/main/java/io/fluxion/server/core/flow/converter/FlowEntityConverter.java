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

package io.fluxion.server.core.flow.converter;

import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.core.flow.FlowConfig;
import io.fluxion.server.infrastructure.version.model.Version;
import io.fluxion.server.infrastructure.version.model.VersionRefType;

/**
 * @author Devil
 */
public class FlowEntityConverter {

    public static Version.ID versionId(String flowId) {
        return new Version.ID(flowId, VersionRefType.FLOW, null);
    }

    public static Version.ID versionId(String flowId, String version) {
        return new Version.ID(flowId, VersionRefType.FLOW, version);
    }

    public static String config(FlowConfig flowConfig) {
        return JacksonUtils.toJSONString(flowConfig);
    }

}
