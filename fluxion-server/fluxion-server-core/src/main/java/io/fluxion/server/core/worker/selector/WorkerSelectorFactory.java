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

package io.fluxion.server.core.worker.selector;

import io.fluxion.remote.core.lb.LBServer;
import io.fluxion.remote.core.lb.LBServerStatistics;
import io.fluxion.remote.core.lb.LBServerStatisticsProvider;
import io.fluxion.remote.core.lb.LoadBalanceType;
import io.fluxion.remote.core.lb.strategies.*;
import io.fluxion.server.core.worker.Worker;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@link WorkerSelector} 工厂
 *
 * @author Brozen
 * @since 2021-05-18
 */
public class WorkerSelectorFactory {

    /**
     * 用于获取 LB 服务的统计信息，LRU、LFU 算法会用到
     */
    private final WorkerStatisticsRepository statisticsRepository = new WorkerStatisticsRepository();

    /**
     * 缓存选择器实例，key = appId + executorName + loadBalanceType
     * 保证同一场景下的负载均衡状态可持久化
     */
    private final Map<String, WorkerSelector> selectorCache = new ConcurrentHashMap<>();

    private final Map<LoadBalanceType, Supplier<WorkerSelector>> selectorSuppliers = new EnumMap<>(LoadBalanceType.class);

    public WorkerSelectorFactory() {
        selectorSuppliers.put(LoadBalanceType.RANDOM, () -> new LBStrategyWorkerSelector(new RandomLBStrategy<>()));
        selectorSuppliers.put(LoadBalanceType.ROUND_ROBIN, () -> new LBStrategyWorkerSelector(new RoundRobinLBStrategy<>()));
        selectorSuppliers.put(LoadBalanceType.LEAST_FREQUENTLY_USED, () -> new LBStrategyWorkerSelector(new LFULBStrategy<>(statisticsRepository)));
        selectorSuppliers.put(LoadBalanceType.LEAST_RECENTLY_USED, () -> new LBStrategyWorkerSelector(new LRULBStrategy<>(statisticsRepository)));
        selectorSuppliers.put(LoadBalanceType.APPOINT, () -> new LBStrategyWorkerSelector(new AppointLBStrategy<>()));
        selectorSuppliers.put(LoadBalanceType.CONSISTENT_HASH, () -> new LBStrategyWorkerSelector(new ConsistentHashLBStrategy<>()));
        selectorSuppliers.put(LoadBalanceType.LEAST_CPU_LOAD, () -> new LBStrategyWorkerSelector(new LeastCpuLoadLBStrategy()));
    }

    /**
     * 获取或创建选择器实例（复用同一场景的实例以保持状态）
     *
     * @param appId           应用ID
     * @param executorName    执行器名称
     * @param loadBalanceType 负载均衡类型
     * @return 作业分发器
     */
    public WorkerSelector getOrCreateSelector(String appId, String executorName, LoadBalanceType loadBalanceType) {
        String key = buildSelectorKey(appId, executorName, loadBalanceType);
        return selectorCache.computeIfAbsent(key, k -> newSelector(loadBalanceType));
    }

    /**
     * 创建新的选择器实例（不复用）
     *
     * @param loadBalanceType 分发类型
     * @return 作业分发器
     */
    public WorkerSelector newSelector(LoadBalanceType loadBalanceType) {
        return Optional.ofNullable(selectorSuppliers.get(loadBalanceType))
            .map(Supplier::get)
            .orElseThrow(() -> new IllegalArgumentException("unknown load balance type: " + loadBalanceType));
    }

    /**
     * 获取统计信息仓库
     */
    public WorkerStatisticsRepository getStatisticsRepository() {
        return statisticsRepository;
    }

    /**
     * 按选择器偏好对候选者排序（最佳优先）
     */
    public List<Worker> sortCandidates(LoadBalanceType loadBalanceType, String executorName, List<Worker> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        switch (loadBalanceType) {
            case LEAST_FREQUENTLY_USED:
                return sortByLFU(candidates);
            case LEAST_RECENTLY_USED:
                return sortByLRU(candidates);
            case ROUND_ROBIN:
                // 轮询保留原始顺序，RR策略会维护index状态
                return new ArrayList<>(candidates);
            case RANDOM:
                // 随机打乱
                List<Worker> shuffled = new ArrayList<>(candidates);
                Collections.shuffle(shuffled);
                return shuffled;
            case LEAST_CPU_LOAD:
                // 按CPU负载升序排序（负载低的优先）
                return sortByCpuLoad(candidates);
            default:
                return new ArrayList<>(candidates);
        }
    }

    /**
     * 按LFU排序（使用次数少的优先）
     */
    private List<Worker> sortByLFU(List<Worker> candidates) {
        Set<String> serverIds = candidates.stream().map(LBServer::id).collect(Collectors.toSet());
        List<LBServerStatistics> stats = statisticsRepository.getStatistics(serverIds, Duration.ofMinutes(10));
        Map<String, Integer> accessCounts = stats.stream()
            .collect(Collectors.toMap(LBServerStatistics::serverId, LBServerStatistics::accessTimes));

        return candidates.stream()
            .sorted(Comparator.comparingInt(w -> accessCounts.getOrDefault(w.id(), 0)))
            .collect(Collectors.toList());
    }

    /**
     * 按LRU排序（最久未使用的优先）
     */
    private List<Worker> sortByLRU(List<Worker> candidates) {
        Set<String> serverIds = candidates.stream().map(LBServer::id).collect(Collectors.toSet());
        List<LBServerStatistics> stats = statisticsRepository.getStatistics(serverIds, Duration.ofMinutes(10));
        Map<String, Long> lastAccessTimes = stats.stream()
            .collect(Collectors.toMap(
                LBServerStatistics::serverId,
                s -> s.latestAccessAt() != null ? s.latestAccessAt().toEpochMilli() : 0L
            ));

        return candidates.stream()
            .sorted(Comparator.comparingLong(w -> lastAccessTimes.getOrDefault(w.id(), 0L)))
            .collect(Collectors.toList());
    }

    /**
     * 按CPU负载升序排序（负载低的优先）
     */
    private List<Worker> sortByCpuLoad(List<Worker> candidates) {
        return candidates.stream()
            .sorted(Comparator.comparingDouble(this::getCpuLoad))
            .collect(Collectors.toList());
    }

    /**
     * 获取worker的CPU负载，无指标时返回最大值（排在最后）
     */
    private double getCpuLoad(Worker worker) {
        if (worker.getMetric() == null) {
            return Double.MAX_VALUE;
        }
        return worker.getMetric().getCpuLoad();
    }

    private String buildSelectorKey(String appId, String executorName, LoadBalanceType loadBalanceType) {
        return appId + ":" + executorName + ":" + loadBalanceType.name();
    }

}
