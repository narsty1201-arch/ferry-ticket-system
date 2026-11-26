<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.util.DBUtil, java.sql.*" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head><meta charset="UTF-8"><title>数据库连通性测试</title></head>
<body>
<h2>数据库连通性测试</h2>
<%
    try (Connection conn = DBUtil.getConnection();
         Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM [user]")) {
        if (rs.next()) {
            out.println("<p style='color:green'>✅ 已连接 FerryDB，用户表记录数：" + rs.getInt(1) + "</p>");
        }
    } catch (Exception e) {
        out.println("<p style='color:red'>❌ 连接失败：" + e.getMessage() + "</p>");
        e.printStackTrace(new java.io.PrintWriter(out));
    }
%>
<p><a href="login.jsp">返回登录页</a></p>
</body>
</html>
