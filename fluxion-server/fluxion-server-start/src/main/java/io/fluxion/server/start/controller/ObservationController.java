/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/Fluxion-io).
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

import io.fluxion.server.start.api.observation.view.ObservationOverviewView;
import io.fluxion.server.start.service.ObservationService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 存量观测接口：诊断用快照，对齐 docs/guides/operations.md「可观测性现状」。
 *
 * @author Devil
 */
@RestController
public class ObservationController {

    @Resource
    private ObservationService observationService;

    @RequestMapping("/api/v1/observation/overview")
    public ObservationOverviewView overview() {
        return observationService.overview();
    }
}