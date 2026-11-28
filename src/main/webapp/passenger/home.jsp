<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>乘客端 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body class="mobile">
<div class="phone">
    <div class="phone-head">⛴ 轮渡码头 · 乘客端</div>
    <div class="phone-body">
        <h2>你好，${sessionScope.userName}</h2>
        <p class="muted">查询班次、选座购票、支付与退票。</p>
        <a href="${pageContext.request.contextPath}/passenger/schedules"><div class="big-btn">🎫 查询班次 · 购票</div></a>
        <a href="orders"><div class="big-btn ghost">📋 我的订单</div></a>
    </div>
    <div class="tabbar">
        <a href="${pageContext.request.contextPath}/passenger/home"><span class="active">🏠 首页</span></a>
        <a href="${pageContext.request.contextPath}/passenger/orders"><span>📋 订单</span></a>
        <a href="${pageContext.request.contextPath}/passenger/profile"><span>👤 我的</span></a>
    </div>
</div>
</body>
</html>
