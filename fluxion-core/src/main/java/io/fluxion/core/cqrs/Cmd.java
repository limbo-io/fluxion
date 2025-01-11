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
import org.axonframework.commandhandling.gateway.CommandGateway;
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
public class Cmd implements ApplicationContextAware {

    private static CommandGateway GATEWAY;

    public static <R, T extends ICmd<R>> R send(T cmd) {
        CompletableFuture<R> future = asyncSend(cmd);
        try {
            return future.get();
        } catch (Exception e) {
            log.error("Cmd get response error cmd:{}", JacksonUtils.toJSONString(cmd), e);
            throw new RuntimeException(e);
        }
    }

    public static <R, T extends ICmd<R>> CompletableFuture<R> asyncSend(T cmd) {
        return GATEWAY.send(cmd);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        GATEWAY = applicationContext.getBean(CommandGateway.class);
    }
}
