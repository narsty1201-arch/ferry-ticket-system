package com.ferry.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * 数据库连接工具。
 *
 * 连接参数优先从 classpath:db.properties 读取，其次读取环境变量，
 * 便于把密码从源码中移出。项目只提交 db.properties.example，
 * 本地复制为 db.properties 后填写即可。
 */
public class DBUtil {
    private static final String DEFAULT_URL =
            "jdbc:sqlserver://localhost:1433;DatabaseName=FerryDB;encrypt=true;trustServerCertificate=true;";
    private static final String DEFAULT_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final Properties CONFIG = loadConfig();

    private static final String DRIVER = config("db.driver", "FERRY_DB_DRIVER", DEFAULT_DRIVER);
    private static final String URL = config("db.url", "FERRY_DB_URL", DEFAULT_URL);
    private static final String USER = config("db.user", "FERRY_DB_USER", "sa");
    private static final String PWD = config("db.password", "FERRY_DB_PASSWORD", null);
    private static final HikariDataSource DATA_SOURCE = createDataSource();

    private static HikariDataSource createDataSource() {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("找不到 SQL Server JDBC 驱动：" + DRIVER);
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("FerryPool");
        config.setDriverClassName(DRIVER);
        config.setJdbcUrl(URL);
        config.setUsername(USER);
        config.setPassword(PWD == null ? "" : PWD);
        config.setMaximumPoolSize(intConfig("db.pool.maxSize", "FERRY_DB_POOL_MAX", 10));
        config.setMinimumIdle(intConfig("db.pool.minIdle", "FERRY_DB_POOL_MIN", 2));
        config.setConnectionTimeout(longConfig("db.pool.connectionTimeoutMs",
                "FERRY_DB_POOL_TIMEOUT", 10000L));
        config.setIdleTimeout(600000L);
        config.setMaxLifetime(1800000L);
        config.setInitializationFailTimeout(-1L);
        return new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        if (PWD == null || PWD.trim().isEmpty()) {
            throw new SQLException("未配置数据库密码：请复制 src/main/resources/db.properties.example "
                    + "为 db.properties 并填写，或设置 FERRY_DB_PASSWORD 环境变量。");
        }
        return DATA_SOURCE.getConnection();
    }

    public static void shutdown() {
        if (DATA_SOURCE != null && !DATA_SOURCE.isClosed()) {
            DATA_SOURCE.close();
        }
    }

    public static void close(AutoCloseable... resources) {
        for (AutoCloseable r : resources) {
            if (r != null) {
                try { r.close(); } catch (Exception ignored) { }
            }
        }
    }

    private static Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream in = DBUtil.class.getResourceAsStream("/db.properties")) {
            if (in != null) props.load(in);
        } catch (Exception e) {
            throw new ExceptionInInitializerError("读取 db.properties 失败：" + e.getMessage());
        }
        return props;
    }

    private static String config(String key, String envKey, String defaultValue) {
        String value = CONFIG.getProperty(key);
        if (isBlank(value)) value = System.getProperty(key);
        if (isBlank(value)) value = System.getenv(envKey);
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static int intConfig(String key, String envKey, int defaultValue) {
        String value = config(key, envKey, null);
        try {
            return value == null ? defaultValue : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long longConfig(String key, String envKey, long defaultValue) {
        String value = config(key, envKey, null);
        try {
            return value == null ? defaultValue : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
