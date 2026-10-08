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
import io.fluxion.server.infrastructure.tag.TagRefType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.io.Serializable;

/**
 * @author Brozen
 * @since 2022-09-22
 */
@Setter
@Getter
@Table(name = TableConstants.FLUXION_TAG)
@Entity
@DynamicInsert
@DynamicUpdate
public class TagEntity extends BaseEntity {

    @EmbeddedId
    private ID id;


    @Override
    public Object getUid() {
        return id;
    }


    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class ID implements Serializable {

        /** 列长度与生产 DDL（V20250101__init.sql uk_tag）对齐：hbm2ddl 默认 varchar(255)
         * 会让复合主键达 4080 bytes，超过 MySQL utf8mb4 的 3072-byte 索引上限，
         * create-drop 在真实 MySQL 上建表失败（Specified key was too long）。 */
        @Column(length = 64)
        private String refId;

        /**
         * @see TagRefType
         */
        @Column(length = 64)
        private String refType;

        /**
         * tag name
         */
        @Column(length = 128)
        private String tagName;

        /**
         * tag value
         */
        @Column(length = 255)
        private String tagValue;
    }
}
