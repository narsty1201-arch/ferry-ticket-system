<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.util.Page,java.util.List,java.util.Map" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    Page<Map<String, Object>> pageData = (Page<Map<String, Object>>) request.getAttribute("pageData");
    if (pageData == null) pageData = Page.empty(1, 15);
    List<Map<String,Object>> records = pageData.getItems();
    String pageUrl = (String) request.getAttribute("pageUrl");
    if (pageUrl == null) pageUrl = "checkin?action=records&page=";
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>检票记录 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<%
    boolean isAdmin = "admin".equals(session.getAttribute("role"));
    String ctx = request.getContextPath();
%>
<body class="desk">
<div class="desk-head"><%= isAdmin ? "码头管理系统 · 检票记录　管理员" : "码头售票系统 · 检票模式　售票员" %>：${sessionScope.userName}
    <a class="logout" href="<%= ctx %>/logout">退出</a>
</div>
<div class="desk-layout">
<% if (isAdmin) { %>
    <aside class="admin-menu" style="width:200px">
        <div><a href="<%= ctx %>/admin/home">数据概览</a></div>
        <div><a href="<%= ctx %>/admin/route">航线管理</a></div>
        <div><a href="<%= ctx %>/admin/ship">船舶管理</a></div>
        <div><a href="<%= ctx %>/admin/schedule">班次管理</a></div>
        <div><a href="<%= ctx %>/admin/users">👥 用户管理</a></div>
        <div><a href="<%= ctx %>/admin/orders">📋 订单管理</a></div>
        <div><a href="<%= ctx %>/admin/stats">📊 统计报表</a></div>
        <div class="active">检票记录</div>
    </aside>
<% } else { %>
    <aside class="desk-menu">
        <div onclick="location.href='<%= ctx %>/seller/checkin'">▶ 检票</div>
        <div class="active">检票记录</div>
        <div onclick="location.href='<%= ctx %>/seller/checkin?action=stats'">检票统计</div>
    </aside>
<% } %>
    <main class="desk-main">
        <h2>检票记录 <span class="muted" style="font-size:14px">共 <%= pageData.getTotal() %> 条</span></h2>
        <table class="data-table">
            <tr><th>序号</th><th>订单号</th><th>乘客</th><th>身份证</th><th>座位</th><th>航线</th><th>发船</th><th>检票时间</th><th>检票员</th><th>状态</th></tr>
            <% for (int i = 0; i < records.size(); i++) { Map<String,Object> r = records.get(i); %>
            <tr>
                <td><%= i+1 %></td>
                <td><%= r.get("order_no") %></td>
                <td><%= r.get("passenger_name") %></td>
                <td><%= r.get("id_card") %></td>
                <td><%= r.get("seat_no") %></td>
                <td><%= r.get("route_name") %></td>
                <td><%= r.get("depart_time") %></td>
                <td><%= r.get("check_time") %></td>
                <td><%= r.get("seller_name") %></td>
                <td><%= r.get("status") %></td>
            </tr>
            <% } %>
            <% if (records.isEmpty()) { %><tr><td colspan="10" style="text-align:center;color:#9ca3af">暂无检票记录</td></tr><% } %>
        </table>
        <%@ include file="/WEB-INF/pagination.jspf" %>
    </main>
</div>
</body>
</html>
