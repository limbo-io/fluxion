/*
 * Copyright 2025-2030 fluxion-io Team (https://github.com/limbo-io).
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

package io.fluxion.test.integration.cluster.support;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读 SQL 观测通道。断言与快照只读，不做任何写/改；UTC 会话与生产同口径。
 *
 * <p>datetime(3) 列返回两种形态：ISO 字符串（原名）与 epoch millis（原名 + "_ms"），
 * 以便测试做时间比较，同时保留人类可读证据。</p>
 */
public class Db implements AutoCloseable {

    private final String url;
    private final String username;
    private final String password;
    private final Connection connection;

    public Db(String url, String username, String password) throws SQLException {
        this.url = url;
        this.username = username;
        this.password = password;
        this.connection = DriverManager.getConnection(url, username, password);
        try (PreparedStatement ps = connection.prepareStatement("set names utf8mb4; set time_zone='+00:00'")) {
            ps.execute();
        }
    }

    public List<Map<String, Object>> query(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int col = 1; col <= meta.getColumnCount(); col++) {
                        String name = meta.getColumnLabel(col);
                        Object value = value(rs, col, meta.getColumnType(col));
                        if (value instanceof java.time.Instant) {
                            java.time.Instant instant = (java.time.Instant) value;
                            row.put(name + "_ms", instant.toEpochMilli());
                            row.put(name, DateTimeFormatter.ISO_INSTANT.format(instant));
                        } else {
                            row.put(name, value);
                        }
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private static Object value(ResultSet rs, int col, int jdbcType) throws SQLException {
        switch (jdbcType) {
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
            case Types.DATE:
            case Types.TIME:
                // 直读存储墙钟（库内约定 UTC），不能用 getTimestamp：
                // 它会把会话时区值换算成 JVM 默认时区返回，非 UTC 宿主机上产生 ± 时区位移
                LocalDateTime ldt = rs.getObject(col, LocalDateTime.class);
                return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
            case Types.BIT:
            case Types.BOOLEAN:
                boolean bool = rs.getBoolean(col);
                return rs.wasNull() ? null : bool;
            case Types.BIGINT:
                long lng = rs.getLong(col);
                return rs.wasNull() ? null : lng;
            case Types.INTEGER:
            case Types.SMALLINT:
            case Types.TINYINT:
                int intVal = rs.getInt(col);
                return rs.wasNull() ? null : intVal;
            case Types.FLOAT:
            case Types.DOUBLE:
            case Types.REAL:
            case Types.NUMERIC:
            case Types.DECIMAL:
                return rs.getBigDecimal(col);
            case Types.VARBINARY:
            case Types.BINARY:
            case Types.LONGVARBINARY:
                byte[] bytes = rs.getBytes(col);
                return bytes == null ? null : MessageDigest.isEqual(bytes, new byte[0]) ? "" : hex(bytes);
            default:
                Object obj = rs.getObject(col);
                return obj == null ? null : String.valueOf(obj);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder("0x");
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public String url() {
        return url;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignore) {
            // 只读连接，关闭失败不影响清理
        }
    }
}