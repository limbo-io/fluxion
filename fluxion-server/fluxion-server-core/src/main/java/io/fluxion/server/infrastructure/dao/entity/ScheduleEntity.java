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
import io.fluxion.server.infrastructure.schedule.ScheduleType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * @author Devil
 * @since 2021/7/23
 */
@Setter
@Getter
@Table(name = TableConstants.FLUXION_SCHEDULE)
@Entity
@DynamicInsert
@DynamicUpdate
public class ScheduleEntity extends BaseEntity {

    @Id
    private String scheduleId;

    private Integer bucket;

    /**
     * 计划作业调度方式
     *
     * @see ScheduleType
     */
    private String scheduleType;

    /**
     * 从何时开始调度作业
     */
    private LocalDateTime startTime;

    /**
     * 从何时结束调度作业
     */
    private LocalDateTime endTime;

    /**
     * 作业调度延迟时间，单位秒
     */
    private Long scheduleDelay;

    /**
     * 作业调度间隔时间，单位秒。
     */
    private Long scheduleInterval;

    /**
     * 作业调度的CRON表达式
     */
    private String scheduleCron;

    /**
     * 作业调度的CRON表达式
     */
    private String scheduleCronType;

    /**
     * 上次触发时间
     */
    private LocalDateTime lastTriggerAt;

    /**
     * 上次回调时间
     */
    private LocalDateTime lastFeedbackAt;

    /**
     * 下次触发时间
     */
    private LocalDateTime nextTriggerAt;

    /**
     * 是否启动
     */
    @Column(name = "is_enabled")
    private boolean enabled;

    @Override
    public Object getUid() {
        return scheduleId;
    }
}
