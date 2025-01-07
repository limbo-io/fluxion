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

import io.fluxion.common.utils.ValidatorUtils;
import io.fluxion.platform.cqrs.Cmd;
import io.fluxion.platform.exception.PlatformException;
import io.fluxion.platform.trigger.cmd.*;
import io.fluxion.remote.api.PageResponse;
import io.fluxion.server.api.trigger.request.TriggerConfigRequest;
import io.fluxion.server.api.trigger.request.TriggerCreateRequest;
import io.fluxion.server.api.trigger.request.TriggerPageRequest;
import io.fluxion.server.api.trigger.request.TriggerUpdateRequest;
import io.fluxion.server.api.trigger.view.TriggerView;
import io.fluxion.server.service.TriggerAppService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.ConstraintViolation;
import java.util.Set;

import static io.fluxion.platform.exception.ErrorCode.PARAM_ERROR;

/**
 * @author Devil
 */
@RestController
public class TriggerController {

    @Resource
    private TriggerAppService triggerAppService;

    @RequestMapping("/api/v1/trigger/create")
    public String create(@RequestBody TriggerCreateRequest request) {
        Set<ConstraintViolation<TriggerCreateRequest>> validate = ValidatorUtils.validate(request);
        if (CollectionUtils.isNotEmpty(validate)) {
            throw new PlatformException(PARAM_ERROR, validate.stream().findFirst().get().getMessage());
        }
        TriggerCreateCmd cmd = new TriggerCreateCmd(
            request.getType(),
            request.getRefType(), request.getRefId(),
            request.getDescription()
        );
        TriggerCreateCmd.Response response = Cmd.send(cmd);
        return response.getId();
    }

    @RequestMapping("/api/v1/trigger/update")
    public void update(@RequestBody TriggerUpdateRequest request) {
        TriggerUpdateCmd cmd = new TriggerUpdateCmd(
            request.getId(),
            request.getDescription()
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
    public PageResponse<TriggerView> page(TriggerPageRequest request) {
        return triggerAppService.page(request);
    }

    @RequestMapping("/api/v1/trigger/get")
    public TriggerView get(@RequestParam String id) {
        return triggerAppService.get(id);
    }

    @RequestMapping("/api/v1/trigger/delete")
    public void delete(@RequestParam String id) {
        Cmd.send(new TriggerDeleteCmd(id));
    }

}
