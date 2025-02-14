/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/fluxion-io).
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

package io.fluxion.server.infrastructure.dao.tx;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * @author Devil
 */
@Component
public class TX implements ApplicationContextAware {

    private static TransactionService transactionService;

    /**
     * 包装在事务中的处理
     *
     * @param supplier 处理逻辑
     * @param <T>      返回类型
     * @return 返回数据
     */
    public static <T> T transactional(Supplier<T> supplier) {
        return transactionService.transactional(supplier);
    }

    public static void transactional(Runnable runnable) {
        transactionService.transactional(runnable);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        transactionService = applicationContext.getBean(TransactionService.class);
    }
}
