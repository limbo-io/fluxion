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

package io.fluxion.worker.core.task.repository;

import io.fluxion.common.utils.time.Formatters;
import io.fluxion.common.utils.time.LocalDateTimeUtils;
import io.fluxion.remote.core.api.request.TaskPageRequest;
import io.fluxion.remote.core.constants.ExecuteMode;
import io.fluxion.remote.core.constants.TaskStatus;
import io.fluxion.worker.core.persistence.ConnectionFactory;
import io.fluxion.worker.core.task.Task;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Devil
 * @since 2023/8/3
 */
public class DBTaskRepository implements TaskRepository {

    private static final Logger log = LoggerFactory.getLogger(DBTaskRepository.class);

    private static final LocalDateTime DEFAULT_REPORT_TIME = LocalDateTimeUtils.parse("2000-01-01 00:00:00", Formatters.YMD_HMS);

    private static final String DEFAULT_REPORT_TIME_STR = LocalDateTimeUtils.formatYMDHMS(DEFAULT_REPORT_TIME);

    private final ConnectionFactory connectionFactory;

    private static final String TABLE_NAME = "fluxion_task";

    public DBTaskRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public boolean existTable() throws SQLException {
        try (Connection conn = connectionFactory.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(null, null, TABLE_NAME, null);
            return tables.next();
        }
    }

    public void initTable() throws SQLException {
        try (Connection conn = connectionFactory.getConnection(); Statement stat = conn.createStatement()) {
            String dropSql = "DROP TABLE IF EXISTS `" + TABLE_NAME + "`;";
            stat.execute(dropSql);

            String createSql = "CREATE TABLE `" + TABLE_NAME + "`\n" +
                "(\n" +
                "    `id`                bigint unsigned NOT NULL AUTO_INCREMENT,\n" +
                "    `task_id`           varchar(255) NOT NULL DEFAULT '',\n" +
                "    `job_id`            varchar(255) NOT NULL DEFAULT '',\n" +
                "    `worker_address`    varchar(255) NOT NULL DEFAULT '',\n" +
                "    `status`            varchar(32)     NOT NULL,\n" +
                "    `trigger_at`        datetime(6) DEFAULT NULL,\n" +
                "    `start_at`          datetime(6) DEFAULT NULL,\n" +
                "    `end_at`            datetime(6) DEFAULT NULL,\n" +
                "    `last_report_at`    datetime(6) NOT NULL," +
                "    `result`            text DEFAULT NULL,\n" +
                "    `error_msg`         varchar(255) DEFAULT '',\n" +
                "    `error_stack_trace` text DEFAULT NULL,\n" +
                "    `created_at`        datetime                                               NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                "    `updated_at`        datetime                                               NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                "    PRIMARY KEY (`id`),\n" +
                "    UNIQUE INDEX       `idx_job_task` (`job_id`, `task_id`),\n" +
                "    INDEX              `idx_report_task` (`last_report_at`, `task_id`)\n" +
                ")";
            stat.execute(createSql);
        }
    }

