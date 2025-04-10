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

package io.fluxion.server.infrastructure.lock;

import java.util.function.Supplier;

/**
 * @author Devil
 * @since 2024/1/13
 */
public interface DistributedLock {
    /**
     * 阻塞 直到获取到锁
     * @param name 锁名
     * @param expire 加锁时间/毫秒
     * @param wait 等待时间
     */
    <T> T lock(String name, long expire, long wait, Supplier<T> supplier);

    /**
     * 尝试加锁，如果锁被占有返回失败
     * 可重入，已有锁对象可以重复加锁刷新锁的过期时间
     *
     * @param name   锁名
     * @param expire 加锁时间/毫秒
     * @return 是否成功
     */
    boolean tryLock(String name, long expire);

    /**
     * 释放锁
     *
     * @param name 锁名
     */
    boolean unlock(String name);
}
