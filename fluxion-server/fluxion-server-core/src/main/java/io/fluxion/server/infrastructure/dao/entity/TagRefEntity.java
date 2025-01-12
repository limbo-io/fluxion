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

import io.fluxion.server.infrastructure.dao.RefID;
import io.fluxion.server.infrastructure.dao.TableConstants;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * @author Brozen
 * @since 2022-09-22
 */
@Setter
@Getter
@Table(name = TableConstants.FLUXION_TAG_REF)
@Entity
@DynamicInsert
@DynamicUpdate
public class TagRefEntity extends BaseEntity {

    @EmbeddedId
    private RefID id;

    /**
     * worker tag key
     */
    private String tagKey;

    /**
     * worker tag value
     */
    private String tagValue;


    @Override
    public Object getUid() {
        return id;
    }

}
