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

package io.fluxion.test.integration.mysql;

import io.fluxion.server.infrastructure.dao.entity.ExecutionEntity;
import io.fluxion.server.infrastructure.dao.repository.ExecutionEntityRepo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = MySqlTestApplication.class)
@ActiveProfiles("test-mysql")
@DisplayName("Execution identity constraint tests")
class ExecutionIdentityConstraintTest extends AbstractMySqlIntegrationTest {

    @Autowired
    private ExecutionEntityRepo executionEntityRepo;

    @Test
    @Transactional
    @DisplayName("same trigger and trigger time can create only one execution")
    void shouldRejectDuplicateScheduleOccurrence() {
        String triggerId = "trigger-" + UUID.randomUUID();
        LocalDateTime triggerAt = LocalDateTime.of(2026, 9, 12, 10, 0);
        executionEntityRepo.saveAndFlush(newExecution(triggerId, triggerAt));

        assertThatThrownBy(() -> executionEntityRepo.saveAndFlush(newExecution(triggerId, triggerAt)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private ExecutionEntity newExecution(String triggerId, LocalDateTime triggerAt) {
        ExecutionEntity entity = new ExecutionEntity();
        entity.setExecutionId("execution-" + UUID.randomUUID());
        entity.setTriggerId(triggerId);
        entity.setTriggerType("schedule");
        entity.setExecutableId("executable-1");
        entity.setExecutableType("executor");
        entity.setExecutableVersion("1");
        entity.setStatus("pending");
        entity.setTriggerAt(triggerAt);
        entity.setBucket(1);
        entity.setFireAttempt(0);
        return entity;
    }
}
