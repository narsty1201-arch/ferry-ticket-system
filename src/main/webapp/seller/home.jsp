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
<%@ include file="/WEB-INF/includes/seller-topbar.jspf" %>
<div class="desk-layout">
    <%@ include file="/WEB-INF/includes/seller-nav.jspf" %>
    <main class="desk-main">
        <div class="page-kicker"><span class="ico ico-grid ico-sm"></span>售票员工作台</div>
        <h2>售票员工作台</h2>
        <p class="muted">代客选座购票、查看经办订单，以及为已支付乘客检票登船。</p>
        <div class="card-grid">
            <a class="action-card" href="${pageContext.request.contextPath}/seller/sell">
                <span class="ico ico-sale"></span>
                <b>进入售票台</b>
                <span>选择班次、座位并录入乘客信息，完成锁座下单。</span>
            </a>
            <a class="action-card" href="${pageContext.request.contextPath}/seller/orders">
                <span class="ico ico-orders"></span>
                <b>查看销售订单</b>
                <span>处理待支付订单、退票与可改签班次。</span>
            </a>
            <a class="action-card" href="${pageContext.request.contextPath}/seller/checkin">
                <span class="ico ico-check"></span>
                <b>进入检票台</b>
                <span>输入订单号，为已支付且未退票的乘客检票。</span>
            </a>
        </div>
        <div class="kpi-row">
            <div class="card kpi"><span>售票流程</span><div style="font-size:13px;margin-top:7px;color:var(--text-2)">选班次 → 选座 → 填乘客 → 锁座 → 支付</div></div>
            <div class="card kpi"><span>检票流程</span><div style="font-size:13px;margin-top:7px;color:var(--text-2)">输入订单号 → 校验 → 检票登船</div></div>
            <div class="card kpi"><span>校验规则</span><div style="font-size:13px;margin-top:7px;color:var(--text-2)">已支付 / 未退票 / 不重复</div></div>
        </div>
    </main>
</div>
</body>
</html>
