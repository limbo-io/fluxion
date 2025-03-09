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
import io.fluxion.server.core.execution.ExecutionRefType;
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.cmd.TriggerCreateCmd;
import io.fluxion.server.core.trigger.cmd.TriggerDeleteCmd;
import io.fluxion.server.core.trigger.cmd.TriggerDisableCmd;
import io.fluxion.server.core.trigger.cmd.TriggerEnableCmd;
import io.fluxion.server.core.trigger.cmd.TriggerUpdateCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.start.api.trigger.request.TriggerCreateRequest;
import io.fluxion.server.start.api.trigger.request.TriggerPageRequest;
import io.fluxion.server.start.api.trigger.request.TriggerUpdateRequest;
import io.fluxion.server.start.api.trigger.view.TriggerView;
import io.fluxion.server.start.service.TriggerService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;


/**
 * @author Devil
 */
@RestController
public class TriggerController {

    @Resource
    private TriggerService triggerService;

    @RequestMapping("/api/v1/trigger/create")
    public String create(@RequestBody TriggerCreateRequest request) {
        Trigger trigger = new Trigger();
        trigger.setConfig(request.getConfig());
        trigger.setDescription(request.getDescription());
        trigger.setRefId(request.getRefId());
        trigger.setRefType(ExecutionRefType.parse(request.getRefType()));
        TriggerCreateCmd.Response response = Cmd.send(new TriggerCreateCmd(trigger));
        return response.getId();
    }

    @RequestMapping("/api/v1/trigger/update")
    public void update(@RequestBody TriggerUpdateRequest request) {
        Trigger trigger = new Trigger();
        trigger.setId(request.getId());
        trigger.setConfig(request.getConfig());
        trigger.setDescription(request.getDescription());
        trigger.setRefId(request.getRefId());
        trigger.setRefType(ExecutionRefType.parse(request.getRefType()));
        Cmd.send(new TriggerUpdateCmd(trigger));
    }

    @RequestMapping("/api/v1/trigger/enable")
    public void enable(@RequestParam String id) {
        Cmd.send(new TriggerEnableCmd(id));
    }

    @RequestMapping("/api/v1/trigger/disable")
    public void disable(@RequestParam String id) {
        Cmd.send(new TriggerDisableCmd(id));
    }

    @RequestMapping("/api/v1/trigger/page")
    public PageResponse<TriggerView> page(TriggerPageRequest request) {
        return triggerService.page(request);
    }

    @RequestMapping("/api/v1/trigger/get")
    public TriggerView get(@RequestParam String id) {
        return triggerService.get(id);
    }

    @RequestMapping("/api/v1/trigger/delete")
    public void delete(@RequestParam String id) {
        Cmd.send(new TriggerDeleteCmd(id));
    }

}
