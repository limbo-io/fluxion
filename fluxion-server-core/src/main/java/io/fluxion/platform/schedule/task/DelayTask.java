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

package io.fluxion.platform.schedule.task;

import io.fluxion.platform.schedule.Executable;

import java.time.LocalDateTime;

/**
 * 延时任务
 * 先创建实例，特定时间触发
 *
 *
 * @author Devil
 */
public class DelayTask extends AbstractTask implements Executable {

    public DelayTask(String id) {
        super(id);
    }

    @Override
    public void execute() {

    }

    @Override
    public LocalDateTime triggerAt() {
        return null;
    }
}
