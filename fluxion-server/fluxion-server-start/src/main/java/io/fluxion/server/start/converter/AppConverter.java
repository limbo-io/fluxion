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

import io.fluxion.server.infrastructure.dao.entity.AppEntity;
import io.fluxion.server.start.api.app.view.AppView;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
public class AppConverter {

    public static List<AppView> toView(List<AppEntity> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }
        return entities.stream().map(AppConverter::toView).collect(Collectors.toList());
    }

    public static AppView toView(AppEntity entity) {
        AppView appView = new AppView();
        appView.setAppId(entity.getAppId());
        appView.setAppName(entity.getAppName());
        return appView;
    }

}
