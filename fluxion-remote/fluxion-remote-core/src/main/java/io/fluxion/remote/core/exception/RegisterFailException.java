/*
 *
 *  * Copyright 2020-2024 fluxion Team (https://github.com/fluxion-io).
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

package io.fluxion.remote.core.exception;

/**
 * @author Brozen
 * @since 2022-08-30
 */
public class RegisterFailException extends RpcException {

    private static final long serialVersionUID = 1497833814754746853L;

    public RegisterFailException() {
    }

    public RegisterFailException(String message) {
        super(message);
    }

    public RegisterFailException(String message, Throwable cause) {
        super(message, cause);
    }

}
