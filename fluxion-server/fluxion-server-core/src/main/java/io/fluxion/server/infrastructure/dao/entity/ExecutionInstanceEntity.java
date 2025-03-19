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

import io.fluxion.server.core.execution.ExecutableType;
import io.fluxion.server.core.execution.ExecutionStatus;
import io.fluxion.server.core.trigger.TriggerType;
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
 * 一次执行的记录 -- 重试
 *
 * @author Devil
 * @since 2021/9/1
 */
//@Setter
//@Getter
//@Table(name = TableConstants.FLUXION_EXECUTION)
//@Entity
//@DynamicInsert
//@DynamicUpdate
public class ExecutionInstanceEntity extends BaseEntity {

    @Id
    private String executionId;

    private String triggerId;

    /**
     * @see TriggerType
     */
    private String triggerType;

    private String executableId;

    /**
     * @see ExecutableType
     */
    private String executableType;

    private String executableVersion;

    /**
     * 状态
     *
     * @see ExecutionStatus
     */
    private String status;

    /**
     * 期望的调度触发时间
     */
    private LocalDateTime triggerAt;

    /**
     * 执行开始时间
     */
    private LocalDateTime startAt;

    /**
     * 执行结束时间
     */
    private LocalDateTime endAt;

    @Override
    public Object getUid() {
        return executionId;
    }
}
