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

package io.fluxion.server.infrastructure.version.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.util.Objects;

/**
 * @author Devil
 */
@Data
public class Version {

    private ID id;
    /**
     * 本次版本的描述信息
     */
    private String description;
    /**
     * 配置信息
     */
    private String config;

    @Getter
    @AllArgsConstructor
    public static class ID {
        /**
         * 关联的数据ID
         */
        private String refId;
        /**
         * 关联的数据类型
         */
        private VersionRefType refType;

        /**
         * 版本号
         */
        private String version;

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            ID id = (ID) o;
            return Objects.equals(refId, id.refId) && refType == id.refType && Objects.equals(version, id.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(refId, refType, version);
        }
    }
}
