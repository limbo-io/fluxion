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

package io.fluxion.worker.core.job;

import java.util.concurrent.atomic.AtomicInteger;

public class TaskCounter {

    AtomicInteger total = new AtomicInteger(0);

    AtomicInteger success = new AtomicInteger(0);

    AtomicInteger fail = new AtomicInteger(0);

    public boolean isFinished() {
        return total.get() == success.get() + fail.get();
    }

    public AtomicInteger getTotal() {
        return total;
    }

    public AtomicInteger getSuccess() {
        return success;
    }

    public AtomicInteger getFail() {
        return fail;
    }
}