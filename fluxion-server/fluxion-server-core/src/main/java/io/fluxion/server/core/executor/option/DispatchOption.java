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

package io.fluxion.server.core.executor.option;

import io.fluxion.remote.core.lb.LoadBalanceType;
import io.fluxion.server.infrastructure.tag.TagFilterOption;
import lombok.*;

import java.util.List;

/**
 * 作业分发配置，值对象
 *
 * @author Brozen
 * @since 2021-06-01
 */
@Data
@Setter(AccessLevel.NONE)
@NoArgsConstructor
@AllArgsConstructor
// 如果用下面自己写的构造函数的，字段要按顺序对应
@Builder(builderClassName = "Builder", toBuilder = true)
public class DispatchOption {

    /**
     * 分发方式
     */
    private LoadBalanceType loadBalanceType = LoadBalanceType.RANDOM;

    /**
     * 最大允许的CPU负载（百分比），小于等于0表示不限制。
     * worker的cpuLoad必须 <= maxCpuLoad 才被认为是可用候选者。
     */
    private Double maxCpuLoad;

    /**
     * 兼容旧字段名的别名
     */
    public Double getMaxCpuLoad() {
        return maxCpuLoad != null ? maxCpuLoad : cpuRequirement;
    }

    /**
     * 兼容旧字段名的别名
     */
    public void setMaxCpuLoad(Double maxCpuLoad) {
        this.maxCpuLoad = maxCpuLoad;
    }

    /**
     * @deprecated Use maxCpuLoad instead (语义取反：cpuLoad <= maxCpuLoad)
     */
    @Deprecated
    private Double cpuRequirement;

    /**
     * 最小可用内存(MB)，小于等于0表示不限制。
     * worker的freeMemory必须 >= minFreeMemory 才被认为是可用候选者。
     */
    private Long minFreeMemory;

    /**
     * 兼容旧字段名的别名
     */
    public Long getMinFreeMemory() {
        return minFreeMemory != null ? minFreeMemory : ramRequirement;
    }

    /**
     * 兼容旧字段名的别名
     */
    public void setMinFreeMemory(Long minFreeMemory) {
        this.minFreeMemory = minFreeMemory;
    }

    /**
     * @deprecated Use minFreeMemory instead (语义保持：freeMemory >= minFreeMemory)
     */
    @Deprecated
    private Long ramRequirement;

    /**
     * tag 过滤器配置
     */
    private List<TagFilterOption> tagFilters;

}
