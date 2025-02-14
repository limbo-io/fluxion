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

package io.fluxion.server.infrastructure.tag.cmd;

import io.fluxion.server.infrastructure.cqrs.ICmd;
import io.fluxion.server.infrastructure.tag.Tag;
import io.fluxion.server.infrastructure.tag.TagRefType;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Devil
 */
@Getter
@AllArgsConstructor
public class TagCreateCmd implements ICmd<Void> {

    /**
     * 关联的数据ID
     */
    private final String refId;
    /**
     * 关联的数据类型
     */
    private final TagRefType refType;

    private final Tag tag;

}
