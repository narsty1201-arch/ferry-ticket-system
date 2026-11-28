<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Schedule,java.util.List,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    @SuppressWarnings("unchecked")
    List<Schedule> list = (List<Schedule>) request.getAttribute("list");
    if (list == null) list = new java.util.ArrayList<Schedule>();
    SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
    String msg = (String) request.getAttribute("msg");
    String ctx = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>售票台 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body class="desk">
<%@ include file="/WEB-INF/includes/seller-topbar.jspf" %>
<div class="desk-layout">
    <%@ include file="/WEB-INF/includes/seller-nav.jspf" %>
    <main class="desk-main">
        <div class="page-kicker"><span class="ico ico-sale ico-sm"></span>代客售票</div>
        <h2>选择班次</h2>
        <% if (msg != null && !msg.isEmpty()) { %><p class="msg err"><%= msg %></p><% } %>
        <table class="data-table">
            <thead>
            <tr><th>班次ID</th><th>航线</th><th>港口</th><th>船舶</th><th>发船</th>
                <th>票价</th><th>余票</th><th>状态</th><th>操作</th></tr>
            </thead>
            <tbody>
            <% for (Schedule s : list) { %>
                <tr>
                    <td><%= s.getId() %></td>
                    <td><%= s.getRouteName() %></td>
                    <td><%= s.getStartPort() %> → <%= s.getEndPort() %></td>
                    <td><%= s.getShipName() %></td>
                    <td><%= s.getDepartTime() == null ? "" : fmt.format(s.getDepartTime()) %></td>
                    <td>￥<%= s.getPrice() %></td>
                    <td><%= s.getRemainSeats() %>/<%= s.getTotalSeats() %></td>
                    <td><%= s.getStatus() %></td>
                    <td>
                        <% if ("售票中".equals(s.getStatus()) && s.getRemainSeats() > 0) { %>
                        <a class="btn-sm btn-add" href="<%= ctx %>/book?action=seats&scheduleId=<%= s.getId() %>"><span class="ico ico-seat ico-sm"></span>选座购票</a>
                        <% } else { %><span class="muted">不可售</span><% } %>
                    </td>
                </tr>
            <% } %>
            </tbody>
        </table>
        <p class="muted">代客下单后订单记入本人的销售订单；座位锁定 5 分钟，请在时限内完成支付。</p>
    </main>
</div>
</body>
</html>