    public Task getById(String jobId, String taskId) {
        String sql = "select * from " + TABLE_NAME + " where job_id = ? and task_id = ?";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jobId);
            ps.setString(2, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return convert(rs);
                } else {
                    return null;
                }
            }
        } catch (Exception e) {
            log.error("TaskRepository.getById error jobId={} taskId={}", jobId, taskId, e);
            return null;
        }
    }

    public Set<String> getIdsByTaskIds(String jobId, Collection<String> taskIds) {
        if (StringUtils.isBlank(jobId) || CollectionUtils.isEmpty(taskIds)) {
            return Collections.emptySet();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("select task_id from ").append(TABLE_NAME).append(" where job_id = ? and task_id in (");
        for (int i = 0; i < taskIds.size(); i++) {
            sb.append("?,");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(")");
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            int i = 0;
            ps.setString(++i, jobId);
            for (String taskId : taskIds) {
                ps.setString(++i, taskId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> existTaskIds = new HashSet<>();
                while (rs.next()) {
                    existTaskIds.add(rs.getString("task_id"));
                }
                return existTaskIds;
            }
        } catch (Exception e) {
            log.error("TaskRepository.getExistTaskIds error jobId={} taskIds={}", jobId, taskIds, e);
            return Collections.emptySet();
        }
    }

    public boolean deleteByJobId(String jobId) {
        String sql = "delete from " + TABLE_NAME + " where job_id = ? ";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jobId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("TaskRepository.deleteByJobId error jobId={} ", jobId, e);
            return false;
        }
    }

    public List<Task> getUnDispatched(String triggerAt, String startId, Integer limit) {
        String sql = "select * from " + TABLE_NAME + " where last_report_at = ? and trigger_at < ? and status = ? and task_id > ? order by task_id limit ?";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 0;
            ps.setString(++i, DEFAULT_REPORT_TIME_STR);
            ps.setString(++i, triggerAt);
            ps.setString(++i, TaskStatus.CREATED.value);
            ps.setString(++i, startId);
            ps.setInt(++i, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Task> tasks = new ArrayList<>();
                while (rs.next()) {
                    tasks.add(convert(rs));
                }
                return tasks;
            }
        } catch (Exception e) {
            log.error("TaskRepository.getUnScheduled error startId={} limit={}", startId, limit, e);
            return Collections.emptyList();
        }
    }

    public List<Task> getByLastReportBetween(String reportTimeStart, String reportTimeEnd, TaskStatus status, String taskId, Integer limit) {
        String sql = "select * from " + TABLE_NAME + " where last_report_at >= ? and last_report_at <= ? and status = ? and task_id > ? order by task_id limit ?";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 0;
            ps.setString(++i, reportTimeStart);
            ps.setString(++i, reportTimeEnd);
            ps.setString(++i, status.value);
            ps.setString(++i, taskId);
            ps.setInt(++i, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Task> tasks = new ArrayList<>();
                while (rs.next()) {
                    tasks.add(convert(rs));
                }
                return tasks;
            }
        } catch (Exception e) {
            log.error("TaskRepository.getByLastReportAtBefore error reportTimeStart={} reportTimeEnd={} limit={}", reportTimeStart, reportTimeEnd, limit, e);
            return Collections.emptyList();
        }
    }

    public List<Task> getByJobId(String jobId) {
        String sql = "select * from " + TABLE_NAME;
        if (StringUtils.isNotBlank(jobId)) {
            sql += " where job_id = ?";
        }
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (StringUtils.isNotBlank(jobId)) {
                ps.setString(1, jobId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Task> tasks = new ArrayList<>();
                while (rs.next()) {
                    tasks.add(convert(rs));
                }
                return tasks;
            }
        } catch (Exception e) {
            log.error("TaskRepository.queryPage error ", e);
            return Collections.emptyList();
        }
    }

    public List<Task> queryPage(TaskPageRequest request) {
        String sql = "select * from " + TABLE_NAME + " where job_id = ? LIMIT ? OFFSET ?";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, request.getJobId());
            ps.setInt(2, request.getPageSize());
            ps.setInt(3, request.getOffset());
            try (ResultSet rs = ps.executeQuery()) {
                List<Task> tasks = new ArrayList<>();
                while (rs.next()) {
                    tasks.add(convert(rs));
                }
                return tasks;
            }
        } catch (Exception e) {
            log.error("TaskRepository.queryPage error request={}", request, e);
            return Collections.emptyList();
        }
    }

    public long queryCount(TaskPageRequest request) {
        String sql = "select count(*) from " + TABLE_NAME + " where job_id = ? ";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, request.getJobId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    return 0L;
                }
            }
        } catch (Exception e) {
            log.error("TaskRepository.queryCount error request={}", request, e);
            return 0L;
        }
    }

    public List<String> getAllTaskResult(String jobId, ExecuteMode executeMode) {
        String sql = "select result from " + TABLE_NAME + " where job_id = ? and `execute_mode` = ?";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jobId);
            ps.setString(2, executeMode.mode);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(rs.getString("result"));
                }
                return results;
            }
        } catch (Exception e) {
            log.error("TaskRepository.getAllTaskResult error jobId={} executeMode={}", jobId, executeMode, e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean dispatched(String jobId, String taskId, String workerAddress) {
        return false; // todo !
    }

    public boolean batchSave(Collection<Task> tasks) {
        if (CollectionUtils.isEmpty(tasks)) {
            return true;
        }
        List<String> values = new ArrayList<>();
        for (Task task : tasks) {
            values.add(" (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        }
        String sql = "insert into " + TABLE_NAME + "(" +
            "task_id, job_id, worker_address, " +
            "status, trigger_at, start_at, end_at, last_report_at, `result`, error_msg, error_stack_trace " +
            ") values " + StringUtils.join(values, ",");

        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 0;
            for (Task task : tasks) {
                ps.setString(++idx, task.getId());
                ps.setString(++idx, task.getJobId());
                ps.setString(++idx, task.getWorkerAddress());
                ps.setString(++idx, task.getStatus().value);

                ps.setString(++idx, task.getTriggerAt() == null ? null : LocalDateTimeUtils.formatYMDHMS(task.getTriggerAt()));
                ps.setString(++idx, task.getStartAt() == null ? null : LocalDateTimeUtils.formatYMDHMS(task.getStartAt()));
                ps.setString(++idx, task.getEndAt() == null ? null : LocalDateTimeUtils.formatYMDHMS(task.getEndAt()));
                ps.setString(++idx, LocalDateTimeUtils.formatYMDHMS(task.getLastReportAt()));

                ps.setString(++idx, task.getResult() == null ? "" : task.getResult());
                ps.setString(++idx, task.getErrorMsg() == null ? "" : task.getErrorMsg());
                ps.setString(++idx, task.getErrorStackTrace() == null ? "" : task.getErrorStackTrace());
            }
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("TaskRepository.batchSave error", e);
            throw new RuntimeException(e);
        }
    }

    public boolean dispatchFail(String jobId, String taskId) {
        String sql = "update " + TABLE_NAME + " set `dispatch_fail_times` = `dispatch_fail_times` + 1 where job_id = ? and task_id = ?";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 0;
            ps.setString(++i, jobId);
            ps.setString(++i, taskId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("TaskRepository.dispatchFail error jobId={} taskId={}", jobId, taskId, e);
            return false;
        }
    }

    public boolean start(Task task) {
        String sql = "update " + TABLE_NAME + " set `status` = ?, worker_address = ?, start_at = ?, last_report_at = ? " +
            " where job_id = ? and task_id = ? and `status` = ? ";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 0;
            String reportAt = LocalDateTimeUtils.formatYMDHMS(task.getLastReportAt());
            ps.setString(++i, TaskStatus.RUNNING.value);
            ps.setString(++i, task.getWorkerAddress());
            ps.setString(++i, reportAt);
            ps.setString(++i, reportAt);
            ps.setString(++i, task.getJobId());
            ps.setString(++i, task.getId());
            ps.setString(++i, TaskStatus.DISPATCHED.value);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("TaskRepository.executing error task={}", task, e);
            return false;
        }
    }

    public boolean report(Task task) {
        String sql = "update " + TABLE_NAME + " set `last_report_at` = ? " +
            " where job_id = ? and task_id = ? and status = ? ";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 0;
            ps.setString(++i, LocalDateTimeUtils.formatYMDHMS(task.getLastReportAt()));
            ps.setString(++i, task.getJobId());
            ps.setString(++i, task.getId());
            ps.setString(++i, TaskStatus.RUNNING.value);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("TaskRepository.report error task={}", task, e);
            return false;
        }
    }

    public boolean success(Task task) {
        String sql = "update " + TABLE_NAME + " set `status` = ?, end_at = ?, `result` = ? " +
            " where job_id = ? and task_id = ? and `status` = ? ";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 0;
            ps.setString(++i, TaskStatus.SUCCEED.value);
            ps.setString(++i, LocalDateTimeUtils.formatYMDHMSS(task.getLastReportAt()));
            ps.setString(++i, task.getResult());
            ps.setString(++i, task.getJobId());
            ps.setString(++i, task.getId());
            ps.setString(++i, TaskStatus.RUNNING.value);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("TaskRepository.success error task={}", task, e);
            return false;
        }
    }

    public boolean fail(Task task) {
        String sql = "update " + TABLE_NAME + " set `status` = ?, start_at = ?, end_at = ?, error_msg = ?, error_stack_trace = ? " +
            "where job_id = ? and task_id = ? and `status` = ? ";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 0;
            String curTimeStr = LocalDateTimeUtils.formatYMDHMS(task.getLastReportAt());
            ps.setString(++i, TaskStatus.FAILED.value);
            ps.setString(++i, task.getStartAt() == null ? curTimeStr : LocalDateTimeUtils.formatYMDHMS(task.getStartAt()));
            ps.setString(++i, curTimeStr);
            ps.setString(++i, task.getErrorMsg() == null ? "" : task.getErrorMsg());
            ps.setString(++i, task.getErrorStackTrace() == null ? "" : task.getErrorStackTrace());
            ps.setString(++i, task.getJobId());
            ps.setString(++i, task.getId());
            ps.setString(++i, TaskStatus.RUNNING.value);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("TaskRepository.fail error task={}", task, e);
            return false;
        }
    }

    private Task convert(ResultSet rs) throws SQLException {
        Task task = new Task(rs.getString("task_id"), rs.getString("job_id"));
        task.setStatus(TaskStatus.parse(rs.getString("status")));
        task.setWorkerAddress(rs.getString("worker_address"));
        task.setTriggerAt(LocalDateTimeUtils.parseYMDHMS(rs.getString("trigger_at")));
        task.setStartAt(LocalDateTimeUtils.parseYMDHMS(rs.getString("start_at")));
        task.setEndAt(LocalDateTimeUtils.parseYMDHMS(rs.getString("end_at")));
        task.setLastReportAt(LocalDateTimeUtils.parseYMDHMS(rs.getString("last_report_at")));
        task.setResult(rs.getString("result"));
        task.setErrorMsg(rs.getString("error_msg"));
        task.setErrorStackTrace(rs.getString("error_stack_trace"));
        return task;
    }

}
