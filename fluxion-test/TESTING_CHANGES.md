# Fluxion Test Changes Log

## Overview

This document records all test deletions, rewrites, and additions during the distributed scheduling test capability governance period.

## Phase 4: Test Cleanup and Documentation

### Deleted Tests

| Test Class | Reason | Alternative Coverage |
|------------|--------|---------------------|
| `FencingConditionMySqlTest` | Only tested MySQL `NOW(3)` and temp tables, no business logic | `ScheduleLeaseMySqlTest`, `BrokerMultiNodeLeaseTest` |
| `ExecutionRegistrationTest` | Pure DTO builder test, no business coverage | Integration tests cover registration flow |
| `ExecutionResultTest` | Pure DTO builder test | `ExecutionRecoveryMySqlTest` covers real results |
| `ExecutionStateTest` | Pure enum test | `DefaultFaultToleranceCoordinator` state transitions |
| `ErrorCategoryTest` | Pure enum test | Covered by actual error handling in integration tests |

### Rewritten Tests

| Test Class | Change | Purpose |
|------------|--------|---------|
| `DistributedLockMySqlTest` | Documented reentrant behavior | Clarify current implementation allows same-thread reacquisition |
| `PeriodicTaskSchedulerTest` | Verified with FakeTimer | Controlled clock instead of Thread.sleep |
| `BrokerMultiNodeLeaseTest` | Added CoreTask chain verification | T2.1: CoreTask → Command → Handler → Database |

### Added Tests

| Test Class | Coverage | Phase |
|------------|----------|-------|
| `WorkerCpuLoadSelectionTest` | T3.5/T5.2: LEAST_CPU_LOAD worker selection | Phase 5 |
| `WorkerLoadBalancingTest` | WorkerFilter and basic load balancing | Phase 3 |
| `BucketOwnershipChangeTest` | T2.5: Bucket transfer and task cancellation | Phase 2 |
| `LeaseBoundaryTest` | T2.2/T2.3: Lease duration boundaries, graceful shutdown | Phase 2 |
| `BacklogStrategyTest` | T3.6: LATEST_ONLY backlog policy logic | Unit test |

## CI/CD Changes

### `.github/workflows/ci.yml`

Added three jobs for T4.6:

1. **compile**: Basic compilation check
2. **regression-test**: Runs `regression-test` profile (non-MySQL tests)
3. **mysql-integration-test**: Runs MySQL integration tests with service container
4. **all-tests-passed**: Gate check requiring both test jobs to pass

### Profile Changes

| Profile | Status | Tests |
|---------|--------|-------|
| `regression-test` | ✅ Active | Non-MySQL unit and core tests |
| `mysql-integration-test` | ✅ Active | MySQL-specific integration tests |
| `module-test` | ❌ Removed | Never existed |
| `link-test` | ❌ Removed | Never existed |
| `e2e` | ❌ Removed | Never existed |
| `all-tests` | ❌ Removed | Never existed |

## Implementation Highlights

### T5.1: Backlog LATEST_ONLY Policy

**File**: `ScheduleDelayCommandService.java`

```java
private List<ScheduleDelay> applyBacklogPolicy(List<ScheduleDelay> delays)
```

- Groups delays by schedule ID
- CRON/FIXED_RATE: Only keeps latest valid trigger
- FIXED_DELAY: Keeps all (no backlog compensation)
- Logs: `[BACKLOG-LATEST-ONLY] Schedule {} has {} INIT delays, only latest {} will be executed`

### T5.2: LEAST_CPU_LOAD Strategy

**Files**:
- `LoadBalanceType.java`: Added `LEAST_CPU_LOAD("least_cpu_load")`
- `LeastCpuLoadLBStrategy.java`: New strategy implementation
- `WorkerSelectorFactory.java`: Registered strategy, added `sortByCpuLoad()`

**Behavior**: Sorts workers by CPU load ascending, treats no-metric as MAX_VALUE (last).

## Outstanding Test Debt

The following areas require product review or additional implementation:

| Task | Reason | Recommendation |
|------|--------|----------------|
| T3.3/T5.4 Timeout retry chain | DefaultFaultToleranceCoordinator implemented but needs IT verification | Create integration test with real timeout firing |
| T3.4/T5.3 Worker offline migration | ExecutionMigrateService exists but needs full worker lifecycle | Integration test with worker heartbeat timeout |
| T4.4 JPQL direct table updates | Currently used for test setup convenience | Expose lease expiration API for tests |
| T5.5 CoreTask lease renewal | CoreTask chain verified, need scheduler verification | Integration test with actual time progression |
| T5.6 Documentation | This file covers most, find review | Review by tech lead |

## Verification Commands

```bash
# Run regression tests
mvn clean test -pl fluxion-test -am -Pregression-test

# Run MySQL integration tests (requires Docker or external MySQL)
mvn test -pl fluxion-test -am -Pmysql-integration-test

# Full build with all tests
mvn clean test
```

## Last Updated

2026-07-23: Core implementations complete, 67 tests passing.
