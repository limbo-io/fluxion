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

package io.fluxion.core.cqrs;

import io.fluxion.common.utils.json.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * @author Devil
 * @date 2024/8/2 16:03
 */
@Slf4j
@Component
public class Query implements ApplicationContextAware {

    private static QueryGateway GATEWAY;

    public static <R, Q extends IQuery<R>> R query(Q query, Class<R> responseType) {
        // todo 直接从 IQuery 中获取返回值类型
        CompletableFuture<R> future = GATEWAY.query(query, responseType);
        try {
            return future.get();
        } catch (Exception e) {
            log.error("Query get response error query:{}, type:{}", JacksonUtils.toJSONString(query), responseType.getName(), e);
            throw new RuntimeException(e);
        }
    }

    public static <R, Q extends IQuery<R>> CompletableFuture<R> asyncQuery(Q query, Class<R> responseType) {
        return GATEWAY.query(query, responseType);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        GATEWAY = applicationContext.getBean(QueryGateway.class);
    }

}
