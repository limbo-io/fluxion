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

package io.fluxion.server.infrastructure.exception;

/**
 * 业务状态码。业务状态码从 100000 开始；
 *
 *
 * 业务状态码由 6 位组成，根据位数从大到小，分为 高两位、中两位、低两位。
 *
 * 以错误码 100001 为例，如下：
 * 10 00 01
 * 高 中 低
 *
 * 可以参考下面格式定义错误码，但不强制要求遵守，这只是个约定：
 * 00 为公用内容
 * 错误码高两位取值范围为 01 ~ 99，可用于区分领域聚合（业务模块，如：日程、参与人、评论、历史记录）；
 * 中两位取值 00~99，可用于标识子业务操作（如：创建日程、删除日程）；
 * 低两位取值 00~99，可用于标识错误类型（如：无权执行操作、业务权限校验不通过）；
 *
 *
 * @author Brozen
 * @since 2024-02-01
 */
public enum ErrorCode {
    // 公用 00 开头
    PARAM_ERROR(400, "param_error"),
    SYSTEM_ERROR(500, "system_error"),
    // 业务定义 01 ~ 99 开头
    ;


    public final int code;
    public final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
