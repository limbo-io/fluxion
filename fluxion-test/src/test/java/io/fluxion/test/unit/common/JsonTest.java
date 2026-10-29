/*
 * Copyright 2025-2030 limbo-io Team (https://github.com/limbo-io).
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

package io.fluxion.test.unit.common;

import io.fluxion.server.core.job.Job;
import io.fluxion.server.core.job.JobMonitor;
import io.fluxion.server.core.job.config.ExecutorJobConfig;

import static org.assertj.core.api.Assertions.assertThat;
import io.limbo.utils.ReflectionUtils;
import io.limbo.utils.json.JacksonUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * @author Devil
 */
public class JsonTest {

    @BeforeAll
    static void configureSubtypeDiscovery() {
        ReflectionUtils.configure("io.fluxion");
    }

    @Test
    void shouldDeserializeJobMonitorCounts() {
        String json = "{\"totalTaskNum\":7,\"successTaskNum\":4,\"failTaskNum\":3}";
        JobMonitor taskMonitor = JacksonUtils.toType(json, JobMonitor.class);
        assertThat(taskMonitor.getTotalTaskNum()).isEqualTo(7);
        assertThat(taskMonitor.getSuccessTaskNum()).isEqualTo(4);
        assertThat(taskMonitor.getFailTaskNum()).isEqualTo(3);
    }

    @Test
    void shouldResolveExecutorJobConfigSubtype() {
        String json = "{\"type\":\"executor\"}";
        Job.Config config = JacksonUtils.toType(json, Job.Config.class);
        assertThat(config).isInstanceOf(ExecutorJobConfig.class);
    }

}
