/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/fluxion-works/fluxion-io).
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

package io.fluxion.test.core.lock;

import io.fluxion.server.infrastructure.dao.repository.LockEntityRepo;
import io.fluxion.server.infrastructure.dao.tx.TransactionService;
import io.fluxion.server.infrastructure.lock.DatabaseDistributedLock;
import io.fluxion.server.infrastructure.lock.Locked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.ConnectException;
import java.sql.SQLException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 死锁自愈隔离验证：InnoDB 的死锁牺牲是事务级瞬时错误（牺牲事务被完整回滚），
 * DatabaseDistributedLock 对获取/释放路径按 SQLState 40001 / errorCode 1213 识别并
 * 重放一次；其余异常立即上抛。真实 MySQL 下环的形成窗口为毫秒级锁排队交错
 * （30 轮 10 线程探针 0 复现），因此自愈逻辑以单元边界固定行为，不依赖概率复现。
 */
@DisplayName("DatabaseDistributedLock deadlock self-healing")
class DatabaseDistributedLockDeadlockRetryTest {

    private LockEntityRepo lockEntityRepo;
    private DatabaseDistributedLock distributedLock;

    @BeforeEach
    void setUp() {
        lockEntityRepo = mock(LockEntityRepo.class);
        TransactionService txService = new TransactionService() {
            @Override
            public <T> T transactional(Supplier<T> supplier) {
                return supplier.get();
            }

            @Override
            public <T> T transactional(Supplier<T> supplier, org.springframework.transaction.TransactionDefinition transactionDefinition) {
                return supplier.get();
            }

            @Override
            public void transactional(Runnable runnable) {
                runnable.run();
            }

            @Override
            public void transactional(Runnable runnable, org.springframework.transaction.TransactionDefinition transactionDefinition) {
                runnable.run();
            }
        };
        distributedLock = new DatabaseDistributedLock();
        ReflectionTestUtils.setField(distributedLock, "lockEntityRepo", lockEntityRepo);
        ReflectionTestUtils.setField(distributedLock, "transactionService", txService);
    }

    @Test
    @DisplayName("获取路径：死锁(40001)重放一次后成功")
    void acquireRetriesOnceOnDeadlock() {
        when(lockEntityRepo.tryAcquireLock(anyString(), anyString(), any()))
            .thenThrow(deadlock())
            .thenReturn(1);

        Locked locked = distributedLock.tryAcquireLock("lock-a", 60_000);

        assertThat(locked).isNotNull();
        verify(lockEntityRepo, times(2)).tryAcquireLock(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("获取路径：非死锁异常立即上抛，不重试")
    void acquirePropagatesNonDeadlockImmediately() {
        when(lockEntityRepo.tryAcquireLock(anyString(), anyString(), any()))
            .thenThrow(new DataAccessResourceFailureException("connection lost"));

        assertThatThrownBy(() -> distributedLock.tryAcquireLock("lock-a", 60_000))
            .isInstanceOf(DataAccessResourceFailureException.class);
        verify(lockEntityRepo, times(1)).tryAcquireLock(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("获取路径：连续两次死锁（概率≈单次的平方）上抛交由调用方语义收敛")
    void acquirePropagatesAfterSecondDeadlock() {
        when(lockEntityRepo.tryAcquireLock(anyString(), anyString(), any()))
            .thenThrow(deadlock())
            .thenThrow(deadlock());

        assertThatThrownBy(() -> distributedLock.tryAcquireLock("lock-a", 60_000))
            .hasRootCauseInstanceOf(SQLException.class);
        verify(lockEntityRepo, times(2)).tryAcquireLock(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("释放路径：解锁 DELETE 死锁(1213)重放一次后成功（finally 语义不被瞬态错误破坏）")
    void unlockRetriesOnceOnDeadlock() {
        when(lockEntityRepo.tryAcquireLock(anyString(), anyString(), any())).thenReturn(1);
        assertThat(distributedLock.tryLock("lock-b", 60_000)).isTrue();

        when(lockEntityRepo.deleteByNameAndOwner(anyString(), anyString()))
            .thenThrow(deadlock(1213))
            .thenReturn(1);

        assertThat(distributedLock.unlock("lock-b")).isTrue();
        verify(lockEntityRepo, times(2)).deleteByNameAndOwner(anyString(), anyString());
    }

    @Test
    @DisplayName("释放路径：重放仍无匹配行为 owner 时不影响语义（幂等确认失败）")
    void unlockRetryStillReturnsFalseWhenNotOwner() {
        when(lockEntityRepo.tryAcquireLock(anyString(), anyString(), any())).thenReturn(1);
        assertThat(distributedLock.tryLock("lock-c", 60_000)).isTrue();

        when(lockEntityRepo.deleteByNameAndOwner(anyString(), anyString()))
            .thenThrow(deadlock(1213))
            .thenReturn(0);

        assertThat(distributedLock.unlock("lock-c")).isFalse();
        verify(lockEntityRepo, times(2)).deleteByNameAndOwner(anyString(), anyString());
    }

    @Test
    @DisplayName("释放路径：埋深原因链的死锁亦可识别（层间包装不丢失 40001）")
    void unlockDetectsWrappedDeadlockInCauseChain() {
        when(lockEntityRepo.tryAcquireLock(anyString(), anyString(), any())).thenReturn(1);
        assertThat(distributedLock.tryLock("lock-d", 60_000)).isTrue();

        when(lockEntityRepo.deleteByNameAndOwner(anyString(), anyString()))
            .thenThrow(new DeadlockLoserDataAccessException("deadlock", deadlock()))
            .thenReturn(1);

        assertThat(distributedLock.unlock("lock-d")).isTrue();
    }

    @Test
    @DisplayName("释放路径：非死锁的连接类异常不重试（不能掩盖真实故障）")
    void unlockPropagatesNonDeadlockImmediately() {
        when(lockEntityRepo.tryAcquireLock(anyString(), anyString(), any())).thenReturn(1);
        assertThat(distributedLock.tryLock("lock-e", 60_000)).isTrue();

        when(lockEntityRepo.deleteByNameAndOwner(anyString(), anyString()))
            .thenThrow(new DataAccessResourceFailureException("connection lost", new ConnectException("refused")));

        assertThatThrownBy(() -> distributedLock.unlock("lock-e"))
            .isInstanceOf(DataAccessResourceFailureException.class);
        verify(lockEntityRepo, times(1)).deleteByNameAndOwner(anyString(), anyString());
    }

    // ---- fixtures ----

    /** Spring 翻译后的死锁形态：DeadlockLoserDataAccessException，cause 链携带 SQLState 40001 的 SQLException。
     * repo 方法不声明 checked 异常，Mockito 只接受 RuntimeException；生产路径中 SQLException
     * 也总是经 Hibernate/Spring 翻译后到达，此形态与真实一致。 */
    private static RuntimeException deadlock() {
        return deadlock(0);
    }

    private static RuntimeException deadlock(int errorCode) {
        SQLException sqlEx = new SQLException(
            "Deadlock found when trying to get lock; try restarting transaction", "40001", errorCode);
        return new DeadlockLoserDataAccessException("deadlock", sqlEx);
    }
}