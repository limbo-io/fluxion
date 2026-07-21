/*
 * Copyright 2025-2030 Limbo Team (https://github.com/limbo-io).
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

package io.fluxion.test.integration.mysql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MySQL-specific tests for fencing mechanism using NOW(3) millisecond precision.
 * 
 * These tests verify that MySQL's NOW(3) function provides consistent 
 * time-based fencing for:
 * - Schedule lease verification (lease_until > NOW(3))
 * - Lock expiration checking (expire_at <= NOW(3))
 * - Execution recovery boundary checks
 * 
 * NOW(3) is critical for sub-second precision in high-frequency scenarios.
 * H2's millisecond precision behaves differently than MySQL.
 */
@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
@DisplayName("Fencing Condition MySQL Integration Tests")
class FencingConditionMySqlTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("NOW(3) returns millisecond-precision timestamp")
    @Transactional
    void testNow3MillisecondPrecision() {
        // When: Query MySQL's NOW(3) function
        Query query = entityManager.createNativeQuery(
            "SELECT NOW(3), NOW(), MICROSECOND(NOW(3)), MICROSECOND(NOW())"
        );
        Object[] result = (Object[]) query.getSingleResult();
        
        String now3 = result[0].toString();
        String now = result[1].toString();
        BigInteger microNow3 = (BigInteger) result[2];
        BigInteger microNow = (BigInteger) result[3];

        // Then: NOW(3) should have millisecond precision (microseconds not 000000)
        // NOW() typically has only second precision
        assertThat(now3).contains("."); // Has millisecond component
        assertThat(microNow3.longValue() % 1000).isNotEqualTo(0); // Sub-millisecond component

        System.out.println("NOW(3): " + now3 + " (microseconds: " + microNow3 + ")");
        System.out.println("NOW():  " + now + " (microseconds: " + microNow + ")");
    }

    @Test
    @DisplayName("NOW(3) is consistent across multiple calls within transaction")
    @Transactional
    void testNow3Consistency() {
        // When: Get NOW(3) multiple times
        Query query1 = entityManager.createNativeQuery("SELECT NOW(3)");
        String time1 = query1.getSingleResult().toString();
        
        Query query2 = entityManager.createNativeQuery("SELECT NOW(3)");
        String time2 = query2.getSingleResult().toString();
        
        // Then: Times should be identical (within same transaction, NOW is constant)
        // Note: MySQL's NOW() returns the time at transaction start for reads
        assertThat(time1).isEqualTo(time2);
    }

    @Test
    @DisplayName("Fencing condition correctly identifies expired leases")
    @Transactional
    void testExpiredLeaseFencing() {
        // Given: Create a test table with lease_until column
        entityManager.createNativeQuery(
            "CREATE TEMPORARY TABLE test_lease (" +
            "  id INT PRIMARY KEY, " +
            "  lease_until DATETIME(3) NULL, " +
            "  owner VARCHAR(64)" +
            ")"
        ).executeUpdate();

        // Insert records with various lease states
        LocalDateTime now = LocalDateTime.now();
        
        // Expired lease (1 second ago)
        entityManager.createNativeQuery(
            "INSERT INTO test_lease (id, lease_until, owner) VALUES (1, DATE_SUB(NOW(3), INTERVAL 1 SECOND), 'broker-a')"
        ).executeUpdate();

        // Valid lease (10 seconds in future)
        entityManager.createNativeQuery(
            "INSERT INTO test_lease (id, lease_until, owner) VALUES (2, DATE_ADD(NOW(3), INTERVAL 10 SECOND), 'broker-b')"
        ).executeUpdate();

        // No lease (null)
        entityManager.createNativeQuery(
            "INSERT INTO test_lease (id, lease_until, owner) VALUES (3, NULL, 'broker-c')"
        ).executeUpdate();

        // When: Query valid leases (lease_until > NOW(3))
        Query validQuery = entityManager.createNativeQuery(
            "SELECT id, owner FROM test_lease WHERE lease_until > NOW(3)"
        );
        @SuppressWarnings("unchecked")
        java.util.List<Object[]> validResults = validQuery.getResultList();

        // Then: Only broker-b should have valid lease
        assertThat(validResults).hasSize(1);
        assertThat(validResults.get(0)[1]).isEqualTo("broker-b");

        // When: Query expired or null leases
        Query expiredQuery = entityManager.createNativeQuery(
            "SELECT id, owner FROM test_lease WHERE lease_until IS NULL OR lease_until <= NOW(3)"
        );
        @SuppressWarnings("unchecked")
        java.util.List<Object[]> expiredResults = expiredQuery.getResultList();

        // Then: broker-a (expired) and broker-c (null) should be found
        assertThat(expiredResults).hasSize(2);
    }

    @Test
    @DisplayName("Expire_at with millisecond precision prevents sub-second race")
    @Transactional
    void testMillisecondPrecisionPreventsRace() throws InterruptedException {
        // Given: Create a test lock table
        entityManager.createNativeQuery(
            "CREATE TEMPORARY TABLE test_lock (" +
            "  name VARCHAR(255) PRIMARY KEY, " +
            "  expire_at DATETIME(3) NOT NULL, " +
            "  owner VARCHAR(255)" +
            ")"
        ).executeUpdate();

        // Insert a lock expiring in 500ms
        entityManager.createNativeQuery(
            "INSERT INTO test_lock (name, expire_at, owner) " +
            "VALUES ('test-lock', DATE_ADD(NOW(3), INTERVAL 500 MICROSECOND), 'owner-a')"
        ).executeUpdate();

        // Immediately try to acquire (should fail - not expired yet)
        Query tryAcquireQuery = entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM test_lock WHERE name = 'test-lock' AND expire_at <= NOW(3)"
        );
        BigInteger canAcquireCount = (BigInteger) tryAcquireQuery.getSingleResult();
        
        // Should not be able to acquire - lock hasn't expired yet
        assertThat(canAcquireCount.intValue()).isEqualTo(0);

        // Wait for expiration
        Thread.sleep(600);

        // Now should be able to acquire
        Query tryAcquireQuery2 = entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM test_lock WHERE name = 'test-lock' AND expire_at <= NOW(3)"
        );
        BigInteger canAcquireCount2 = (BigInteger) tryAcquireQuery2.getSingleResult();
        
        assertThat(canAcquireCount2.intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("Atomic INSERT ON DUPLICATE KEY UPDATE with NOW(3)")
    @Transactional
    void testAtomicInsertOnDuplicateKeyUpdate() {
        // Given: Create a test lock table
        entityManager.createNativeQuery(
            "CREATE TEMPORARY TABLE test_atomic_lock (" +
            "  name VARCHAR(255) PRIMARY KEY, " +
            "  expire_at DATETIME(3) NOT NULL, " +
            "  owner VARCHAR(255), " +
            "  updated_at DATETIME(3) NOT NULL" +
            ")"
        ).executeUpdate();

        // Insert a lock that will expire in 100ms
        entityManager.createNativeQuery(
            "INSERT INTO test_atomic_lock (name, expire_at, owner, updated_at) " +
            "VALUES ('atomic-test', DATE_SUB(NOW(3), INTERVAL 1 SECOND), 'old-owner', NOW(3))"
        ).executeUpdate();

        // When: Use atomic INSERT ... ON DUPLICATE KEY UPDATE to acquire expired lock
        int affected = entityManager.createNativeQuery(
            "INSERT INTO test_atomic_lock (name, expire_at, owner, updated_at) " +
            "VALUES (:name, :expireAt, :owner, NOW(3)) " +
            "ON DUPLICATE KEY UPDATE " +
            "  owner = IF(expire_at <= NOW(3), :owner, owner), " +
            "  expire_at = IF(expire_at <= NOW(3), :expireAt, expire_at), " +
            "  updated_at = IF(expire_at <= NOW(3), NOW(3), updated_at)"
        )
        .setParameter("name", "atomic-test")
        .setParameter("expireAt", LocalDateTime.now().plusMinutes(5))
        .setParameter("owner", "new-owner")
        .executeUpdate();

        // Then: Should have affected 2 rows (updated)
        assertThat(affected).isEqualTo(2);

        // Verify new owner
        Query verifyQuery = entityManager.createNativeQuery(
            "SELECT owner FROM test_atomic_lock WHERE name = 'atomic-test'"
        );
        String newOwner = (String) verifyQuery.getSingleResult();
        assertThat(newOwner).isEqualTo("new-owner");
    }

    @Test
    @DisplayName("Concurrent time checks use same NOW(3) reference in transaction")
    @Transactional
    void testTimeConsistencyAcrossQueries() {
        // When: Select current time multiple ways
        Query nowQuery = entityManager.createNativeQuery("SELECT NOW(3)");
        String nowTime = nowQuery.getSingleResult().toString();

        Query delayQuery = entityManager.createNativeQuery(
            "SELECT NOW(3), SLEEP(0.01), NOW(3)"
        );
        Object[] delayResult = (Object[]) delayQuery.getSingleResult();
        String timeBeforeSleep = delayResult[0].toString();
        String timeAfterSleep = delayResult[2].toString();

        // Then: Within a transaction, NOW() is constant (transaction start time)
        assertThat(timeBeforeSleep).isEqualTo(timeAfterSleep);
        System.out.println("NOW(3) consistency check passed: " + nowTime + " = " + timeBeforeSleep);
    }
}
