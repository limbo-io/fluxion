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

package io.fluxion.worker.spring.starter.configuration;

import io.fluxion.remote.core.cluster.BaseNode;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.remote.core.lb.BaseLBServer;
import io.fluxion.remote.core.lb.LBServer;
import io.fluxion.remote.core.utils.NetUtils;
import io.fluxion.worker.core.Worker;
import io.fluxion.worker.core.persistence.ConnectionFactory;
import io.fluxion.worker.core.persistence.H2ConnectionFactory;
import io.fluxion.worker.core.task.repository.DBTaskRepository;
import io.fluxion.worker.core.task.repository.TaskRepository;
import io.fluxion.worker.spring.starter.SpringDelegatedWorker;
import io.fluxion.worker.spring.starter.processor.ExecutorMethodProcessor;
import io.fluxion.worker.spring.starter.properties.DatasourceProperties;
import io.fluxion.worker.spring.starter.properties.WorkerProperties;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

/**
 * @author Devil
 */
@Configuration
@ConditionalOnProperty(prefix = "fluxion.worker", value = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WorkerProperties.class)
public class FluxionWorkerAutoConfiguration {

    private final WorkerProperties properties;

    private static final Integer DEFAULT_HTTP_SERVER_PORT = 9787;

    public FluxionWorkerAutoConfiguration(WorkerProperties properties) {
        this.properties = properties;
    }

    @Value("${spring.application.name}")
    private String springAppName;

    @Bean("fluxionExecutorMethodProcessor")
    public ExecutorMethodProcessor executorMethodProcessor() {
        return new ExecutorMethodProcessor();
    }

    @Bean("fluxionTaskRepository")
    @ConditionalOnMissingBean(TaskRepository.class)
    public TaskRepository taskRepository() throws SQLException {
        DatasourceProperties datasource = properties.getDatasource();
        ConnectionFactory connectionFactory = new H2ConnectionFactory(datasource.getUrl(), datasource.getUsername(), datasource.getPassword());
        DBTaskRepository taskRepository = new DBTaskRepository(connectionFactory);
        // 先放这里了后面考虑生命周期
        if (properties.getDatasource().isInitTable()) {
            taskRepository.initTable();
        }
        return taskRepository;
    }

    /**
     * Worker 实例
     */
    @Bean("fluxionWorker")
    public Worker worker(WorkerProperties properties, TaskRepository taskRepository) throws MalformedURLException {
        // AppName 优先使用worker配置，否则使用spring配置
        String appName = StringUtils.isBlank(properties.getAppName()) ? springAppName : properties.getAppName();
        Assert.isTrue(StringUtils.isNotBlank(appName), "Worker appName must not blank");

        // port
        Integer port = properties.getPort() != null ? properties.getPort() : DEFAULT_HTTP_SERVER_PORT;
        Assert.isTrue(port > 0, "Worker port must be a positive integer in range 1 ~ 65534");

        // 优先使用指定的 host，如未指定则自动寻找本机 IP
        String host = properties.getHost();
        if (StringUtils.isEmpty(host)) {
            host = NetUtils.getLocalIp();
        }

        // tags
        Map<String, Set<String>> tags = tags(properties);

        // brokers
        List<LBServer> brokerNodes = brokerNodes(properties);

        // worker
        return new SpringDelegatedWorker(
            appName, properties.getProtocol(), host, port,
            properties.getQueueSize(), properties.getConcurrency(), tags,
            taskRepository, brokerNodes
        );
    }

    private Map<String, Set<String>> tags(WorkerProperties properties) {
        if (CollectionUtils.isEmpty(properties.getTags())) {
            return Collections.emptyMap();
        }

        return properties.getTags().stream().map(s -> {
            String[] sp = s.split("=");
            if (sp.length < 2 || StringUtils.isAnyBlank(sp)) {
                return null;
            }
            return Pair.of(sp[0], sp[1]);
        }).filter(Objects::nonNull).collect(Collectors.groupingBy(Pair::getKey, mapping(Pair::getValue, toSet())));
    }

    private List<LBServer> brokerNodes(WorkerProperties properties) {
        List<URL> brokerUrls = properties.getBrokers() == null ? Collections.emptyList() : properties.getBrokers();
        return brokerUrls.stream()
            .map(url -> {
                BaseNode node = new BaseNode(Protocol.parse(url.getProtocol()), url.getHost(), url.getPort());
                return new BaseLBServer(node);
            })
            .collect(Collectors.toList());
    }

}
