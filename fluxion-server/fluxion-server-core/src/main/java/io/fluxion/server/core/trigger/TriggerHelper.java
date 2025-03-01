/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/fluxion-io).
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

package io.fluxion.server.core.trigger;

import io.fluxion.common.thread.CommonThreadPool;
import io.fluxion.common.utils.MD5Utils;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.execution.Execution;
import io.fluxion.server.core.execution.cmd.ExecutionCreateCmd;
import io.fluxion.server.core.trigger.cmd.ScheduleRefreshLastTriggerCmd;
import io.fluxion.server.core.trigger.config.TriggerConfig;
import io.fluxion.server.core.trigger.query.ScheduleByIdQuery;
import io.fluxion.server.core.trigger.run.Schedule;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;
import io.fluxion.server.infrastructure.schedule.task.AbstractTask;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * @author Devil
 */
@Slf4j
public class TriggerHelper {

    /**
     * 触发器对应的schedule的id
     */
    public static String scheduleId(String triggerId) {
        return triggerId + "_tg";
    }

    public static String scheduleVersion(String refId, int refType, ScheduleOption scheduleOption) {
        return MD5Utils.md5(refId + "_" + refType + "_" + JacksonUtils.toJSONString(scheduleOption));
    }

    /**
     * 放入 TaskScheduler 中的task的id
     */
    public static String taskScheduleId(Schedule schedule) {
        // entity = trigger 所以不会变，这里使用version判断是否有版本变动
        return schedule.getId() + "_" + schedule.getVersion() + "_sch";
    }

    public static void consumerTask(AbstractTask task, String scheduleId) {
        // 移除不需要调度的
        Schedule schedule = Query.query(new ScheduleByIdQuery(scheduleId)).getSchedule();
        if (!schedule.isEnabled()) {
            log.info("Schedule is not enabled id:{}", scheduleId);
            task.stop();
            return;
        }
        // 非当前节点的，可能重新分配给其他了
        if (!Objects.equals(BrokerContext.broker().id(), schedule.getBrokerId())) {
            log.info("Schedule is not schedule by current broker scheduleId:{} brokerId:{} currentBrokerId:{}",
                scheduleId, schedule.getBrokerId(), BrokerContext.broker().id()
            );
            task.stop();
            return;
        }
        // 版本变化了老的可以不用执行
        String taskScheduleId = TriggerHelper.taskScheduleId(schedule);
        if (!task.id().equals(taskScheduleId)) {
            log.info("Schedule version is change id:{} taskScheduleId:{} taskId:{}",
                scheduleId, taskScheduleId, task.id()
            );
            task.stop();
            return;
        }
        // 更新上次触发时间
        Cmd.send(new ScheduleRefreshLastTriggerCmd(
            schedule.getId(), task.triggerAt()
        ));
        // 创建执行记录
        Execution execution = Cmd.send(new ExecutionCreateCmd(
            schedule.getTriggerId(),
            Trigger.Type.SCHEDULE,
            schedule.getRefId(),
            schedule.getRefType(),
            task.triggerAt()
        )).getExecution();
        // 异步执行
        CommonThreadPool.IO.submit(execution::execute);
    }

}
