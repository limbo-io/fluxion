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

package io.fluxion.core.executor.data;

import io.fluxion.core.tag.TagFilterOption;
import io.fluxion.remote.lb.LoadBalanceType;
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
     * 所需的CPU核心数，小于等于0表示此作业未定义CPU需求。在分发作业时，会根据此方法返回的CPU核心需求数量来检测一个worker是否有能力执行此作业。
     */
    private Float cpuRequirement;

    /**
     * 所需的内存MB数，小于等于0表示此作业未定义内存需求。在分发作业时，会根据此方法返回的内存需求数量来检测一个worker是否有能力执行此作业。
     */
    private Long ramRequirement;

    /**
     * tag 过滤器配置
     */
    private List<TagFilterOption> tagFilters;

}
