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
import io.fluxion.core.schedule.ScheduleType;
import io.fluxion.core.trigger.Trigger;
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
 * @since 2021/7/23
 */
@Setter
@Getter
@Table(name = TableConstants.FLUXION_SCHEDULE_TASK)
@Entity
@DynamicInsert
@DynamicUpdate
public class ScheduleTaskEntity extends BaseEntity {

    @Id
    private String scheduleTaskId;
    /**
     * 用来判断 调度配置是否有变化
     */
    private String version;

    private String refId;

    /**
     * @see Trigger.RefType
     */
    private String refType;

    /**
     * 分配的节点 ip:host
     */
    private String brokerUrl;

    /**
     * 计划作业调度方式
     * @see ScheduleType
     */
    private String scheduleType;

    /**
     * 从何时开始调度作业
     */
    private LocalDateTime scheduleStartAt;

    /**
     * 从何时结束调度作业
     */
    private LocalDateTime scheduleEndAt;

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
    private LocalDateTime latelyTriggerAt;

    /**
     * 上次回调时间
     */
    private LocalDateTime latelyFeedbackAt;

    /**
     * 下次触发时间
     */
    private LocalDateTime nextTriggerAt;

    /**
     * 是否启动
     */
    private boolean enabled;

    @Override
    public Object getUid() {
        return scheduleTaskId;
    }

}
