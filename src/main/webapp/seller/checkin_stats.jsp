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
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>检票统计 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<%
    boolean isAdmin = "admin".equals(session.getAttribute("role"));
    String ctx = request.getContextPath();
%>
<body class="desk">
<%@ include file="/WEB-INF/includes/staff-topbar.jspf" %>
<div class="desk-layout">
    <%@ include file="/WEB-INF/includes/staff-nav.jspf" %>
    <main class="desk-main">
        <div class="page-kicker"><span class="ico ico-chart ico-sm"></span>检票管理</div>
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
