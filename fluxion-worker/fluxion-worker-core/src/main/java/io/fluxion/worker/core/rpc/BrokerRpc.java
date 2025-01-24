/*
 * Copyright 2024-2030 fluxion-io Team (https://github.com/fluxion-io).
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

package io.fluxion.worker.core.rpc;


import io.fluxion.remote.core.exception.RegisterFailException;

/**
 * @author Brozen
 * @since 2022-08-30
 */
public interface BrokerRpc {

    /**
     * 向 Broker 注册 Worker
     */
    String register() throws RegisterFailException;


    /**
     * 向 Broker 发送心跳
     */
    void heartbeat();

}
