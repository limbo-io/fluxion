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

package io.fluxion.server.infrastructure.dao.repository;

import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.time.Instant;

/**
 * @author Devil
 */
@Repository
public interface ExecutionEntityRepo extends JpaRepository<ExecutionEntity, String>, JpaSpecificationExecutor<ExecutionEntity> {

    /**
     * Execution 创建幂等：每个 (triggerId, triggerAt) 只允许一个实例
     */
    ExecutionEntity findByTriggerIdAndTriggerAt(String triggerId, Instant triggerAt);

    /**
     * 身份回查的加锁当前读：REPEATABLE READ 下普通 SELECT 走事务快照——先行者后提交的行
     * 对后到者事务不可见，归属判定会误判为不存在；悲观锁读走 InnoDB 当前读，必取最新已提交行。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    ExecutionEntity findWithLockingByTriggerIdAndTriggerAt(String triggerId, Instant triggerAt);

    /**
     * 以 (trigger_id, trigger_at) 身份唯一键为目标的数据库幂等写入：重复键时对已有行
     * 执行 no-op 赋值（trigger_at 新旧同值），不向外抛唯一键异常、不吞其它 SQL 错误。
     * 返回行数只作参考——驱动对 ON DUPLICATE 的 found/affected 计数语义不一致，
     * CREATED/EXISTING 必须由调用方回查行的 executionId 归属判定。
     */
    @Modifying
    @Query(value = "insert into fluxion_execution " +
        "(execution_id, trigger_id, trigger_type, executable_id, executable_type, executable_version, " +
        "status, trigger_at, deadline_at, bucket, fire_attempt, is_deleted, created_at, updated_at) " +
        "values (:executionId, :triggerId, :triggerType, :executableId, :executableType, :executableVersion, " +
        ":status, :triggerAt, :deadlineAt, :bucket, 0, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
        "on duplicate key update trigger_at = trigger_at", nativeQuery = true)
    int insertExecutionIdentity(@Param("executionId") String executionId,
                               @Param("triggerId") String triggerId,
                               @Param("triggerType") String triggerType,
                               @Param("executableId") String executableId,
                               @Param("executableType") String executableType,
                               @Param("executableVersion") String executableVersion,
                               @Param("status") String status,
                               @Param("triggerAt") Instant triggerAt,
                               @Param("deadlineAt") Instant deadlineAt,
                               @Param("bucket") int bucket);

}