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

/**
 * Represents a successfully acquired distributed lock with its ownership token.
 * The token must be used to release the lock to prevent accidental release
 * of locks held by other threads/processes.
 *
 * @param name the lock name
 * @param token the unique ownership token assigned to this lock acquisition
 * @author Fluxion Team
 * @since 2025
 */
public record Locked(String name, String token) {
    /**
     * Validates that the provided token matches this lock's ownership token.
     *
     * @param otherToken the token to validate
     * @return true if the token matches, false otherwise
     */
    public boolean isOwner(String otherToken) {
        return token != null && token.equals(otherToken);
    }
}
