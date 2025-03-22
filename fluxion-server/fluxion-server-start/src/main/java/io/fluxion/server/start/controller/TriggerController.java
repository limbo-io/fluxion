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
import io.fluxion.server.core.trigger.Trigger;
import io.fluxion.server.core.trigger.cmd.TriggerCreateCmd;
import io.fluxion.server.core.trigger.cmd.TriggerDeleteCmd;
import io.fluxion.server.core.trigger.cmd.TriggerDisableCmd;
import io.fluxion.server.core.trigger.cmd.TriggerDraftCmd;
import io.fluxion.server.core.trigger.cmd.TriggerEnableCmd;
import io.fluxion.server.core.trigger.cmd.TriggerPublishCmd;
import io.fluxion.server.core.trigger.cmd.TriggerUpdateCmd;
import io.fluxion.server.core.trigger.query.TriggerByIdQuery;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.start.api.trigger.request.TriggerConfigRequest;
import io.fluxion.server.start.api.trigger.request.TriggerCreateRequest;
import io.fluxion.server.start.api.trigger.request.TriggerPageRequest;
import io.fluxion.server.start.api.trigger.request.TriggerUpdateRequest;
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
        TriggerCreateCmd.Response response = Cmd.send(new TriggerCreateCmd(
            request.getName(), request.getDescription()
        ));
        return response.getId();
    }

    @RequestMapping("/api/v1/trigger/update")
    public void update(@RequestBody TriggerUpdateRequest request) {
        Cmd.send(new TriggerUpdateCmd(
            request.getId(), request.getName(), request.getDescription()
        ));
    }

    @RequestMapping("/api/v1/trigger/draft")
    public void draft(@RequestBody TriggerConfigRequest request) {
        TriggerDraftCmd cmd = new TriggerDraftCmd(
            request.getId(),
            request.getConfig()
        );
        Cmd.send(cmd);
    }

    @RequestMapping("/api/v1/trigger/publish")
    public void publish(@RequestBody TriggerConfigRequest request) {
        TriggerPublishCmd cmd = new TriggerPublishCmd(
            request.getId(),
            request.getConfig()
        );
        Cmd.send(cmd);
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
    public PageResponse<Trigger> page(TriggerPageRequest request) {
        return triggerService.page(request);
    }

    @RequestMapping("/api/v1/trigger/get")
    public Trigger get(@RequestParam String id) {
        return Query.query(new TriggerByIdQuery(id)).getTrigger();
    }

    @RequestMapping("/api/v1/trigger/delete")
    public void delete(@RequestParam String id) {
        Cmd.send(new TriggerDeleteCmd(id));
    }

}
