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

package io.fluxion.core.dao.entity;

import io.fluxion.core.dao.TableConstants;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * @author Devil
 * @since 2022/7/18
 */
@Setter
@Getter
@Table(name = TableConstants.FLUXION_BROKER)
@Entity
@DynamicInsert
@DynamicUpdate
public class BrokerEntity extends BaseEntity {

    @Id
    private String brokerId;

    private String name;

    /**
     * 服务使用的通信协议
     */
    private String protocol;

    private String host;

    private Integer port;

    /**
     * 上线时间
     */
    private LocalDateTime onlineTime;

    /**
     * 上次心跳时间
     */
    private LocalDateTime lastHeartbeat;

    @Override
    public Object getUid() {
        return brokerId;
    }
}
