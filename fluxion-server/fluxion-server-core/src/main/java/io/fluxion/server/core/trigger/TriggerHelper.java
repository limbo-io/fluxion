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

import io.fluxion.common.utils.MD5Utils;
import io.fluxion.common.utils.json.JacksonUtils;
import io.fluxion.server.infrastructure.schedule.ScheduleOption;

/**
 * @author Devil
 */
public class TriggerHelper {

    public static String scheduleId(String triggerId) {
        return triggerId + "_tg";
    }

    public static String scheduleVersion(String refId, int refType, ScheduleOption scheduleOption) {
        return MD5Utils.md5(refId + "_" + refType + "_" + JacksonUtils.toJSONString(scheduleOption));
    }
}
