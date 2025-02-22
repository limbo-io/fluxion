/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/fluxion-io).
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

package io.fluxion.server.infrastructure.tag.handler;

import io.fluxion.server.infrastructure.dao.entity.TagEntity;
import io.fluxion.server.infrastructure.dao.repository.TagEntityRepo;
import io.fluxion.server.infrastructure.tag.Tag;
import io.fluxion.server.infrastructure.tag.TagRefType;
import io.fluxion.server.infrastructure.tag.cmd.TagBatchSaveCmd;
import io.fluxion.server.infrastructure.tag.cmd.TagCreateCmd;
import org.apache.commons.collections4.CollectionUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Devil
 */
@Service
public class TagCommandService {

    @Resource
    private TagEntityRepo tagEntityRepo;

    @CommandHandler
    public void handle(TagCreateCmd cmd) {
        TagEntity entity = toEntity(cmd.getRefId(), cmd.getRefType(), cmd.getTag());
        tagEntityRepo.saveAndFlush(entity);
    }

    @CommandHandler
    public void handle(TagBatchSaveCmd cmd) {
        String refId = cmd.getRefId();
        TagRefType refType = cmd.getRefType();
        tagEntityRepo.deleteById_RefIdAndId_RefType(refId, refType.value);
        if (CollectionUtils.isEmpty(cmd.getTags())) {
            return;
        }
        List<TagEntity> entities = cmd.getTags().stream().map(t -> toEntity(refId, refType, t))
            .collect(Collectors.toList());
        tagEntityRepo.saveAllAndFlush(entities);
    }


    private TagEntity toEntity(String refId, TagRefType refType, Tag tag) {
        TagEntity tagEntity = new TagEntity();
        tagEntity.setId(toId(refId, refType, tag));
        return tagEntity;
    }

    private TagEntity.ID toId(String refId, TagRefType refType, Tag tag) {
        return new TagEntity.ID(
            refId,
            refType.value,
            tag.getName(),
            tag.getValue()
        );
    }

}
