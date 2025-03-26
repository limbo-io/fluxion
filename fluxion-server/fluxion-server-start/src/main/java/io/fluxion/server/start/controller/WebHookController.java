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

import io.fluxion.server.core.workflow.cmd.WorkflowCreateCmd;
import io.fluxion.server.infrastructure.cqrs.Cmd;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Devil
 */
@RestController
public class WebHookController {

    @RequestMapping("/api/v1/webhook")
    public String trigger(@RequestBody WorkflowCreateCmd cmd) {
        WorkflowCreateCmd.Response response = Cmd.send(cmd);
        return response.getId();
    }


}
