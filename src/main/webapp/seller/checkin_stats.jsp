<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List,java.util.Map" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> stats = (List<Map<String,Object>>) request.getAttribute("stats");
    if (stats == null) stats = new java.util.ArrayList<Map<String,Object>>();
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>检票统计 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        .rate-bar { display:inline-block; width:120px; height:10px; background:#e5e7eb; border-radius:5px; overflow:hidden; vertical-align:middle; }
        .rate-bar i { display:block; height:100%; background:#22c55e; }
    </style>
</head>
<%
    boolean isAdmin = "admin".equals(session.getAttribute("role"));
    String ctx = request.getContextPath();
%>
<body class="desk">
<div class="desk-head"><%= isAdmin ? "码头管理系统 · 检票统计　管理员" : "码头售票系统 · 检票模式　售票员" %>：${sessionScope.userName}
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
        <div class="active">检票统计</div>
    </aside>
<% } else { %>
    <aside class="desk-menu">
        <div onclick="location.href='<%= ctx %>/seller/checkin'">▶ 检票</div>
        <div onclick="location.href='<%= ctx %>/seller/checkin?action=records'">检票记录</div>
        <div class="active">检票统计</div>
    </aside>
<% } %>
    <main class="desk-main">
        <h2>检票统计（各班次上座/检票率）</h2>
        <table class="data-table">
            <tr><th>班次ID</th><th>航线</th><th>发船时间</th><th>已售</th><th>已检票</th><th>检票率</th></tr>
            <% for (Map<String,Object> s : stats) {
                double rate = 0; try { rate = Double.parseDouble(String.valueOf(s.get("rate"))); } catch (Exception ignored) {}
            %>
            <tr>
                <td><%= s.get("schedule_id") %></td>
                <td><%= s.get("route_name") %></td>
                <td><%= s.get("depart_time") %></td>
                <td><%= s.get("sold") %></td>
                <td><%= s.get("checked") %></td>
                <td><span class="rate-bar"><i style="width:<%= rate %>%"></i></span> <%= s.get("rate") %>%</td>
            </tr>
            <% } %>
        </table>
    </main>
</div>
</body>
</html>
