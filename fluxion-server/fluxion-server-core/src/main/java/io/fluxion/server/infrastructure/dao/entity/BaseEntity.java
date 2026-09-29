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

package io.fluxion.server.infrastructure.dao.entity;


import lombok.Getter;
import lombok.Setter;
import org.hibernate.Hibernate;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import java.time.Instant;
import java.util.Objects;

/**
 * @author Devil
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    /**
     * 记录创建时间；运行态绝对时间，应用侧生成（见 CONTEXT.md「统一时间模型」）。
     */
    private Instant createdAt;

    /**
     * 记录更新时间；运行态绝对时间，应用侧生成。
     */
    private Instant updatedAt;

    /**
     * 是否删除
     */
    @Column(name = "is_deleted")
    private boolean deleted;

    public abstract Object getUid();

    /**
     * createdAt/updatedAt 由数据库 DEFAULT CURRENT_TIMESTAMP 生成，取决于数据库会话时区；
     * 改为应用侧 UTC 生成，不再依赖会话时钟（见 CONTEXT.md「统一时间模型」）。
     */
    @PrePersist
    public void prePersistTimestamps() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdateTimestamps() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) {
            return false;
        }

        BaseEntity that = (BaseEntity) o;
        return getUid() != null && Objects.equals(getUid(), that.getUid());
    }


    @Override
    public int hashCode() {
        return Objects.hashCode(getUid());
    }
}
