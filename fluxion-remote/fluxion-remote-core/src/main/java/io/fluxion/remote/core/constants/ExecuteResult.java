/*
 *
 *  * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * 	http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package io.fluxion.remote.core.constants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;

/**
 * 执行结果
 *
 * @author Brozen
 * @since 2021-07-07
 */
public enum ExecuteResult {

    UNKNOWN(CommonConstants.UNKNOWN),
    /**
     * 执行成功
     */
    SUCCEED("succeed"),
    /**
     * 部分成功
     */
    PARTIALLY_SUCCESSFUL("partially_successful"),
    /**
     * 执行异常
     */
    FAILED("failed"),
    /**
     * 手动终止
     */
    TERMINATED("terminated"),

    ;

    /**
     * 执行结果枚举值
     */
    @JsonValue
    public final String result;

    ExecuteResult(String result) {
        this.result = result;
    }


    /**
     * 校验是否当前结果
     * @param result 待校验值
     */
    public boolean is(String result) {
        return this.result.equals(result);
    }


    /**
     * 解析作业执行结果
     * @param result 待解析值
     * @return 解析失败返回null
     */
    @JsonCreator
    public static ExecuteResult parse(String result) {
        for (ExecuteResult _result : values()) {
            if (_result.is(result)) {
                return _result;
            }
        }
        return UNKNOWN;
    }
}
