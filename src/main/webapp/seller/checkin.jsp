<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    String msg = request.getParameter("msg");
    String orderNo = request.getParameter("orderNo");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>检票台 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body class="desk">
<%@ include file="/WEB-INF/includes/seller-topbar.jspf" %>
<div class="desk-layout">
    <%@ include file="/WEB-INF/includes/seller-nav.jspf" %>
    <main class="desk-main">
        <div class="page-kicker"><span class="ico ico-check ico-sm"></span>检票登船</div>
        <h2>检票登船</h2>
        <div class="scan-box">
            <h3><span class="ico ico-ticket"></span>扫描或输入订单号检票</h3>
            <p class="muted">输入乘客的订单号（如 FD2026112500001），确认后完成检票登船。</p>
            <% if (msg != null && !msg.isEmpty()) { %>
                <div class="msg <%= msg.startsWith("检票失败") ? "err" : "ok" %>"><span class="ico ico-info ico-sm"></span><span><%= msg %></span></div>
            <% } %>
            <form method="post" action="${pageContext.request.contextPath}/seller/checkin" id="checkinForm">
                <input type="text" name="orderNo" placeholder="请输入订单号" value="<%= orderNo == null ? "" : orderNo %>" autofocus>
                <button class="btn-primary"><span class="ico ico-check"></span>确认检票（Enter）</button>
            </form>
            <div class="notice-line"><span class="ico ico-info ico-sm"></span><span>校验规则：订单须已支付、未退票、未重复检票。</span></div>
        </div>
    </main>
</div>
</body>
</html>
