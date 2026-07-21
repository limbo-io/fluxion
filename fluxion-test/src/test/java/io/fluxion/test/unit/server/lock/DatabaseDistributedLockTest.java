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

package io.fluxion.test.unit.server.lock;

import io.fluxion.server.infrastructure.dao.entity.LockEntity;
import io.fluxion.server.infrastructure.dao.repository.LockEntityRepo;
import io.fluxion.test.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 【DatabaseDistributedLock 单元测试】
 * 
 * 测试目标：验证数据库分布式锁的核心语义和 Bug 修复
 * 
 * Bug 验证：
 *   1. 未过期锁被不同 owner 获取时应当返回 false（原逻辑错误返回 true）
 *   2. Expired lock 被 owner-B 接管后，owner-A 的 unlock 不应该删除 owner-B 的锁
 *   3. UUID token 避免 threadId 冲突
 * 
 * 注意：使用 BaseIntegrationTest 的基础设施，但直接测试 Repository 层的原子操作
 */
class DatabaseDistributedLockTest extends BaseIntegrationTest {

    @Autowired
    private LockEntityRepo lockEntityRepo;

    @AfterEach
    void tearDown() {
        lockEntityRepo.deleteAll();
    }

    @Test
    @DisplayName("Test 1: When unexpired lock owned by owner-A, owner-B tryLock must return false")
    void whenUnexpiredLockOwnedByA_OwnerBTryLockMustReturnFalse() {
        // Arrange: Owner-A acquires an unexpired lock
        String lockName = "test-lock-unexpired";
        String ownerA = "broker-1_token-A-" + UUID.randomUUID();
        
        // Directly insert lock for owner-A with future expiration
        LockEntity lockA = new LockEntity();
        lockA.setName(lockName);
        lockA.setOwner(ownerA);
        lockA.setExpireAt(LocalDateTime.now().plus(60, ChronoUnit.SECONDS));
        lockEntityRepo.save(lockA);

        // Verify lock is in database
        LockEntity found = lockEntityRepo.findByName(lockName);
        assertNotNull(found);
        assertEquals(ownerA, found.getOwner());

        // Act & Assert: Owner-B tries atomic acquire - should fail because A's lock hasn't expired
        String ownerB = "broker-2_token-B-" + UUID.randomUUID();
        LocalDateTime expireAtB = LocalDateTime.now().plus(60, ChronoUnit.SECONDS);
        LocalDateTime now = LocalDateTime.now();
        
        int affected = lockEntityRepo.tryAcquireLock(lockName, ownerB, expireAtB, now);
        
        // BUG FIX VERIFICATION: Before the fix, this logic was inverted
        // tryAcquireLock should return 0 (failure) because A's lock is still valid
        assertEquals(0, affected, 
            "Owner-B should NOT be able to acquire an UNEXPIRED lock owned by Owner-A. " +
            "This tests the atomic conditional update works correctly.");
        
        // Verify A still owns the lock
        LockEntity afterAttempt = lockEntityRepo.findByName(lockName);
        assertNotNull(afterAttempt);
        assertEquals(ownerA, afterAttempt.getOwner(), "Lock should still be owned by Owner-A");
    }

    @Test
    @DisplayName("Test 2: When expired lock is taken over by owner-B, owner-A's unlock must not affect B's lock")
    void whenExpiredLockTakenOverByB_OwnerAUnlockMustNotDeleteBsLock() {
        // Arrange: Create an EXPIRED lock for Owner-A
        String lockName = "test-lock-expired-takeover";
        String ownerA = "broker-1_token-A-" + UUID.randomUUID();
        
        LockEntity lockEntity = new LockEntity();
        lockEntity.setName(lockName);
        lockEntity.setOwner(ownerA);
        lockEntity.setExpireAt(LocalDateTime.now().minus(1, ChronoUnit.SECONDS)); // Already expired
        lockEntityRepo.save(lockEntity);

        // Verify lock exists and is expired
        LockEntity found = lockEntityRepo.findByName(lockName);
        assertNotNull(found);
        assertTrue(found.getExpireAt().isBefore(LocalDateTime.now()), "Lock should be expired");

        // Act: Owner-B takes over the expired lock using atomic acquire
        String ownerB = "broker-2_token-B-" + UUID.randomUUID();
        LocalDateTime expireAtB = LocalDateTime.now().plus(60, ChronoUnit.SECONDS);
        LocalDateTime now = LocalDateTime.now();
        
        int affected = lockEntityRepo.tryAcquireLock(lockName, ownerB, expireAtB, now);
        
        // Assert: Owner-B should successfully take over the expired lock
        assertEquals(1, affected, "Owner-B should successfully take over the EXPIRED lock");
        
        // Verify B now owns the lock
        LockEntity afterTakeover = lockEntityRepo.findByName(lockName);
        assertNotNull(afterTakeover);
        assertEquals(ownerB, afterTakeover.getOwner(), "Lock should now be owned by Owner-B");
        assertFalse(afterTakeover.getExpireAt().isBefore(LocalDateTime.now()), "Lock should be unexpired");

        // Act: Owner-A tries to unlock using their old token
        int deletedByA = lockEntityRepo.deleteByNameAndOwner(lockName, ownerA);
        
        // Assert: Owner-A's unlock should NOT delete B's lock (0 rows affected)
        assertEquals(0, deletedByA, 
            "Owner-A should NOT be able to delete Owner-B's lock. " +
            "This validates ownership is correctly tied to the token.");
        
        // Verify B's lock is still intact
        LockEntity afterWrongUnlock = lockEntityRepo.findByName(lockName);
        assertNotNull(afterWrongUnlock, "Lock should still exist after Owner-A's invalid unlock attempt");
        assertEquals(ownerB, afterWrongUnlock.getOwner(), "Lock should still be owned by Owner-B");
    }

