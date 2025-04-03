/*
 * Copyright 2020-2024 Limbo Team (https://github.com/limbo-world).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxion.worker.spring.starter.properties;

import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.remote.core.constants.WorkerRemoteConstant;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URL;
import java.time.Duration;
import java.util.List;

/**
 * @author Brozen
 * @since 2022-09-05
 */
@ConfigurationProperties(prefix = "fluxion.worker")
public class WorkerProperties {

    /**
     * 是否启用 worker
     */
    private boolean enabled = true;

    /**
     * 应用名
     */
    private String appName;

    /**
     * broker 节点连接列表
     */
    private List<URL> brokers;

    /**
     * worker 注册时，向 broker 提交的 RPC 通信协议类型。默认为 http。
     */
    private Protocol protocol = Protocol.HTTP;

    /**
     * worker 注册时，向 broker 提交的 RPC 通信 host，可以是域名或 IP 地址，如不填写则自动发现本机非 127.0.0.1 的地址。
     * 多网卡场景下，建议显式配置 host。
     */
    private String host = "";

    /**
     * worker 注册时，向 broker 提交的 RPC 通信端口，默认为 9787
     */
    private Integer port;

    /**
     * Worker 向 Broker 发送心跳请求的间隔
     */
    private Duration heartbeat = Duration.ofSeconds(WorkerRemoteConstant.HEARTBEAT_TIMEOUT_SECOND);

    /**
     * 任务执行并发数量。worker 将允许同时执行的任务个数，同时执行的任务数量超出此限制后，后续接收的任务将放入积压队列中。默认为系统 CPU 核数。
     */
    private int concurrency = Runtime.getRuntime().availableProcessors();

    /**
     * 任务积压队列容量。如积压队列已满，则 worker 无法继续接收任务。为0情况下队列数大小等于任务执行并发数。
     */
    private int queueSize = 0;

    /**
     * worker 节点标签，可用于下发任务时进行过滤
     * k=v 格式的 tag 表达式，如果 k 或 v 为空，则 tag 不会被添加。
     */
    private List<String> tags;

    private DatasourceProperties datasource;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<URL> getBrokers() {
        return brokers;
    }

    public void setBrokers(List<URL> brokers) {
        this.brokers = brokers;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Duration getHeartbeat() {
        return heartbeat;
    }

    public void setHeartbeat(Duration heartbeat) {
        this.heartbeat = heartbeat;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public DatasourceProperties getDatasource() {
        return datasource;
    }

    public void setDatasource(DatasourceProperties datasource) {
        this.datasource = datasource;
    }
}
