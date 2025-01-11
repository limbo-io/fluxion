package io.fluxion.core.dao.entity;

import io.fluxion.core.dao.TableConstants;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 一个任务 实例 存储运行数据
 *
 * @author Devil
 * @since 2021/9/1
 */
@Setter
@Getter
@Table(name = TableConstants.FLUXION_TASK)
@Entity
@DynamicInsert
@DynamicUpdate
public class TaskEntity extends BaseEntity {

    @Id
    private String taskId;

    private String executionId;

    private Integer instanceType;

    /**
     * 关联的 id
     * flow 中是nodeId
     * executor 则直接为其id
     */
    private String refId;


    /**
     * 计划时间
     */
    private LocalDateTime triggerAt;

    /**
     * 开始时间
     */
    private LocalDateTime startAt;

    /**
     * 结束时间
     */
    private LocalDateTime endAt;

    /**
     * 分配的负载节点 ip:host
     */
    private String brokerUrl;

    /**
     * 执行节点
     */
    private String workerId;

    /**
     * 上次上报时间戳，毫秒
     */
    private LocalDateTime lastReportAt;

    @Override
    public Object getUid() {
        return taskId;
    }
}
