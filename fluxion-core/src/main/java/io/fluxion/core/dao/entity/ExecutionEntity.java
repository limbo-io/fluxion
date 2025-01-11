package io.fluxion.core.dao.entity;

import io.fluxion.core.dao.TableConstants;
import io.fluxion.core.trigger.Trigger;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * 执行记录
 *
 * @author Devil
 * @since 2021/9/1
 */
@Setter
@Getter
@Table(name = TableConstants.FLUXION_EXECUTION)
@Entity
@DynamicInsert
@DynamicUpdate
public class ExecutionEntity extends BaseEntity {

    @Id
    private String executionId;

    private String refId;

    /**
     * @see Trigger.RefType
     */
    private String refType;

    private String version;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 属性参数
     */
    protected String attributes;

    /**
     * 期望的调度触发时间
     */
    private LocalDateTime triggerAt;

    /**
     * 执行开始时间
     */
    private LocalDateTime startAt;

    /**
     * 执行结束时间
     */
    private LocalDateTime feedbackAt;

    @Override
    public Object getUid() {
        return executionId;
    }
}
