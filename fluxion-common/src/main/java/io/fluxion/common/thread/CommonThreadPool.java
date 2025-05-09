/*
 *
 *  * Copyright 2020-2024 fluxion Team (https://github.com/fluxion-io).
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * 	http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package io.fluxion.common.thread;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 先统一放这里 后期优化
 *
 * @author Devil
 * @since 2023/5/12
 */
public class CommonThreadPool {

    public static final ThreadPoolExecutor CPU = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(),
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(4096),
            new ThreadPoolExecutor.CallerRunsPolicy());

    public static final ThreadPoolExecutor IO = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 4,
            Runtime.getRuntime().availableProcessors() * 4,
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(1048576),
            new ThreadPoolExecutor.CallerRunsPolicy());

}
