/*
 * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
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
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * @author Brozen
 * @since 2021-06-02
 */
@Setter
@Getter
@Table(name = TableConstants.FLUXION_WORKER_METRIC)
@Entity
@DynamicInsert
@DynamicUpdate
public class WorkerMetricEntity extends BaseEntity {

    @Id
    private String workerId;

    /**
     * cpu 核心数
     */
    private Integer cpuProcessors;

    private Double cpuLoad;

    /**
     * 可用的内存空间，单位字节
     */
    private Long freeMemory;

    /**
     * 任务队列剩余可排队数
     */
    private Integer availableQueueNum;

    /**
     * 上次心跳上报时间戳，毫秒
     */
    private LocalDateTime lastHeartbeatAt;

    @Override
    public Object getUid() {
        return workerId;
    }

}
