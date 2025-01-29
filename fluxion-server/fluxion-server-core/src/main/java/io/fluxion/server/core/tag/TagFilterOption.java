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
package io.fluxion.server.core.tag;

import lombok.*;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 根据标签过滤
 *
 * @author Brozen
 * @since 2022-12-14
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter(AccessLevel.NONE)
@Getter
public class TagFilterOption {

    /**
     * 标签名
     */
    private String tagName;

    /**
     * 标签值
     */
    private String tagValue;

    /**
     * 匹配条件
     */
    private TagFilterCondition condition;


    /**
     * 过滤 Worker，判断是否符合条件。
     */
    public Predicate<Tagged> asPredicate() {
        return tagged -> {
            Map<String, Set<String>> tags = tagged.tags();

            Set<String> values = tags.get(this.tagName);
            switch (this.condition) {
                case EXISTS:
                    return CollectionUtils.isNotEmpty(values);

                case NOT_EXISTS:
                    return CollectionUtils.isEmpty(values);

                case MUST_MATCH_VALUE:
                    return CollectionUtils.isNotEmpty(values) && values.contains(this.tagValue);

                case MUST_NOT_MATCH_VALUE:
                    return CollectionUtils.isNotEmpty(values) && !values.contains(this.tagValue);

                case MUST_MATCH_VALUE_REGEX:
                    Pattern pattern = Pattern.compile(this.tagValue);
                    return CollectionUtils.isNotEmpty(values) && values.stream().anyMatch(s -> pattern.matcher(s).find());

                default:
                    return false;
            }
        };
    }

}