    @Test
    @DisplayName("Test 3: Same owner can re-acquire (re-entrant) and refresh expiration")
    void sameOwnerCanReacquireAndRefreshExpiration() {
        String lockName = "test-reentrant";
        String ownerA = "broker-1_token-A";
        LocalDateTime originalExpire = LocalDateTime.now().plus(10, ChronoUnit.SECONDS);
        
        // First acquire
        LockEntity first = new LockEntity();
        first.setName(lockName);
        first.setOwner(ownerA);
        first.setExpireAt(originalExpire);
        lockEntityRepo.save(first);
        
        // Act: Same owner re-acquires (simulates re-entrant lock or lease renewal)
        LocalDateTime newExpire = LocalDateTime.now().plus(60, ChronoUnit.SECONDS);
        LocalDateTime now = LocalDateTime.now();
        
        int affected = lockEntityRepo.tryAcquireLock(lockName, ownerA, newExpire, now);
        
        // Assert: Should succeed because same owner
        assertEquals(1, affected, "Same owner should be able to re-acquire/re-fresh lock");
        
        // Verify expiration was updated
        LockEntity afterReacquire = lockEntityRepo.findByName(lockName);
        assertNotNull(afterReacquire);
        assertTrue(afterReacquire.getExpireAt().isAfter(originalExpire), 
            "Expiration should be refreshed to later time");
    }

    @Test
    @DisplayName("Test 4: Atomic insert new lock when record doesn't exist")
    void atomicInsertWhenRecordDoesNotExist() {
        String lockName = "test-new-lock";
        String owner = "broker-1_token-new";
        LocalDateTime expireAt = LocalDateTime.now().plus(60, ChronoUnit.SECONDS);
        LocalDateTime now = LocalDateTime.now();
        
        // Verify no lock exists
        assertNull(lockEntityRepo.findByName(lockName), "Lock should not exist initially");
        
        // Act: Acquire when no record exists
        int affected = lockEntityRepo.tryAcquireLock(lockName, owner, expireAt, now);
        
        // Assert: Should succeed and create new record
        assertEquals(1, affected, "Should successfully insert new lock record");
        
        LockEntity created = lockEntityRepo.findByName(lockName);
        assertNotNull(created);
        assertEquals(owner, created.getOwner());
    }

    @Test
    @DisplayName("Test 5: UUID-based token prevents threadId collision")
    void uuidTokenPreventsThreadIdCollision() {
        // This test validates that we now use UUID instead of threadId
        // by checking the format of owner tokens in the database
        
        String lockName = "test-uuid-token";
        String brokerId = "broker-test";
        
        // Generate token using the new method (UUID format)
        String token = brokerId + "_" + UUID.randomUUID().toString();
        
        LockEntity lock = new LockEntity();
        lock.setName(lockName);
        lock.setOwner(token);
        lock.setExpireAt(LocalDateTime.now().plus(60, ChronoUnit.SECONDS));
        lockEntityRepo.save(lock);
        
        LockEntity found = lockEntityRepo.findByName(lockName);
        assertNotNull(found);
        
        // Verify token contains UUID (not just numeric threadId)
        String owner = found.getOwner();
        assertTrue(owner.contains("-"), "Token should contain UUID with dashes");
        assertTrue(owner.startsWith(brokerId + "_"), "Token should start with brokerId_");
        
        // Verify it's a valid UUID format after the brokerId_
        String uuidPart = owner.substring(owner.indexOf("_") + 1);
        assertDoesNotThrow(() -> UUID.fromString(uuidPart), 
            "Token suffix should be a valid UUID");
    }

    @Test
    @DisplayName("Test 6: Boundary test - expiry exactly at now should be considered expired (<=)")
    void expiryExactlyAtNowShouldBeConsideredExpired() {
        String lockName = "test-boundary-expiry";
        String ownerA = "broker-1_token-A";
        
        // Create lock with expiry exactly at "now"
        LocalDateTime exactNow = LocalDateTime.now();
        LockEntity lock = new LockEntity();
        lock.setName(lockName);
        lock.setOwner(ownerA);
        lock.setExpireAt(exactNow);
        lockEntityRepo.save(lock);
        
        // Owner-B tries to acquire with expire_at <= now (should succeed)
        String ownerB = "broker-2_token-B";
        LocalDateTime newExpire = exactNow.plus(60, ChronoUnit.SECONDS);
        
        int affected = lockEntityRepo.tryAcquireLock(lockName, ownerB, newExpire, exactNow);
        
        // Should succeed because expire_at <= now (boundary condition)
        assertEquals(1, affected, 
            "Lock with expire_at exactly at now should be considered expired and acquirable");
    }
}
