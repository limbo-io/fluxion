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

package io.fluxion.remote.core.server;

/**
 * @author Brozen
 * @since 2022-08-30
 */
public enum RpcServerStatus {

    /**
     * 闲置
     */
    IDLE,
    /**
     * 初始化中
     */
    INITIALIZING,
    /**
     * 运行中
     */
    RUNNING,
    /**
     * 关闭中
     */
    TERMINATING,
    /**
     * 已经关闭
     */
    TERMINATED,

}
