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

package io.fluxion.server.core.schedule.service;

import com.google.common.collect.Lists;
import io.fluxion.common.utils.time.TimeUtils;
import io.fluxion.server.core.broker.BrokerContext;
import io.fluxion.server.core.broker.query.BucketsByBrokerQuery;
import io.fluxion.server.core.schedule.ScheduleDelay;
import io.fluxion.server.core.schedule.ScheduleDelayConstants;
import io.fluxion.server.core.schedule.converter.ScheduleDelayEntityConverter;
import io.fluxion.server.core.schedule.query.ScheduleDelayNextCleanQuery;
import io.fluxion.server.core.schedule.query.ScheduleDelayNextTriggerQuery;
import io.fluxion.server.infrastructure.cqrs.Query;
import io.fluxion.server.infrastructure.dao.entity.ScheduleDelayEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Devil
 */
@Slf4j
@Service
public class ScheduleDelayQueryService {

    @Resource
    private EntityManager entityManager;

    @QueryHandler
    public ScheduleDelayNextTriggerQuery.Response handle(ScheduleDelayNextTriggerQuery query) {
        String brokerId = BrokerContext.broker().id();
        List<Integer> buckets = Query.query(new BucketsByBrokerQuery(brokerId)).getBuckets();
        LocalDateTime nextTriggerAt = TimeUtils.currentLocalDateTime().plusSeconds(ScheduleDelayConstants.LOAD_INTERVAL_SECONDS);
        List<ScheduleDelayEntity> entities = entityManager.createQuery("select e from ScheduleDelayEntity e" +
                " where e.bucket in :buckets and e.id.triggerAt <= :triggerAt and delayId > :lastDelayId " +
                " and e.status = :status and e.deleted = false order by id.triggerAt, delayId asc ", ScheduleDelayEntity.class
            )
            .setParameter("buckets", buckets)
            .setParameter("lastDelayId", query.getLastDelayId())
            .setParameter("triggerAt", nextTriggerAt)
            .setParameter("status", ScheduleDelay.Status.INIT.value)
            .setMaxResults(query.getLimit())
            .getResultList();
        return new ScheduleDelayNextTriggerQuery.Response(ScheduleDelayEntityConverter.convert(entities));
    }

    @QueryHandler
    public ScheduleDelayNextCleanQuery.Response handle(ScheduleDelayNextCleanQuery query) {
        String brokerId = BrokerContext.broker().id();
        List<Integer> buckets = Query.query(new BucketsByBrokerQuery(brokerId)).getBuckets();
        List<ScheduleDelayEntity> entities = entityManager.createQuery("select e from ScheduleDelayEntity e" +
                " where e.bucket in :buckets and e.id.triggerAt <= :triggerAt and delayId > :lastDelayId " +
                " order by id.triggerAt, delayId asc ", ScheduleDelayEntity.class
            )
            .setParameter("buckets", buckets)
            .setParameter("lastDelayId", query.getLastDelayId())
            .setParameter("triggerAt", query.getEndAt())
            .setMaxResults(query.getLimit())
            .getResultList();
        return new ScheduleDelayNextCleanQuery.Response(ScheduleDelayEntityConverter.convert(entities));
    }

}
