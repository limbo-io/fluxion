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

package io.fluxion.worker.demo.executor;

import io.fluxion.worker.core.executor.MapReduceExecutor;
import io.fluxion.worker.core.job.Job;
import io.fluxion.worker.core.task.Task;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Devil
 */
@Component
public class MapReduceDemoExecutor extends MapReduceExecutor {

    @Override
    public List<Task> sharding(Job job) {
        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Task task = new Task("SUB_" + i, job.getId());
            tasks.add(task);
        }
        return tasks;
    }

    @Override
    public void reduce(Map<String, String> taskResults) {
        for (Map.Entry<String, String> entry : taskResults.entrySet()) {
            System.out.println("Reduce " + entry.getKey() + ":" + entry.getValue());
        }
    }

    @Override
    public void run(Task task) {
        System.out.println("Hello " + task.getId());
    }
}
