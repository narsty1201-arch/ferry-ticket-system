<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>管理后台 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body class="admin">
<header class="admin-head">🚢 码头管理系统 · 管理后台　管理员：${sessionScope.userName}
    <a class="logout" href="${pageContext.request.contextPath}/logout">退出</a>
</header>
<div class="admin-layout">
    <aside class="admin-menu">
        <div class="active">数据概览</div>
        <div><a href="route">航线管理</a></div>
        <div><a href="ship">船舶管理</a></div>
        <div><a href="schedule">班次管理</a></div>
        <div><a href="users">👥 用户管理</a></div>
        <div><a href="orders">📋 订单管理</a></div>
        <div><a href="stats">📊 统计报表</a></div>
        <div><a href="${pageContext.request.contextPath}/admin/checkin?action=records">检票记录</a></div>
    </aside>
    <main class="admin-main">
        <h2>数据概览</h2>
        <p class="muted">实时业务统计请查看 <a href="stats">📊 统计报表</a>（SQL 聚合查询生成）。</p>
        <div class="kpi-row">
            <div class="card kpi"><div class="muted">快捷入口</div><div style="font-size:14px;margin-top:6px"><a href="route">航线</a> · <a href="ship">船舶</a> · <a href="schedule">班次</a></div></div>
            <div class="card kpi"><div class="muted">统计数据</div><div style="font-size:14px;margin-top:6px"><a href="stats">营收 / 上座率 / 业绩</a></div></div>
            <div class="card kpi"><div class="muted">检票管理</div><div style="font-size:14px;margin-top:6px"><a href="${pageContext.request.contextPath}/seller/checkin">售票员检票台</a></div></div>
        </div>
    </main>
</div>
</body>
</html>
