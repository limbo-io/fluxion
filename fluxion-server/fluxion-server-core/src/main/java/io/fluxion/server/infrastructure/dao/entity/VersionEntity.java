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

package io.fluxion.server.infrastructure.dao.entity;

import io.fluxion.server.infrastructure.dao.TableConstants;
import io.fluxion.server.infrastructure.version.model.VersionRefType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.io.Serializable;

/**
 * 多版本控制
 *
 * @author Devil
 */
@Setter
@Getter
@Table(name = TableConstants.FLUXION_VERSION)
@Entity
@DynamicInsert
@DynamicUpdate
public class VersionEntity extends BaseEntity {

    /**
     * @see VersionRefType
     */
    @EmbeddedId
    private ID id;

    /**
     * 本次版本的描述信息
     */
    private String description;

    /**
     * 配置信息
     */
    private String config;

    @Override
    public Object getUid() {
        return id;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class ID implements Serializable {

        private String refId;

        private int refType;

        private String version;
    }
}
