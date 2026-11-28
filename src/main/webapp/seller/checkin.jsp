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
            <p class="muted">输入订单号检完整单；也可以填写船票 ID 只检其中一张。检票窗口为发船前 60 分钟至发船后 10 分钟。</p>
            <% if (msg != null && !msg.isEmpty()) { %>
                <div class="msg <%= msg.startsWith("检票失败") ? "err" : "ok" %>"><span class="ico ico-info ico-sm"></span><span><%= msg %></span></div>
            <% } %>
            <form method="post" action="${pageContext.request.contextPath}/seller/checkin" id="checkinForm">
                <input type="text" name="orderNo" placeholder="请输入订单号" value="<%= orderNo == null ? "" : orderNo %>" autofocus>
                <input type="number" name="ticketId" min="1" placeholder="船票ID（可选）" style="margin-top:8px;width:100%">
                <button class="btn-primary"><span class="ico ico-check"></span>确认检票（Enter）</button>
            </form>
            <div class="notice-line"><span class="ico ico-info ico-sm"></span><span>校验规则：订单须已支付、未退票、未重复检票，且班次处于检票时间窗口；失败尝试会记录原因。</span></div>
        </div>
    </main>
</div>
</body>
</html>
