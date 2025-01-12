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

import lombok.Getter;

import java.util.function.Supplier;

/**
 * 平台操作相关异常
 *
 * @author Brozen
 * @since 2021-05-14
 */
@Getter
public class PlatformException extends RuntimeException {

    private final int code;

    public PlatformException(ErrorCode code) {
        super(code.message);
        this.code = code.code;
    }

    public PlatformException(ErrorCode code, String message) {
        super(message);
        this.code = code.code;
    }

    public PlatformException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code.code;
    }

    public static Supplier<PlatformException> supplier(ErrorCode code, String message) {
        return () -> new PlatformException(code, message);
    }

    public static Supplier<PlatformException> supplier(ErrorCode code, String message, Throwable cause) {
        return () -> new PlatformException(code, message, cause);
    }

}
