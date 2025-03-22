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

import io.fluxion.server.core.task.TaskStatus;
import io.fluxion.server.core.task.TaskType;
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
 * 一个任务 实例 存储运行数据
 *
 * @author Devil
 * @since 2021/9/1
 */
@Setter
@Getter
@Table(name = TableConstants.FLUXION_TASK)
@Entity
@DynamicInsert
@DynamicUpdate
public class TaskEntity extends BaseEntity {

    @Id
    private String taskId;

    private String executionId;

    /**
     * @see TaskType
     */
    private String taskType;

    /**
     * 关联的 id
     * flow 中是nodeId
     * executor 则直接为空
     */
    private String refId;

    /**
     * 状态
     *
     * @see TaskStatus
     */
    private String status;

    /**
     * 应该触发的时间
     */
    private LocalDateTime triggerAt;

    /**
     * 开始时间
     */
    private LocalDateTime startAt;

    /**
     * 结束时间
     */
    private LocalDateTime endAt;

    /**
     * 执行节点
     */
    private String workerAddress;

    /**
     * 上次上报时间戳，毫秒
     */
    private LocalDateTime lastReportAt;

    /**
     * 当前是第几次重试
     */
    private Integer retryTimes;

    @Override
    public Object getUid() {
        return taskId;
    }
}
