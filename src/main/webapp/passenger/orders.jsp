<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Order,com.ferry.entity.Schedule,java.util.List,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    @SuppressWarnings("unchecked")
    List<Order> orders = (List<Order>) request.getAttribute("orders");
    @SuppressWarnings("unchecked")
    List<Schedule> schedules = (List<Schedule>) request.getAttribute("schedules");
    if (orders == null) orders = new java.util.ArrayList<Order>();
    if (schedules == null) schedules = new java.util.ArrayList<Schedule>();
        String msg = (String) request.getAttribute("msg");
        java.util.Map<Integer, java.util.List<String>> changeInfos =
            (java.util.Map<Integer, java.util.List<String>>) request.getAttribute("changeInfos");
        if (changeInfos == null) changeInfos = new java.util.HashMap<Integer, java.util.List<String>>();
        java.util.Map<Integer, java.util.List<String>> paymentLogs =
            (java.util.Map<Integer, java.util.List<String>>) request.getAttribute("paymentLogs");
        if (paymentLogs == null) paymentLogs = new java.util.HashMap<Integer, java.util.List<String>>();
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>我的订单 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        .order { border:1px solid #e5e7eb; border-radius:12px; padding:14px; margin:12px 0; background:#fff; }
        .order .no { font-size:12px; color:#6b7280; }
        .order .st { float:right; font-size:13px; padding:2px 8px; border-radius:10px; background:#dbeafe; color:#1d4ed8; }
        .order .st.paid { background:#dcfce7; color:#15803d; }
        .order .st.cancel { background:#f3f4f6; color:#6b7280; }
        .ops button { padding:8px 12px; border:0; border-radius:8px; cursor:pointer; font-size:13px; margin-right:6px; }
        .op-pay { background:#2563eb; color:#fff; }
        .op-refund { background:#f97316; color:#fff; }
        select,input { padding:6px; border:1px solid #d1d5db; border-radius:6px; font-size:13px; }
    </style>
</head>
<body class="mobile">
<div class="phone">
    <div class="phone-head">📋 我的订单</div>
    <div class="phone-body">
        <% if (msg != null && !msg.isEmpty()) { %><p class="msg ok"><%= msg %></p><% } %>
        <% if (orders.isEmpty()) { %><p class="muted">暂无订单，快去<a href="schedules.jsp">查询班次</a>购票吧。</p><% } %>

        <% for (Order o : orders) {
            String st = o.getStatus();
            String cls = "已支付".equals(st)||"已检票".equals(st)||"已完成".equals(st) ? "paid"
                       : (st.contains("取消")||st.contains("退票")) ? "cancel" : "";
        %>
        <div class="order">
            <div class="no">订单号 <%= o.getOrderNo() %> <span class="st <%= cls %>"><%= st %></span></div>
            <% java.util.List<String> infos = changeInfos.get(o.getId());
               if (infos != null && !infos.isEmpty()) { %>
            <div style="margin:6px 0;padding:6px 10px;background:#fef3c7;border-left:3px solid #f59e0b;border-radius:4px;font-size:12px;color:#92400e">
                <b>🔄 已改签 <%= infos.size() %> 次</b>
                <% for (String info : infos) { %><div style="margin-top:3px"><%= info %></div><% } %>
            </div>
            <% } %>
            <div style="margin:8px 0"><b><%= o.getRouteName() %></b>　<%= o.getShipName() %></div>
            <div class="muted">发船 <%= fmt.format(o.getDepartTime()) %>　座位 <%= o.getSeatNo()==null?"-":o.getSeatNo() %></div>
            <div style="margin:6px 0">金额 <b style="color:#dc2626">￥<%= o.getTotalAmount() %></b>
                <% if (o.getRefundAmount() != null) { %>　实退 <b style="color:#16a34a">￥<%= o.getRefundAmount() %></b><% } %>
            </div>
            <% if (o.getRefundReason() != null) { %><div class="muted">退款原因：<%= o.getRefundReason() %></div><% } %>
            <% if (o.getRefundEta() != null) { %><div class="muted">预计到账：<b style="color:#0891b2"><%= new java.text.SimpleDateFormat("yyyy-MM-dd").format(o.getRefundEta()) %> 前</b>（3个工作日，以银行实际到账为准）</div><% } %>
            <% java.util.List<String> logs = paymentLogs.get(o.getId());
               if (logs != null && !logs.isEmpty()) { %>
            <div style="margin:6px 0;padding:6px 10px;background:#f0f9ff;border-left:3px solid #0ea5e9;border-radius:4px;font-size:12px;color:#0c4a6e">
                <b>💰 资金流水</b>
                <% for (String log : logs) { %><div style="margin-top:3px"><%= log %></div><% } %>
            </div>
            <% } %>

            <div class="ops">
            <% if ("待支付".equals(st)) { %>
                <form method="post" action="${pageContext.request.contextPath}/book" style="display:inline">
                    <input type="hidden" name="action" value="pay"><input type="hidden" name="orderId" value="<%= o.getId() %>">
                    <button class="op-pay">立即支付（5分钟内）</button>
                </form>
            <% } %>
            <% if ("已支付".equals(st) || "已检票".equals(st)) { %>
                <form method="post" action="${pageContext.request.contextPath}/book" style="display:inline" onsubmit="return confirm('确定退票？将按规则扣手续费')">
                    <input type="hidden" name="action" value="refund"><input type="hidden" name="orderId" value="<%= o.getId() %>">
                    <input type="text" name="reason" placeholder="退票原因" size="8">
                    <button class="op-refund">退票</button>
                </form>
            <% } %>
            </div>

            <% if ("已支付".equals(st)) { %>
            <form method="post" action="${pageContext.request.contextPath}/book" style="margin-top:8px"
                  onsubmit="return confirm('确定改签？')">
                <input type="hidden" name="action" value="change"><input type="hidden" name="orderId" value="<%= o.getId() %>">
                <select name="changeTarget">
                    <% for (String opt : (java.util.List<String>) request.getAttribute("changeOptions")) {
               String[] parts = opt.split("\\|", 3); %>
                    <option value="<%= parts[0] %>|<%= parts[1] %>"><%= parts[2] %></option>
                    <% } %>
                </select>
                <input type="text" name="reason" placeholder="改签原因(选填)" size="10">
                <button class="op-pay">改签</button>
                <div class="muted" style="font-size:11px;margin-top:3px">↑选择新班次和座位，差价自动计算</div>
            </form>
            <% } %>
        </div>
        <% } %>
    </div>
    <div class="tabbar">
        <a href="${pageContext.request.contextPath}/passenger/home.jsp"><span>🏠 首页</span></a>
        <a href="${pageContext.request.contextPath}/passenger/schedules.jsp"><span>🎫 班次</span></a>
        <a href="${pageContext.request.contextPath}/passenger/orders"><span class="active">📋 订单</span></a>
        <a href="${pageContext.request.contextPath}/passenger/profile"><span>👤 我的</span></a>
    </div>
</div>
</body>
</html>
