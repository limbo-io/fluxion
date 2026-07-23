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

/**
 * H2-backed integration test base.
 *
 * The legacy package name is retained to avoid a disruptive package move; the
 * active test profile supplies an in-memory H2 datasource.
 */
public abstract class AbstractMySqlIntegrationTest {
}
