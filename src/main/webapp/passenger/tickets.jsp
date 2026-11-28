<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Ticket,java.util.List,java.text.SimpleDateFormat" %>
<%!
    private static String h(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    @SuppressWarnings("unchecked")
    List<Ticket> tickets = (List<Ticket>) request.getAttribute("tickets");
    if (tickets == null) tickets = new java.util.ArrayList<Ticket>();
    String msg = (String) request.getAttribute("msg");
    SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
    String ctx = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>我的船票 - 轮渡码头</title>
    <link rel="stylesheet" href="<%= ctx %>/css/style.css">
</head>
<body class="mobile">
<div class="phone">
    <div class="phone-head"><span class="ico ico-ticket"></span>我的船票</div>
    <div class="phone-body">
        <% if (msg != null && !msg.isEmpty()) { %><p class="msg ok"><%= h(msg) %></p><% } %>
        <% if (tickets.isEmpty()) { %>
        <div class="card muted"><span class="ico ico-info"></span> 暂无可使用的船票，先去
            <a href="<%= ctx %>/passenger/schedules">查询班次</a>购票吧。</div>
        <% } %>

        <% for (Ticket t : tickets) {
            String qr = t.getQrCode();
            if (qr == null || qr.trim().isEmpty()) qr = "FERRY-" + t.getOrderNo() + "-" + t.getId();
            int seed = qr.hashCode();
            String status = t.getStatus() == null ? "已出票" : t.getStatus();
        %>
        <article class="e-ticket">
            <div class="e-ticket-head">
                <span><%= h(t.getRouteName()) %> · <%= h(t.getShipName()) %></span>
                <b><%= h(status) %></b>
            </div>
            <div class="e-ticket-route">
                <span><%= h(t.getStartPort()) %></span><i>→</i><span><%= h(t.getEndPort()) %></span>
            </div>
            <div class="e-ticket-meta">
                <div><span>发船时间</span><b><%= t.getDepartTime() == null ? "-" : fmt.format(t.getDepartTime()) %></b></div>
                <div><span>座位</span><b><%= h(t.getSeatNo()) %></b></div>
                <div><span>乘客</span><b><%= h(t.getPassengerName()) %></b></div>
                <div><span>票价</span><b>￥<%= t.getPrice() %></b></div>
            </div>
            <div class="e-ticket-qr">
                <div class="qr-pattern" aria-label="票码 <%= h(qr) %>">
                    <% for (int y = 0; y < 21; y++) {
                           for (int x = 0; x < 21; x++) {
                               boolean dark;
                               if (x < 7 && y < 7) {
                                   dark = x == 0 || x == 6 || y == 0 || y == 6 || (x >= 2 && x <= 4 && y >= 2 && y <= 4);
                               } else if (x >= 14 && y < 7) {
                                   int lx = x - 14;
                                   dark = lx == 0 || lx == 6 || y == 0 || y == 6 || (lx >= 2 && lx <= 4 && y >= 2 && y <= 4);
                               } else if (x < 7 && y >= 14) {
                                   int ly = y - 14;
                                   dark = x == 0 || x == 6 || ly == 0 || ly == 6 || (x >= 2 && x <= 4 && ly >= 2 && ly <= 4);
                               } else {
                                   dark = ((seed >>> ((x * 3 + y * 5) % 31)) & 1) == 1;
                               }
                    %><i class="<%= dark ? "on" : "" %>"></i><% } } %>
                </div>
                <div class="qr-text">
                    <span>检票票码</span>
                    <b><%= h(qr) %></b>
                    <small>检票台可输入订单号，系统会校验乘客、班次和检票时间。</small>
                </div>
            </div>
            <div class="e-ticket-foot">
                订单号 <b><%= h(t.getOrderNo()) %></b>
                <a href="<%= ctx %>/passenger/orders">查看订单</a>
            </div>
        </article>
        <% } %>
    </div>
    <%@ include file="/WEB-INF/includes/passenger-tabbar.jspf" %>
</div>
</body>
</html>
