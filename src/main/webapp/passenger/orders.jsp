<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Order,java.util.List,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    @SuppressWarnings("unchecked")
    List<Order> orders = (List<Order>) request.getAttribute("orders");
    if (orders == null) orders = new java.util.ArrayList<Order>();
    @SuppressWarnings("unchecked")
    List<String> changeOptions = (List<String>) request.getAttribute("changeOptions");
    if (changeOptions == null) changeOptions = new java.util.ArrayList<String>();
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
</head>
<body class="mobile">
<div class="phone">
    <div class="phone-head">📋 我的订单</div>
    <div class="phone-body">
        <% if (msg != null && !msg.isEmpty()) { %><p class="msg ok"><%= msg %></p><% } %>
        <% if (orders.isEmpty()) { %><p class="muted">暂无订单，快去<a href="${pageContext.request.contextPath}/passenger/schedules">查询班次</a>购票吧。</p><% } %>

        <% for (Order o : orders) {
            String st = o.getStatus();
            String cls = "已支付".equals(st)||"已检票".equals(st)||"已完成".equals(st) ? "paid"
                       : (st.contains("取消")||st.contains("退票")) ? "cancel" : "";
        %>
        <div class="order">
            <div class="no">订单号 <%= o.getOrderNo() %> <span class="st <%= cls %>"><%= st %></span></div>
            <% java.util.List<String> infos = changeInfos.get(o.getId());
               if (infos != null && !infos.isEmpty()) { %>
            <div class="note warn">
                <b>🔄 已改签 <%= infos.size() %> 次</b>
                <% for (String info : infos) { %><div style="margin-top:3px"><%= info %></div><% } %>
            </div>
            <% } %>
            <div class="order-title"><%= o.getRouteName() %>　<%= o.getShipName() %></div>
            <div class="muted">发船 <%= fmt.format(o.getDepartTime()) %>　座位 <%= o.getSeatNo()==null?"-":o.getSeatNo() %></div>
            <div class="amount">金额 ￥<%= o.getTotalAmount() %>
                <% if (o.getRefundAmount() != null) { %>　<span class="refund">实退 ￥<%= o.getRefundAmount() %></span><% } %>
            </div>
            <% if (o.getRefundReason() != null) { %><div class="muted">退款原因：<%= o.getRefundReason() %></div><% } %>
            <% if (o.getRefundEta() != null) { %><div class="muted eta">预计到账：<b><%= new java.text.SimpleDateFormat("yyyy-MM-dd").format(o.getRefundEta()) %> 前</b>（3个工作日，以银行实际到账为准）</div><% } %>
            <% java.util.List<String> logs = paymentLogs.get(o.getId());
               if (logs != null && !logs.isEmpty()) { %>
            <div class="note">
                <b>💰 资金流水</b>
                <% for (String log : logs) { %><div style="margin-top:3px"><%= log %></div><% } %>
            </div>
            <% } %>

            <div class="ops">
            <% if ("待支付".equals(st)) { %>
                <form method="post" action="${pageContext.request.contextPath}/book">
                    <input type="hidden" name="action" value="pay"><input type="hidden" name="orderId" value="<%= o.getId() %>">
                    <button class="op-pay">立即支付（5分钟内）</button>
                </form>
            <% } %>
            <% if ("已支付".equals(st) || "已检票".equals(st)) { %>
                <form method="post" action="${pageContext.request.contextPath}/book" onsubmit="return confirm('确定退票？将按规则扣手续费')">
                    <input type="hidden" name="action" value="refund"><input type="hidden" name="orderId" value="<%= o.getId() %>">
                    <input type="text" name="reason" placeholder="退票原因" size="8">
                    <button class="op-refund">退票</button>
                </form>
            <% } %>
            </div>

            <%
                boolean canChange = "已支付".equals(st) && o.getDepartTime() != null
                        && o.getDepartTime().after(new java.util.Date());
                java.util.List<String> validChanges = new java.util.ArrayList<String>();
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
            <form method="post" action="${pageContext.request.contextPath}/book" class="change-form"
                  onsubmit="return confirm('确定改签？')">
                <input type="hidden" name="action" value="change"><input type="hidden" name="orderId" value="<%= o.getId() %>">
                <select name="changeTarget">
                    <% for (String opt : validChanges) {
                        String[] parts = opt.split("\\|", 4); %>
                    <option value="<%= parts[0] %>|<%= parts[1] %>"><%= parts[3] %></option>
                    <% } %>
                </select>
                <input type="text" name="reason" placeholder="改签原因(选填)" size="10">
                <button class="op-pay">改签</button>
                <div class="hint-small">可换同班次其他座位，或改到发船时间更晚的未截止班次</div>
            </form>
            <% } %>
        </div>
        <% } %>
    </div>
    <div class="tabbar">
        <a href="${pageContext.request.contextPath}/passenger/home"><span>🏠 首页</span></a>
        <a href="${pageContext.request.contextPath}/passenger/schedules"><span>🎫 班次</span></a>
        <a href="${pageContext.request.contextPath}/passenger/orders"><span class="active">📋 订单</span></a>
        <a href="${pageContext.request.contextPath}/passenger/profile"><span>👤 我的</span></a>
    </div>
</div>
</body>
</html>
