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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import java.io.Serializable;

/**
 * 一个任务运行记录
 *
 * @author Devil
 * @since 2021/9/1
 */
//@Setter
//@Getter
//@Table(name = TableConstants.FLUXION_TASK_RECORD)
//@Entity
//@DynamicInsert
//@DynamicUpdate
public class TaskRecordEntity extends BaseEntity {

    @EmbeddedId
    private ID id;

    /**
     * 开始时间
     */
    private Long startAt;

    /**
     * 结束时间
     */
    private Long endAt;

    /**
     * 下发节点
     */
    private String brokerUrl;

    /**
     * 执行节点
     */
    private String workerAddress;

    /**
     * 错误信息
     */
    private String errorMsg;

    @Override
    public Object getUid() {
        return id;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Embeddable
    public static class ID implements Serializable {

        private String taskId;

        /**
         * 第几次执行
         */
        private int time;
    }
}
