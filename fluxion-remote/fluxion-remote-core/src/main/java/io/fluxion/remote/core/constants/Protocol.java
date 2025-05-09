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

package io.fluxion.remote.core.constants;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 服务的通信协议
 */
public enum Protocol {

    UNKNOWN("unknown", 0),

    /**
     * HTTP协议通信
     */
    HTTP("http", 80),

    ;

    /**
     * 协议名称
     */
    @JsonValue
    public final String value;

    /**
     * 协议默认端口号
     */
    public final int port;

    Protocol(String value, int port) {
        this.value = value;
        this.port = port;
    }


    public boolean is(String protocol) {
        return this.value.equalsIgnoreCase(protocol);
    }


    /**
     * 解析worker支持的协议
     */
    @JsonCreator
    public static Protocol parse(String protocolName) {
        if (protocolName == null) {
            return UNKNOWN;
        }

        for (Protocol protocol : values()) {
            if (protocol.value.equalsIgnoreCase(protocolName)) {
                return protocol;
            }
        }

        return UNKNOWN;
    }

}
