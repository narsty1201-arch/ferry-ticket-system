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
    <style>
        .scan-box { max-width:560px; margin:30px auto; padding:26px; background:#fff; border-radius:14px; box-shadow:0 2px 12px rgba(0,0,0,.06); }
        .scan-box h3 { margin:0 0 6px; color:#1f2937; }
        .scan-box input { width:100%; box-sizing:border-box; font-size:18px; padding:12px 14px; border:2px solid #2563eb; border-radius:8px; margin:14px 0; }
        .scan-box button { width:100%; font-size:16px; padding:12px; }
        .msg.err { background:#fee2e2; color:#b91c1c; padding:10px 14px; border-radius:8px; margin-bottom:12px; }
        .msg.ok { background:#dcfce7; color:#15803d; padding:10px 14px; border-radius:8px; margin-bottom:12px; }
    </style>
</head>
<body class="desk">
<div class="desk-head">码头售票系统 · 检票模式　售票员：${sessionScope.userName}
    <a class="logout" href="${pageContext.request.contextPath}/logout">退出</a>
</div>
<div class="desk-layout">
    <aside class="desk-menu">
        <div class="active">▶ 检票</div>
        <div onclick="location.href='${pageContext.request.contextPath}/seller/checkin?action=records'">检票记录</div>
        <div onclick="location.href='${pageContext.request.contextPath}/seller/checkin?action=stats'">检票统计</div>
        <div onclick="location.href='home.jsp'">返回首页</div>
    </aside>
    <main class="desk-main">
        <h2>检票登船</h2>
        <div class="scan-box">
            <h3>🎫 扫描或输入订单号检票</h3>
            <p class="muted">输入乘客的订单号（如 FD2026112500001），确认后完成检票登船。</p>
            <% if (msg != null && !msg.isEmpty()) { %>
                <div class="msg <%= msg.startsWith("检票失败") ? "err" : "ok" %>"><%= msg %></div>
            <% } %>
            <form method="post" action="${pageContext.request.contextPath}/seller/checkin" id="checkinForm">
                <input type="text" name="orderNo" placeholder="请输入订单号" value="<%= orderNo == null ? "" : orderNo %>" autofocus>
                <button class="btn-primary">确认检票 (Enter)</button>
            </form>
            <p class="muted" style="font-size:12px;margin-top:12px">💡 校验规则：订单须已支付、未退票、未重复检票</p>
        </div>
    </main>
</div>
</body>
</html>
