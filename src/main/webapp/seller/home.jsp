<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>售票台 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body class="desk">
<div class="desk-head">码头售票系统 · 工作台　售票员：${sessionScope.userName}
    <a class="logout" href="${pageContext.request.contextPath}/logout">退出</a>
</div>
<div class="desk-layout">
    <aside class="desk-menu">
        <div class="active">工作台</div>
        <div onclick="location.href='${pageContext.request.contextPath}/seller/sell'">🎫 售票</div>
        <div onclick="location.href='${pageContext.request.contextPath}/seller/orders'">📋 销售订单</div>
        <div onclick="location.href='${pageContext.request.contextPath}/seller/checkin'">▶ 检票</div>
        <div onclick="location.href='${pageContext.request.contextPath}/seller/checkin?action=records'">检票记录</div>
        <div onclick="location.href='${pageContext.request.contextPath}/seller/checkin?action=stats'">检票统计</div>
    </aside>
    <main class="desk-main">
        <h2>售票员工作台</h2>
        <p class="muted">代客选座购票、查看经办订单，以及为已支付乘客检票登船。</p>
        <div style="margin-top:20px">
            <a class="btn-primary" style="display:inline-block;padding:12px 24px;text-decoration:none;margin-right:12px" href="${pageContext.request.contextPath}/seller/sell">🎫 进入售票台</a>
            <a class="btn-primary" style="display:inline-block;padding:12px 24px;text-decoration:none;margin-right:12px" href="${pageContext.request.contextPath}/seller/orders">📋 查看销售订单</a>
            <a class="btn-primary" style="display:inline-block;padding:12px 24px;text-decoration:none" href="${pageContext.request.contextPath}/seller/checkin">🎯 进入检票台</a>
        </div>
        <div class="kpi-row" style="margin-top:26px">
            <div class="card kpi"><div class="muted">售票流程</div><div style="font-size:14px;margin-top:6px">选班次 → 选座 → 填乘客 → 锁座 → 支付</div></div>
            <div class="card kpi"><div class="muted">检票流程</div><div style="font-size:14px;margin-top:6px">输入订单号 → 校验 → 检票登船</div></div>
            <div class="card kpi"><div class="muted">校验规则</div><div style="font-size:14px;margin-top:6px">已支付 / 未退票 / 不重复</div></div>
        </div>
    </main>
</div>
</body>
</html>
