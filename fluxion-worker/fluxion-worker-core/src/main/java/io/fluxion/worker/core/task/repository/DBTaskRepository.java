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
import io.fluxion.remote.core.api.request.worker.TaskPageRequest;
import io.fluxion.remote.core.cluster.BaseNode;
import io.fluxion.remote.core.cluster.Node;
import io.fluxion.remote.core.constants.Protocol;
import io.fluxion.remote.core.constants.TaskStatus;
import io.fluxion.worker.core.WorkerContext;
import io.fluxion.worker.core.persistence.ConnectionFactory;
import io.fluxion.worker.core.task.Task;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Devil
 * @since 2023/8/3
 */
public class DBTaskRepository implements TaskRepository {

    private static final Logger log = LoggerFactory.getLogger(DBTaskRepository.class);

    private static final LocalDateTime DEFAULT_REPORT_TIME = LocalDateTimeUtils.parse("2000-01-01 00:00:00", Formatters.YMD_HMS);

    private static final String DEFAULT_REPORT_TIME_STR = LocalDateTimeUtils.formatYMDHMSS(DEFAULT_REPORT_TIME);

    private final ConnectionFactory connectionFactory;

    private final WorkerContext workerContext;

    private static final String TABLE_NAME = "fluxion_task";

    public DBTaskRepository(ConnectionFactory connectionFactory, WorkerContext workerContext) {
        this.connectionFactory = connectionFactory;
        this.workerContext = workerContext;
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
                "    `start_at`          datetime(3) DEFAULT NULL,\n" +
                "    `end_at`            datetime(3) DEFAULT NULL,\n" +
                "    `last_report_at`    datetime(3) DEFAULT NULL," +
                "    `result`            text DEFAULT NULL,\n" +
                "    `error_msg`         varchar(255) DEFAULT '',\n" +
                "    `error_stack_trace` text DEFAULT NULL,\n" +
                "    `created_at`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                "    `updated_at`        datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                "    PRIMARY KEY (`id`),\n" +
                "    UNIQUE INDEX       `idx_job_task` (`job_id`, `task_id`),\n" +
                "    INDEX              `idx_report_task` (`last_report_at`, `task_id`)\n" +
                ")";
            stat.execute(createSql);
        }
    }

    @Override
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

    @Override
    public Map<String, String> getAllSubTaskResult(String jobId) {
        String sql = "select task_id, result from " + TABLE_NAME + " where job_id = ? ";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, String> results = new HashMap<>();
                while (rs.next()) {
                    results.put(rs.getString("task_id"), rs.getString("result"));
                }
                return results;
            }
        } catch (Exception e) {
            log.error("TaskRepository.getAllTaskResult error jobId={}", jobId, e);
            return Collections.emptyMap();
        }
    }

    @Override
    public boolean dispatched(String jobId, String taskId, String workerAddress) {
        String sql = "update " + TABLE_NAME + " set `status` = ? " +
            " where job_id = ? and task_id = ? and `status` = ? and `worker_node` = ? ";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 0;
            ps.setString(++i, TaskStatus.DISPATCHED.value);
            ps.setString(++i, jobId);
            ps.setString(++i, taskId);
            ps.setString(++i, TaskStatus.CREATED.value);
            ps.setString(++i, workerAddress);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("TaskRepository.executing error jobId:{} taskId:{} workerAddress:{}", jobId, taskId, workerAddress, e);
            return false;
        }
    }

    @Override
    public boolean batchSave(Collection<Task> tasks) {
        if (CollectionUtils.isEmpty(tasks)) {
            return true;
        }
        List<String> values = new ArrayList<>();
        for (Task task : tasks) {
            values.add(" (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
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
                ps.setString(++idx, task.workerAddress() == null ? "" : task.workerAddress());
                ps.setString(++idx, task.getStatus().value);

                ps.setString(++idx, task.getStartAt() == null ? null : LocalDateTimeUtils.formatYMDHMSS(task.getStartAt()));
                ps.setString(++idx, task.getEndAt() == null ? null : LocalDateTimeUtils.formatYMDHMSS(task.getEndAt()));
                ps.setString(++idx, task.getLastReportAt() == null ? null : LocalDateTimeUtils.formatYMDHMSS(task.getLastReportAt()));

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

    @Override
    public boolean start(String jobId, String taskId, String workerAddress, LocalDateTime reportAt) {
        String sql = "update " + TABLE_NAME + " set `status` = ?, start_at = ?, last_report_at = ? " +
            " where job_id = ? and task_id = ? and `status` = ? and worker_address = ? ";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 0;
            String reportAtStr = LocalDateTimeUtils.formatYMDHMSS(reportAt);
            ps.setString(++i, TaskStatus.RUNNING.value);
            ps.setString(++i, reportAtStr);
            ps.setString(++i, reportAtStr);
            ps.setString(++i, jobId);
            ps.setString(++i, taskId);
            ps.setString(++i, TaskStatus.DISPATCHED.value);
            ps.setString(++i, workerAddress);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("TaskRepository.executing error jobId:{} taskId:{} workerAddress:{} reportTime:{}", jobId, taskId, workerAddress, reportAt, e);
            return false;
        }
    }

    @Override
    public boolean report(String jobId, String taskId, TaskStatus status, String workerAddress, LocalDateTime reportAt) {
        String sql = "update " + TABLE_NAME + " set `last_report_at` = ? " +
            " where job_id = ? and task_id = ? and status = ? and `worker_address` = ? ";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 0;
            ps.setString(++i, LocalDateTimeUtils.formatYMDHMSS(reportAt));
            ps.setString(++i, jobId);
            ps.setString(++i, taskId);
            ps.setString(++i, status.value);
            ps.setString(++i, workerAddress);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("TaskRepository.report error jobId:{} taskId:{} status:{} workerAddress:{} reportTime:{}", jobId, taskId, status, workerAddress, reportAt, e);
            return false;
        }
    }

    @Override
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

    @Override
    public boolean fail(Task task) {
        String sql = "update " + TABLE_NAME + " set `status` = ?, end_at = ?, error_msg = ?, error_stack_trace = ? " +
            "where job_id = ? and task_id = ? and `status` in (?, ?) ";
        try (Connection conn = connectionFactory.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 0;
            ps.setString(++i, TaskStatus.FAILED.value);
            ps.setString(++i, LocalDateTimeUtils.formatYMDHMSS(task.getLastReportAt()));
            ps.setString(++i, task.getErrorMsg() == null ? "" : task.getErrorMsg());
            ps.setString(++i, task.getErrorStackTrace() == null ? "" : task.getErrorStackTrace());
            ps.setString(++i, task.getJobId());
            ps.setString(++i, task.getId());
            ps.setString(++i, TaskStatus.CREATED.value);
            ps.setString(++i, TaskStatus.RUNNING.value);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("TaskRepository.fail error task={}", task, e);
            return false;
        }
    }

    private Task convert(ResultSet rs) throws SQLException {
        Timestamp startAt = rs.getTimestamp("start_at");
        Timestamp endAt = rs.getTimestamp("end_at");
        Timestamp lastReportAt = rs.getTimestamp("last_report_at");

        Task task = new Task(rs.getString("task_id"), rs.getString("job_id"));
        task.setStatus(TaskStatus.parse(rs.getString("status")));
        task.setWorkerNode(addressToNode(rs.getString("worker_address")));
        task.setRemoteNode(workerContext.node());
        task.setStartAt(startAt == null ? null : startAt.toLocalDateTime());
        task.setEndAt(endAt == null ? null : endAt.toLocalDateTime());
        task.setLastReportAt(lastReportAt == null ? null : lastReportAt.toLocalDateTime());
        task.setResult(rs.getString("result"));
        task.setErrorMsg(rs.getString("error_msg"));
        task.setErrorStackTrace(rs.getString("error_stack_trace"));
        return task;
    }

    private Node addressToNode(String address) {
        if (StringUtils.isBlank(address)) {
            return null;
        }
        try {
            URL url = new URL(address);
            return new BaseNode(Protocol.parse(url.getProtocol()), url.getHost(), url.getPort());
        } catch (MalformedURLException e) {
            log.error("addressToNode fail {}", address, e);
            return null;
        }
    }

}
