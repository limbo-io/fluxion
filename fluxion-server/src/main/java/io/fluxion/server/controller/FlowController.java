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

package io.fluxion.server.controller;

import io.fluxion.core.cqrs.Cmd;
import io.fluxion.core.flow.cmd.*;
import io.fluxion.remote.api.PageResponse;
import io.fluxion.server.api.flow.request.FlowConfigRequest;
import io.fluxion.server.api.flow.request.FlowCreateRequest;
import io.fluxion.server.api.flow.request.FlowPageRequest;
import io.fluxion.server.api.flow.request.FlowUpdateRequest;
import io.fluxion.server.api.flow.view.FlowView;
import io.fluxion.server.service.FlowAppService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author Devil
 */
@RestController
public class FlowController {

    @Resource
    private FlowAppService flowAppService;

    @RequestMapping("/api/v1/flow/create")
    public String create(@RequestBody FlowCreateRequest request) {
        FlowCreateCmd cmd = new FlowCreateCmd(request.getName(), request.getDescription());
        FlowCreateCmd.Response response = Cmd.send(cmd);
        return response.getId();
    }

    @RequestMapping("/api/v1/flow/update")
    public void update(@RequestBody FlowUpdateRequest request) {
        FlowUpdateCmd cmd = new FlowUpdateCmd(
            request.getId(),
            request.getName(),
            request.getDescription()
        );
        Cmd.send(cmd);
    }

    @RequestMapping("/api/v1/flow/draft")
    public String draft(@RequestBody FlowConfigRequest request) {
        FlowDraftCmd cmd = new FlowDraftCmd(
            request.getId(),
            request.getConfig()
        );
        return Cmd.send(cmd).getVersion();
    }

    @RequestMapping("/api/v1/flow/publish")
    public FlowPublishCmd.Response publish(@RequestBody FlowConfigRequest request) {
        FlowPublishCmd cmd = new FlowPublishCmd(
            request.getId(),
            request.getConfig()
        );
        return Cmd.send(cmd);
    }

    @RequestMapping("/api/v1/flow/page")
    public PageResponse<FlowView> page(@RequestBody FlowPageRequest request) {
        return flowAppService.page(request);
    }

    @RequestMapping("/api/v1/flow/get")
    public FlowView get(@RequestParam String id, @RequestParam(required = false) String version) {
        return flowAppService.get(id, version);
    }

    @RequestMapping("/api/v1/flow/delete")
    public void delete(@RequestParam String id) {
        Cmd.send(new FlowDeleteCmd(id));
    }

}
