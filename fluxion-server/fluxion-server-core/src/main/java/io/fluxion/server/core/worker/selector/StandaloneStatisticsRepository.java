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

import io.fluxion.common.utils.concurrent.Lockable;
import io.fluxion.remote.core.lb.LBServer;
import io.fluxion.remote.core.lb.LBServerStatistics;
import io.fluxion.remote.core.lb.LBServerStatisticsProvider;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
public abstract class StandaloneStatisticsRepository<T extends LBServerStatistics> extends Lockable<List<StandaloneStatisticsRepository.StatisticsRecord>> implements LBServerStatisticsProvider {

    /**
     * 最久统计多长时间的数据，默认 12H。
     */
    private Duration maxStatisticDuration = Duration.ofHours(12);

    /**
     * 进行一次记录
     */
    public void record(LBServer lbServer) {
        runInWriteLock(records -> {
            // 新增记录
            records.add(new StatisticsRecord(
                lbServer.serverId(), Instant.now()
            ));

            // 同时检测头部 10 条记录是否过时，如过时需要移除
            int maxDetectItems = 10;
            Instant expiresLimit = Instant.now().plusSeconds(-maxStatisticDuration.getSeconds());
            Iterator<StatisticsRecord> iterator = records.iterator();
            for (int i = 0; i < maxDetectItems; i++) {
                if (!iterator.hasNext()) {
                    break;
                }

                StatisticsRecord r = iterator.next();
                if (r.dispatchAt.isBefore(expiresLimit)) {
                    iterator.remove();
                } else {
                    break;
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * @param serverIds 服务 ID 结合
     * @param interval  查询的统计信息时长
     * @return
     */
    @Override
    public List<LBServerStatistics> getStatistics(Set<String> serverIds, Duration interval) {
        Instant limit = Instant.now().plusSeconds(-interval.getSeconds());
        // 读取所有统计数据
        Map<String, MutableLBStatistics> statistics = new HashMap<>();
        runInReadLock(records -> {
            for (int i = records.size() - 1; i >= 0; i--) {
                StatisticsRecord record = records.get(i);
                if (serverIds.contains(record.serviceId) && record.dispatchAt.isAfter(limit)) {
                    MutableLBStatistics lbStatistics = statistics.computeIfAbsent(record.serviceId, MutableLBStatistics::new);
                    lbStatistics.statisticRecord(record);
                }
            }

            for (String serverId : serverIds) {
                statistics.computeIfAbsent(serverId, MutableLBStatistics::new);
            }

        });

        return statistics.values().stream()
            .map(this::map)
            .collect(Collectors.toList());
    }

    public abstract T map(MutableLBStatistics mutableLBStatistics);

    @AllArgsConstructor
    public static class StatisticsRecord {
        private String serviceId;
        /**
         * 下发时间点
         */
        private Instant dispatchAt;
    }

    @Getter
    public static class MutableLBStatistics {
        private final String serviceId;
        private Instant lastDispatchTaskAt;
        private int dispatchTimes;

        MutableLBStatistics(String serviceId) {
            this.serviceId = serviceId;
        }

        void statisticRecord(StatisticsRecord statisticsRecord) {
            if (this.lastDispatchTaskAt == null || this.lastDispatchTaskAt.isBefore(statisticsRecord.dispatchAt)) {
                this.lastDispatchTaskAt = statisticsRecord.dispatchAt;
            }

            this.dispatchTimes++;
        }

    }

}
