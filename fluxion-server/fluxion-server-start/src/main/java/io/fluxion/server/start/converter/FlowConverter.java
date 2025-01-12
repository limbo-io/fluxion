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

package io.fluxion.server.start.converter;

import io.fluxion.server.infrastructure.dao.entity.FlowEntity;
import io.fluxion.server.start.api.flow.view.FlowView;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
public class FlowConverter {

    public static List<FlowView> toView(List<FlowEntity> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }
        return entities.stream().map(FlowConverter::toView).collect(Collectors.toList());
    }

    public static FlowView toView(FlowEntity entity) {
        FlowView flowView = new FlowView();
        flowView.setId(entity.getFlowId());
        flowView.setName(entity.getName());
        flowView.setDescription(entity.getDescription());
        flowView.setRunVersion(entity.getRunVersion());
        flowView.setDraftVersion(entity.getDraftVersion());
        return flowView;
    }

}
