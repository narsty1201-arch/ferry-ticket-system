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
<%@ include file="/WEB-INF/includes/admin-topbar.jspf" %>
<div class="admin-layout">
    <%@ include file="/WEB-INF/includes/admin-nav.jspf" %>
    <main class="admin-main">
        <div class="page-kicker"><span class="ico ico-grid ico-sm"></span>运营总览</div>
        <h2>数据概览</h2>
        <p class="muted">集中管理航线、船舶、班次、用户与订单，实时业务统计请查看统计报表。</p>
        <div class="card-grid">
            <a class="action-card" href="route">
                <span class="ico ico-route"></span>
                <b>航线与船舶</b>
                <span>维护运营航线、船舶信息与座位数量。</span>
            </a>
            <a class="action-card" href="schedule">
                <span class="ico ico-calendar"></span>
                <b>班次管理</b>
                <span>发布班次、调整状态并处理取消退票。</span>
            </a>
            <a class="action-card" href="stats">
                <span class="ico ico-chart"></span>
                <b>统计报表</b>
                <span>查看营收、上座率和售票员业绩。</span>
            </a>
        </div>
        <div class="kpi-row">
            <div class="card kpi"><span>快捷入口</span><div style="font-size:13px;margin-top:7px"><a href="route">航线</a> · <a href="ship">船舶</a> · <a href="schedule">班次</a></div></div>
            <div class="card kpi"><span>统计数据</span><div style="font-size:13px;margin-top:7px"><a href="stats">营收 / 上座率 / 业绩</a></div></div>
            <div class="card kpi"><span>检票管理</span><div style="font-size:13px;margin-top:7px"><a href="${pageContext.request.contextPath}/admin/checkin">检票记录与统计</a></div></div>
        </div>
    </main>
</div>
</body>
</html>
