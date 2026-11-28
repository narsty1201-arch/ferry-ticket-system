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
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>销售订单 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body class="desk">
<%@ include file="/WEB-INF/includes/seller-topbar.jspf" %>
<div class="desk-layout">
    <%@ include file="/WEB-INF/includes/seller-nav.jspf" %>
    <main class="desk-main">
        <div class="page-kicker"><span class="ico ico-orders ico-sm"></span>销售订单</div>
        <h2>我经办的订单 <span class="muted" style="font-size:14px">共 <%= orders.size() %> 条</span></h2>
        <% if (msg != null && !msg.isEmpty()) { %><p class="msg ok"><%= msg %></p><% } %>
        <% if (orders.isEmpty()) { %><div class="card muted">暂无订单，先去<a href="<%= ctx %>/seller/sell">售票台</a>代客购票吧。</div><% } %>

        <% for (Order o : orders) {
            String st = o.getStatus();
            String cls = "已支付".equals(st)||"已检票".equals(st)||"已完成".equals(st) ? "paid"
                       : (st.contains("取消")||st.contains("退票")) ? "cancel" : "";
            int lockRemain = Math.max(0, Math.min(300, o.getLockRemainSeconds()));
            String lockText = String.format("%02d:%02d", lockRemain / 60, lockRemain % 60);
        %>
        <div class="order">
            <div class="no">订单号 <%= o.getOrderNo() %> <span class="st <%= cls %>"><%= st %></span></div>
            <% List<String> infos = changeInfos.get(o.getId());
               if (infos != null && !infos.isEmpty()) { %>
            <div class="note warn">
                <b><span class="ico ico-refresh ico-sm"></span>已改签 <%= infos.size() %> 次</b>
                <% for (String info : infos) { %><div style="margin-top:3px"><%= info %></div><% } %>
            </div>
            <% } %>
            <div class="order-title"><b><%= o.getRouteName() %></b>　<%= o.getShipName() %>
                <%= o.getDepartTime() == null ? "" : fmt.format(o.getDepartTime()) %>　座位 <%= o.getSeatNo() == null ? "-" : o.getSeatNo() %></div>
            <div class="order-meta">乘船人 <%= o.getPassengerName() == null ? "-" : o.getPassengerName() %>　金额 <b class="price-strong">￥<%= o.getTotalAmount() %></b>
                <% if (o.getRefundAmount() != null) { %>　实退 <b class="price-ok">￥<%= o.getRefundAmount() %></b><% } %></div>
            <% if (o.getRefundReason() != null) { %><div class="muted">退款原因：<%= o.getRefundReason() %></div><% } %>
            <% if (o.getRefundEta() != null) { %><div class="muted">预计到账：<b style="color:#0891b2"><%= new java.text.SimpleDateFormat("yyyy-MM-dd").format(o.getRefundEta()) %> 前</b>（3个工作日）</div><% } %>
            <% List<String> logs = paymentLogs.get(o.getId());
               if (logs != null && !logs.isEmpty()) { %>
            <div class="note">
                <b><span class="ico ico-money ico-sm"></span>资金流水</b>
                <% for (String log : logs) { %><div style="margin-top:3px"><%= log %></div><% } %>
            </div>
            <% } %>

            <% if ("待支付".equals(st)) { %>
            <div class="pay-panel<%= lockRemain <= 0 ? " expired" : "" %>" data-remain="<%= lockRemain %>">
                <div class="pay-timer">
                    <span>支付剩余</span>
                    <b class="pay-countdown"><%= lockText %></b>
                </div>
                <div class="pay-actions">
                    <form method="post" action="<%= ctx %>/book"
                          onsubmit="return confirm('取消后座位立即释放，确定取消订单吗？')">
                        <input type="hidden" name="action" value="cancel">
                        <input type="hidden" name="orderId" value="<%= o.getId() %>">
                        <button class="op-cancel">取消订单</button>
                    </form>
                    <form method="post" action="<%= ctx %>/book">
                        <input type="hidden" name="action" value="pay">
                        <input type="hidden" name="orderId" value="<%= o.getId() %>">
                        <button class="op-pay">立即支付</button>
                    </form>
                </div>
            </div>
            <% } %>
            <% if ("已支付".equals(st) || "已检票".equals(st)) { %>
            <div class="ops">
                <form method="post" action="<%= ctx %>/book" style="display:inline" onsubmit="return confirm('确定退票？将按规则扣手续费')">
                    <input type="hidden" name="action" value="refund"><input type="hidden" name="orderId" value="<%= o.getId() %>">
                    <input type="text" name="reason" placeholder="退票原因" size="8">
                    <button class="op-refund">退票</button>
                </form>
            </div>
            <% } %>

            <%
                boolean canChange = "已支付".equals(st) && o.getDepartTime() != null
                        && o.getDepartTime().after(new java.util.Date());
                List<String> validChanges = new java.util.ArrayList<String>();
                if (canChange) {
                    for (String opt : changeOptions) {
                        String[] parts = opt.split("\\|", 4);
                        if (parts.length == 4
                                && (Long.parseLong(parts[0]) == o.getScheduleId()
                                    || Long.parseLong(parts[2]) > o.getDepartTime().getTime())) {
                            validChanges.add(opt);
                        }
                    }
                }
            %>
            <% if (!validChanges.isEmpty()) { %>
                <form method="post" action="<%= ctx %>/book" class="change-form" onsubmit="return confirm('确定改签？')">
                    <input type="hidden" name="action" value="change"><input type="hidden" name="orderId" value="<%= o.getId() %>">
                    <select name="changeTarget">
                        <% for (String opt : validChanges) { String[] parts = opt.split("\\|", 4); %>
                        <option value="<%= parts[0] %>|<%= parts[1] %>"><%= parts[3] %></option>
                        <% } %>
                    </select>
                    <input type="text" name="reason" placeholder="改签原因(选填)" size="10">
                    <button class="op-pay">改签</button>
                </form>
            <% } %>
        </div>
        <% } %>
    </main>
</div>
<script src="<%= ctx %>/js/order-timer.js"></script>
</body>
</html>
