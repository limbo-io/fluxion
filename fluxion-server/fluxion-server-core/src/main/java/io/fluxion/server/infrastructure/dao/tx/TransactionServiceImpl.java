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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.annotation.Resource;
import java.util.function.Supplier;

/**
 * @author Devil
 * @since 2023/12/29
 */
@Slf4j
@Service
public class TransactionServiceImpl implements TransactionService {

    @Resource
    private PlatformTransactionManager transactionManager;

    @Override
    public <T> T transactional(Supplier<T> supplier) {
        return transactional(supplier, null);
    }

    @Override
    public <T> T transactional(Supplier<T> supplier, TransactionDefinition transactionDefinition) {
        TransactionStatus transaction = begin(transactionDefinition);
        try {
            T result = supplier.get();
            commitOrRollback(transaction);
            return result;
        } catch (Exception e) {
            rollback(transaction, e);
            throw e;
        }
    }

    @Override
    public void transactional(Runnable runnable) {
        transactional(runnable, null);
    }

    @Override
    public void transactional(Runnable runnable, TransactionDefinition transactionDefinition) {
        TransactionStatus transaction = begin(transactionDefinition);
        try {
            runnable.run();
            commitOrRollback(transaction);
        } catch (Exception e) {
            rollback(transaction, e);
            throw e;
        }
    }

    // 开启事务
    private TransactionStatus begin(TransactionDefinition transactionDefinition) {
        if (transactionDefinition == null) {
            transactionDefinition = new DefaultTransactionDefinition();
        }

        return transactionManager.getTransaction(transactionDefinition);
    }

    private void commitOrRollback(TransactionStatus transaction) {
        if (transaction.isRollbackOnly()) {
            rollback(transaction, null);
        } else {
            commit(transaction);
        }
    }

    //提交事务
    private void commit(TransactionStatus transaction) {
        if (transaction.isNewTransaction() && !transaction.isCompleted()) {
            transactionManager.commit(transaction);
        }
    }

    //回滚事务
    private void rollback(TransactionStatus transaction, Exception e) {
        if (transaction.isNewTransaction() && !transaction.isCompleted()) {
            transactionManager.rollback(transaction);
            if (e != null) {
                log.error("Transaction error and rollback", e);
            }
        }
    }

}
