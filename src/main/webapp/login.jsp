<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    String error = (String) request.getAttribute("error");
    String username = (String) request.getAttribute("username");
    if (username == null) username = "";
    boolean registered = "1".equals(request.getParameter("registered"));
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>登录 - 轮渡码头客运管理系统</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body class="login-page">
<div class="login-card">
    <h1>⛴ 轮渡码头客运管理系统</h1>
    <p class="subtitle">请登录以继续使用</p>
    <% if (registered) { %><div class="msg ok">注册成功，请登录</div><% } %>
    <% if (error != null) { %><div class="msg err"><%= error %></div><% } %>
    <form action="${pageContext.request.contextPath}/login" method="post">
        <label>用户名<input type="text" name="username" value="<%= username %>" required autofocus></label>
        <label>密码<input type="password" name="password" required></label>
        <button type="submit" class="btn-primary btn-block">登 录</button>
    </form>
    <p class="footer-link">还没有账号？<a href="register.jsp">乘客注册</a></p>
    <div class="hint">
        <b>演示账号</b>（密码均为 123456）<br>
        管理员：admin　售票员：seller1　乘客：pass1
    </div>
</div>
</body>
</html>
