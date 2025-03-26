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

package io.fluxion.server.start.controller;

import io.fluxion.remote.core.api.PageResponse;
import io.fluxion.server.core.workflow.cmd.*;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.start.api.workflow.request.WorkflowConfigRequest;
import io.fluxion.server.start.api.workflow.request.WorkflowCreateRequest;
import io.fluxion.server.start.api.workflow.request.WorkflowPageRequest;
import io.fluxion.server.start.api.workflow.request.WorkflowUpdateRequest;
import io.fluxion.server.start.api.workflow.view.WorkflowView;
import io.fluxion.server.start.service.WorkflowService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@RestController
public class WorkflowController {

    @Resource
    private WorkflowService workflowService;

    @RequestMapping("/api/v1/workflow/create")
    public String create(@RequestBody WorkflowCreateRequest request) {
        WorkflowCreateCmd cmd = new WorkflowCreateCmd(request.getName(), request.getDescription());
        WorkflowCreateCmd.Response response = Cmd.send(cmd);
        return response.getId();
    }

    @RequestMapping("/api/v1/workflow/update")
    public void update(@RequestBody WorkflowUpdateRequest request) {
        WorkflowUpdateCmd cmd = new WorkflowUpdateCmd(
            request.getId(),
            request.getName(),
            request.getDescription()
        );
        Cmd.send(cmd);
    }

    @RequestMapping("/api/v1/workflow/draft")
    public String draft(@RequestBody WorkflowConfigRequest request) {
        WorkflowDraftCmd cmd = new WorkflowDraftCmd(
            request.getId(),
            request.getConfig()
        );
        return Cmd.send(cmd).getVersion();
    }

    @RequestMapping("/api/v1/workflow/publish")
    public WorkflowPublishCmd.Response publish(@RequestBody WorkflowConfigRequest request) {
        WorkflowPublishCmd cmd = new WorkflowPublishCmd(
            request.getId(),
            request.getConfig()
        );
        return Cmd.send(cmd);
    }

    @RequestMapping("/api/v1/workflow/page")
    public PageResponse<WorkflowView> page(@RequestBody WorkflowPageRequest request) {
        return workflowService.page(request);
    }

    @RequestMapping("/api/v1/workflow/get")
    public WorkflowView get(@RequestParam String id, @RequestParam(required = false) String version) {
        return workflowService.get(id, version);
    }

    @RequestMapping("/api/v1/workflow/delete")
    public void delete(@RequestParam String id) {
        Cmd.send(new WorkflowDeleteCmd(id));
    }

}
