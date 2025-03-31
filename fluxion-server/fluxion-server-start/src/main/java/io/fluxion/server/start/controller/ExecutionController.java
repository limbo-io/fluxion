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

package io.fluxion.server.start.controller;

import io.fluxion.remote.core.api.PageResponse;
import io.fluxion.server.start.api.execution.request.ExecutionPageRequest;
import io.fluxion.server.start.api.execution.view.ExecutionView;
import io.fluxion.server.start.service.ExecutionService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@RestController
public class ExecutionController {

    @Resource
    private ExecutionService executionService;

    @RequestMapping("/api/v1/execution/page")
    public PageResponse<ExecutionView> create(@RequestBody ExecutionPageRequest request) {
        return executionService.page(request);
    }
}
