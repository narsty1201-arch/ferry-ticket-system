<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    String error = (String) request.getAttribute("error");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>乘客注册 - 轮渡码头客运管理系统</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body class="login-page">
<div class="login-card">
    <h1>乘客注册</h1>
    <% if (error != null) { %><div class="msg err"><%= error %></div><% } %>
    <form action="${pageContext.request.contextPath}/register" method="post">
        <label>用户名<input type="text" name="username" required placeholder="4-20位字母数字下划线"></label>
        <label>密码<input type="password" name="password" required placeholder="至少6位"></label>
        <label>真实姓名<input type="text" name="realName" required></label>
        <label>手机号<input type="text" name="phone" required placeholder="11位手机号"></label>
        <label>身份证号<input type="text" name="idCard" required placeholder="18位身份证号"></label>
        <button type="submit" class="btn-primary btn-block">注 册</button>
    </form>
    <p class="footer-link"><a href="login.jsp">← 返回登录</a></p>
</div>
</body>
</html>
