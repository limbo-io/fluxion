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

import io.fluxion.server.core.trigger.TriggerRefType;
import io.fluxion.server.infrastructure.dao.TableConstants;
import io.fluxion.server.core.trigger.config.Trigger;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 *
 * @author Devil
 */
@Setter
@Getter
@Table(name = TableConstants.FLUXION_TRIGGER)
@Entity
@DynamicInsert
@DynamicUpdate
public class TriggerEntity extends BaseEntity {


    @Id
    private String triggerId;

    /**
     * 触发方式
     * @see Trigger.Type
     */
    private String type;

    private String refId;

    /**
     * @see TriggerRefType
     */
    private int refType;

    /**
     * 描述
     */
    private String description;

    /**
     * 触发配置
     */
    private String config;

    /**
     * 是否启动
     */
    @Column(name = "is_enabled")
    private boolean enabled;

    @Override
    public Object getUid() {
        return triggerId;
    }
}
