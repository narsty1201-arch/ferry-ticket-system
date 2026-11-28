<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List,java.util.Map" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    @SuppressWarnings("unchecked")
    Map<String,Object> ov = (Map<String,Object>) request.getAttribute("ov");
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> byRoute = (List<Map<String,Object>>) request.getAttribute("byRoute");
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> bySchedule = (List<Map<String,Object>>) request.getAttribute("bySchedule");
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> sellers = (List<Map<String,Object>>) request.getAttribute("sellers");
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> statusDist = (List<Map<String,Object>>) request.getAttribute("statusDist");
    if (ov == null) ov = new java.util.HashMap<String,Object>();
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>统计报表 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body class="admin">
<%@ include file="/WEB-INF/includes/admin-topbar.jspf" %>
<div class="admin-layout">
    <%@ include file="/WEB-INF/includes/admin-nav.jspf" %>
    <main class="admin-main">
        <div class="page-kicker"><span class="ico ico-chart ico-sm"></span>经营分析</div>
        <h2>统计报表</h2>
        <p class="muted">数据由 SQL 聚合查询实时生成（COUNT / SUM / AVG / GROUP BY / CASE WHEN / JOIN）。</p>

        <div class="kpi-row" style="margin-top:18px">
            <div class="card kpi"><div class="muted">总订单数</div><div class="kpi-num"><%= ov.get("total_orders") %></div></div>
            <div class="card kpi"><div class="muted">总营收</div><div class="kpi-num">￥<%= ov.get("total_income") %></div></div>
            <div class="card kpi"><div class="muted">已售座位</div><div class="kpi-num"><%= ov.get("sold_seats") %></div></div>
            <div class="card kpi"><div class="muted">已检票</div><div class="kpi-num"><%= ov.get("checked_orders") %></div></div>
            <div class="card kpi"><div class="muted">退票订单</div><div class="kpi-num"><%= ov.get("refunded_orders") %></div></div>
        </div>

        <div class="sec">
            <h3>按航线统计（GROUP BY + JOIN + SUM）</h3>
            <table class="data-table">
                <tr><th>航线</th><th>港口</th><th>订单数</th><th>营收</th><th>已售座位</th></tr>
                <% for (Map<String,Object> r : byRoute) { %>
                <tr><td><%= r.get("route_name") %></td><td><%= r.get("start_port") %> → <%= r.get("end_port") %></td>
                    <td><%= r.get("order_count") %></td><td>￥<%= r.get("income") %></td><td><%= r.get("sold") %></td></tr>
                <% } %>
            </table>
        </div>

        <div class="sec">
            <h3>各班次上座率（CASE WHEN + 计算列）</h3>
            <table class="data-table">
                <tr><th>班次</th><th>航线</th><th>发船</th><th>总座位</th><th>已售</th><th>上座率</th></tr>
                <% for (Map<String,Object> r : bySchedule) {
                    double rate = 0; try { rate = Double.parseDouble(String.valueOf(r.get("rate"))); } catch (Exception ignored) {}
                %>
                <tr><td><%= r.get("schedule_id") %></td><td><%= r.get("route_name") %></td><td><%= r.get("depart_time") %></td>
                    <td><%= r.get("total_seats") %></td><td><%= r.get("sold_seats") %></td>
                    <td><span class="rate-bar"><i style="width:<%= rate %>%"></i></span> <%= r.get("rate") %>%</td></tr>
                <% } %>
            </table>
        </div>

        <div class="sec">
            <h3>售票员业绩排名（视图 + AVG）</h3>
            <table class="data-table">
                <tr><th>姓名</th><th>账号</th><th>售出订单</th><th>销售额</th><th>平均单额</th></tr>
                <% for (Map<String,Object> r : sellers) { %>
                <tr><td><%= r.get("real_name") %></td><td><%= r.get("username") %></td>
                    <td><%= r.get("order_count") %></td><td>￥<%= r.get("total_amount") %></td><td>￥<%= r.get("avg_amount") %></td></tr>
                <% } %>
            </table>
        </div>

        <div class="sec">
            <h3>订单状态分布（CASE WHEN 分类统计）</h3>
            <table class="data-table">
                <tr><th>待支付</th><th>已支付</th><th>已检票</th><th>已退票</th><th>已取消</th></tr>
                <% for (Map<String,Object> r : statusDist) { %>
                <tr><td><%= r.get("pending") %></td><td><%= r.get("paid") %></td><td><%= r.get("checked") %></td>
                    <td><%= r.get("refunded") %></td><td><%= r.get("cancelled") %></td></tr>
                <% } %>
            </table>
        </div>
    </main>
</div>
</body>
</html>
