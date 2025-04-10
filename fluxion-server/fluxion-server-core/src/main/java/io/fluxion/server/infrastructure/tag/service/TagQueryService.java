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

package io.fluxion.server.infrastructure.tag.service;

import com.google.common.collect.Maps;
import io.fluxion.server.infrastructure.dao.entity.TagEntity;
import io.fluxion.server.infrastructure.dao.repository.TagEntityRepo;
import io.fluxion.server.infrastructure.tag.Tag;
import io.fluxion.server.infrastructure.tag.query.TagsByRefsQuery;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Service
public class TagQueryService {

    @Resource
    private TagEntityRepo tagEntityRepo;

    @QueryHandler
    public TagsByRefsQuery.Response handle(TagsByRefsQuery query) {
        List<TagEntity> tagEntities = tagEntityRepo.findById_RefIdInAndId_RefType(query.getRefIds(), query.getRefType().value);
        Map<String, List<TagEntity>> refEntities = tagEntities.stream().collect(Collectors.groupingBy(e -> e.getId().getRefId()));
        return new TagsByRefsQuery.Response(Maps.transformValues(
            refEntities,
            list -> list.stream()
                .map(e -> new Tag(e.getId().getTagName(), e.getId().getTagValue()))
                .collect(Collectors.toList())
        ));
    }
}
