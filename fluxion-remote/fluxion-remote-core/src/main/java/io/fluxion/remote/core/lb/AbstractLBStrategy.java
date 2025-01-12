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

package io.fluxion.remote.core.lb;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * @author Brozen
 */
public abstract class AbstractLBStrategy<S extends LBServer> implements LBStrategy<S> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * {@inheritDoc}
     * @param servers 被负载的服务列表
     * @param invocation 本次调用的上下文信息
     * @return
     */
    @Override
    public Optional<S> select(List<S> servers, Invocation invocation) {
        // 有服务存在，但是如果所有服务都挂了的话，也返回空
        if (CollectionUtils.isEmpty(servers)) {
            return Optional.empty();
        }

        try {
            return doSelect(servers, invocation);
        } catch (Exception e) {
            log.error("[{}] select with error servers={} invocation={}", getClass().getSimpleName(), servers, invocation);
            throw new LoadBalanceException(e);
        }
    }


    /**
     * 从非空列表选取对象。
     * @param servers 被负载的服务列表，可以保证非空。
     * @param invocation 本次调用的上下文信息
     */
    protected abstract Optional<S> doSelect(List<S> servers, Invocation invocation);

}
