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

import io.fluxion.remote.core.lb.LBServerStatisticsProvider;
import io.fluxion.remote.core.lb.LoadBalanceType;
import io.fluxion.remote.core.lb.strategies.*;
import lombok.Setter;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@link WorkerSelector} 工厂
 *
 * @author Brozen
 * @since 2021-05-18
 */
public class WorkerSelectorFactory {

    /**
     * 用于获取 LB 服务的统计信息，LRU、LFU 算法会用到。
     * 如果确认不使用 LRU、LFU 算法，可以不设置此属性
     */
    @Setter
    private LBServerStatisticsProvider<WorkerLBStatistics> lbServerStatisticsProvider = (sids, interval) -> Collections.emptyList();

    private final Map<LoadBalanceType, Supplier<WorkerSelector>> selectors = new EnumMap<>(LoadBalanceType.class);

    public WorkerSelectorFactory() {
        selectors.put(LoadBalanceType.RANDOM, () -> new LBStrategyWorkerSelector(new RandomLBStrategy<>()));
        selectors.put(LoadBalanceType.ROUND_ROBIN, () -> new LBStrategyWorkerSelector(new RoundRobinLBStrategy<>()));
        selectors.put(LoadBalanceType.LEAST_FREQUENTLY_USED, () -> new LBStrategyWorkerSelector(new LFULBStrategy<>(this.lbServerStatisticsProvider)));
        selectors.put(LoadBalanceType.LEAST_RECENTLY_USED, () -> new LBStrategyWorkerSelector(new LRULBStrategy<>(this.lbServerStatisticsProvider)));
        selectors.put(LoadBalanceType.APPOINT, () -> new LBStrategyWorkerSelector(new AppointLBStrategy<>()));
        selectors.put(LoadBalanceType.CONSISTENT_HASH, () -> new LBStrategyWorkerSelector(new ConsistentHashLBStrategy<>()));
    }

    /**
     * 根据作业的分发方式，创建一个分发器实例。委托给{@link LoadBalanceType}执行。
     *
     * @param loadBalanceType 分发类型
     * @return 作业分发器
     */
    public WorkerSelector newSelector(LoadBalanceType loadBalanceType) {
        return Optional.ofNullable(selectors.get(loadBalanceType))
            .map(Supplier::get)
            .orElseThrow(() -> new IllegalArgumentException("unknown load balance type: " + loadBalanceType));
    }

}
