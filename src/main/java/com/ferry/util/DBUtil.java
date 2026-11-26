package com.ferry.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** 数据库连接工具：照搬旧项目 DBUtil 的写法，仅把库名改为 FerSWryDB */
public class DBUtil {
    private static final String DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final String URL =
            "jdbc:sqlserver://localhost:1433;DatabaseName=FerryDB;encrypt=true;trustServerCertificate=true;";
    // 复用旧项目中已验证可用的 sa 账号（实践课环境）
    private static final String USER = "sa";
    private static final String PWD = "zlj463294220";

    static {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PWD);
    }

    public static void close(AutoCloseable... resources) {
        for (AutoCloseable r : resources) {
            if (r != null) {
                try { r.close(); } catch (Exception ignored) { }
            }
        }
    }
}
