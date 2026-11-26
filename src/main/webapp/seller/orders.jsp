<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Order,java.util.List,java.util.Map,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    @SuppressWarnings("unchecked")
    List<Order> orders = (List<Order>) request.getAttribute("orders");
    if (orders == null) orders = new java.util.ArrayList<Order>();
    @SuppressWarnings("unchecked")
    List<String> changeOptions = (List<String>) request.getAttribute("changeOptions");
    if (changeOptions == null) changeOptions = new java.util.ArrayList<String>();
    @SuppressWarnings("unchecked")
    Map<Integer, List<String>> changeInfos = (Map<Integer, List<String>>) request.getAttribute("changeInfos");
    if (changeInfos == null) changeInfos = new java.util.HashMap<Integer, List<String>>();
    @SuppressWarnings("unchecked")
    Map<Integer, List<String>> paymentLogs = (Map<Integer, List<String>>) request.getAttribute("paymentLogs");
    if (paymentLogs == null) paymentLogs = new java.util.HashMap<Integer, List<String>>();
    String msg = (String) request.getAttribute("msg");
    SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
    String ctx = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>销售订单 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        .order { border:1px solid #e5e7eb; border-radius:10px; padding:14px; margin:12px 0; background:#fff; max-width:900px; }
        .order .no { font-size:13px; color:#6b7280; }
        .order .st { float:right; font-size:13px; padding:2px 8px; border-radius:10px; background:#dbeafe; color:#1d4ed8; }
        .order .st.paid { background:#dcfce7; color:#15803d; }
        .order .st.cancel { background:#f3f4f6; color:#6b7280; }
        .btn-sm { padding:6px 12px; border:0; border-radius:6px; cursor:pointer; font-size:13px; }
        .btn-pay { background:#2563eb; color:#fff; }
        .btn-refund { background:#f97316; color:#fff; }
        select,input { padding:6px; border:1px solid #d1d5db; border-radius:6px; font-size:13px; }
    </style>
</head>
<body class="desk">
<div class="desk-head">码头售票系统 · 销售订单　售票员：${sessionScope.userName}
    <a class="logout" href="<%= ctx %>/logout">退出</a>
</div>
<div class="desk-layout">
    <aside class="desk-menu">
        <div onclick="location.href='<%= ctx %>/seller/home.jsp'">工作台</div>
        <div onclick="location.href='<%= ctx %>/seller/sell'">🎫 售票</div>
        <div class="active">📋 销售订单</div>
        <div onclick="location.href='<%= ctx %>/seller/checkin'">▶ 检票</div>
        <div onclick="location.href='<%= ctx %>/seller/checkin?action=records'">检票记录</div>
        <div onclick="location.href='<%= ctx %>/seller/checkin?action=stats'">检票统计</div>
    </aside>
    <main class="desk-main">
        <h2>📋 我经办的订单 <span class="muted" style="font-size:14px">共 <%= orders.size() %> 条</span></h2>
        <% if (msg != null && !msg.isEmpty()) { %><p class="msg ok"><%= msg %></p><% } %>
        <% if (orders.isEmpty()) { %><p class="muted">暂无订单，先去<a href="<%= ctx %>/seller/sell">售票台</a>代客购票吧。</p><% } %>

        <% for (Order o : orders) {
            String st = o.getStatus();
            String cls = "已支付".equals(st)||"已检票".equals(st)||"已完成".equals(st) ? "paid"
                       : (st.contains("取消")||st.contains("退票")) ? "cancel" : "";
        %>
        <div class="order">
            <div class="no">订单号 <%= o.getOrderNo() %> <span class="st <%= cls %>"><%= st %></span></div>
            <% List<String> infos = changeInfos.get(o.getId());
               if (infos != null && !infos.isEmpty()) { %>
            <div style="margin:6px 0;padding:6px 10px;background:#fef3c7;border-left:3px solid #f59e0b;border-radius:4px;font-size:12px;color:#92400e">
                <b>🔄 已改签 <%= infos.size() %> 次</b>
                <% for (String info : infos) { %><div style="margin-top:3px"><%= info %></div><% } %>
            </div>
            <% } %>
            <div style="margin:8px 0"><b><%= o.getRouteName() %></b>　<%= o.getShipName() %>
                <%= o.getDepartTime() == null ? "" : fmt.format(o.getDepartTime()) %>　座位 <%= o.getSeatNo() == null ? "-" : o.getSeatNo() %></div>
            <div>乘船人 <%= o.getPassengerName() == null ? "-" : o.getPassengerName() %>　金额 <b style="color:#dc2626">￥<%= o.getTotalAmount() %></b>
                <% if (o.getRefundAmount() != null) { %>　实退 <b style="color:#16a34a">￥<%= o.getRefundAmount() %></b><% } %></div>
            <% if (o.getRefundReason() != null) { %><div class="muted">退款原因：<%= o.getRefundReason() %></div><% } %>
            <% if (o.getRefundEta() != null) { %><div class="muted">预计到账：<b style="color:#0891b2"><%= new java.text.SimpleDateFormat("yyyy-MM-dd").format(o.getRefundEta()) %> 前</b>（3个工作日）</div><% } %>
            <% List<String> logs = paymentLogs.get(o.getId());
               if (logs != null && !logs.isEmpty()) { %>
            <div style="margin:6px 0;padding:6px 10px;background:#f0f9ff;border-left:3px solid #0ea5e9;border-radius:4px;font-size:12px;color:#0c4a6e">
                <b>💰 资金流水</b>
                <% for (String log : logs) { %><div style="margin-top:3px"><%= log %></div><% } %>
            </div>
            <% } %>

            <div style="margin-top:10px">
            <% if ("待支付".equals(st)) { %>
                <form method="post" action="<%= ctx %>/book" style="display:inline">
                    <input type="hidden" name="action" value="pay"><input type="hidden" name="orderId" value="<%= o.getId() %>">
                    <button class="btn-sm btn-pay">立即支付（5分钟内）</button>
                </form>
            <% } %>
            <% if ("已支付".equals(st) || "已检票".equals(st)) { %>
                <form method="post" action="<%= ctx %>/book" style="display:inline" onsubmit="return confirm('确定退票？将按规则扣手续费')">
                    <input type="hidden" name="action" value="refund"><input type="hidden" name="orderId" value="<%= o.getId() %>">
                    <input type="text" name="reason" placeholder="退票原因" size="8">
                    <button class="btn-sm btn-refund">退票</button>
                </form>
            <% } %>
            <% if ("已支付".equals(st)) { %>
                <form method="post" action="<%= ctx %>/book" style="display:inline;margin-top:8px" onsubmit="return confirm('确定改签？')">
                    <input type="hidden" name="action" value="change"><input type="hidden" name="orderId" value="<%= o.getId() %>">
                    <select name="changeTarget">
                        <% for (String opt : changeOptions) { String[] parts = opt.split("\\|", 3); %>
                        <option value="<%= parts[0] %>|<%= parts[1] %>"><%= parts[2] %></option>
                        <% } %>
                    </select>
                    <input type="text" name="reason" placeholder="改签原因(选填)" size="10">
                    <button class="btn-sm btn-pay">改签</button>
                </form>
            <% } %>
            </div>
        </div>
        <% } %>
    </main>
</div>
</body>
</html>
