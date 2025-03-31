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

package io.fluxion.server.core.worker;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.fluxion.common.constants.CommonConstants;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.remote.core.lb.LBServer;
import io.fluxion.server.core.worker.executor.WorkerExecutor;
import io.fluxion.server.core.worker.metric.WorkerMetric;
import io.fluxion.server.infrastructure.tag.Tag;
import io.fluxion.server.infrastructure.tag.Tagged;
import lombok.Getter;

import java.util.List;

/**
 * @author Devil
 */
@Getter
public class Worker implements LBServer, Tagged {

    private String appId;

    private Protocol protocol;

    private String host;

    private int port;

    private String address;

    /**
     * 执行器
     */
    private List<WorkerExecutor> executors;

    /**
     * 标签
     */
    private List<Tag> tags;

    /**
     * Worker 状态指标
     */
    private WorkerMetric metric;

    private Status status;

    /**
     * 是否启用 不启用则无法下发任务
     */
    private boolean enabled;

    public Worker(String appId, String host, int port, Protocol protocol,
                  List<WorkerExecutor> executors, List<Tag> tags, WorkerMetric metric,
                  Status status, boolean enabled) {
        this.appId = appId;
        this.host = host;
        this.port = port;
        this.protocol = protocol;
        this.address = host + ":" + port;
        this.executors = executors;
        this.tags = tags;
        this.metric = metric;
        this.status = status;
        this.enabled = enabled;
    }

    public String id() {
        return address;
    }

    @Override
    public List<Tag> tags() {
        return tags;
    }

    @Override
    public boolean isAlive() {
        return Status.ONLINE == status;
    }

    @Override
    public Protocol protocol() {
        return protocol;
    }

    @Override
    public String host() {
        return host;
    }

    @Override
    public int port() {
        return port;
    }

    /**
     * Worker的状态
     */
    public enum Status {

        UNKNOWN(CommonConstants.UNKNOWN),

        ONLINE("online"),
        OFFLINE("offline"),

        ;

        @JsonValue
        public final String status;

        Status(String status) {
            this.status = status;
        }

        public boolean is(String status) {
            return this.status.equals(status);
        }

        /**
         * 解析worker状态
         */
        @JsonCreator
        public static Status parse(String status) {
            for (Status statusEnum : values()) {
                if (statusEnum.is(status)) {
                    return statusEnum;
                }
            }
            return UNKNOWN;
        }

    }
}
