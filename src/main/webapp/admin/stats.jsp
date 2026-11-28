<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Route,java.util.List,java.util.Map" %>
<%!
    private String h(Object value) {
        return com.ferry.util.HtmlUtil.escape(value);
    }
%>
<%
    if (session.getAttribute("loginUser") == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
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
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> refundReasons = (List<Map<String,Object>>) request.getAttribute("refundReasons");
    @SuppressWarnings("unchecked")
    List<Route> routes = (List<Route>) request.getAttribute("routes");
    if (ov == null) ov = new java.util.HashMap<String,Object>();
    if (byRoute == null) byRoute = new java.util.ArrayList<Map<String,Object>>();
    if (bySchedule == null) bySchedule = new java.util.ArrayList<Map<String,Object>>();
    if (sellers == null) sellers = new java.util.ArrayList<Map<String,Object>>();
    if (statusDist == null) statusDist = new java.util.ArrayList<Map<String,Object>>();
    if (refundReasons == null) refundReasons = new java.util.ArrayList<Map<String,Object>>();
    if (routes == null) routes = new java.util.ArrayList<Route>();
    String startDate = (String) request.getAttribute("startDate");
    String endDate = (String) request.getAttribute("endDate");
    String filterError = (String) request.getAttribute("filterError");
    Integer selectedRouteId = (Integer) request.getAttribute("routeId");
    String exportUrl = (String) request.getAttribute("exportUrl");
    if (exportUrl == null) exportUrl = "stats?format=xlsx&startDate=&endDate=&routeId=";
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
        <p class="muted">数据由 SQL 聚合查询实时生成。日期采用半开区间，营收按订单创建时间、上座率按班次发船时间、退票原因按退款时间、检票业绩按检票时间统计。</p>

        <form class="form-row" method="get" action="${pageContext.request.contextPath}/admin/stats">
            <div><label>开始日期</label>
                <input type="date" name="startDate" value="<%= h(startDate) %>"></div>
            <div><label>结束日期（含当天）</label>
                <input type="date" name="endDate" value="<%= h(endDate) %>"></div>
            <div><label>航线</label>
                <select name="routeId">
                    <option value="">全部航线</option>
                    <% for (Route route : routes) {
                        boolean selected = selectedRouteId != null && selectedRouteId.intValue() == route.getId();
                    %>
                    <option value="<%= route.getId() %>" <%= selected ? "selected" : "" %>>
                        <%= h(route.getRouteName()) %>（<%= h(route.getStartPort()) %> → <%= h(route.getEndPort()) %>）
                    </option>
                    <% } %>
                </select>
            </div>
            <div><button type="submit" class="btn-sm btn-add">应用筛选</button>
                <a class="btn-sm btn-secondary" href="${pageContext.request.contextPath}/admin/stats">重置</a>
                <a class="btn-sm btn-secondary" href="<%= h(exportUrl) %>">导出 Excel</a></div>
        </form>
        <% if (filterError != null && !filterError.isEmpty()) { %>
        <p class="msg err"><%= h(filterError) %></p>
        <% } %>

        <div class="kpi-row" style="margin-top:18px">
            <div class="card kpi"><div class="muted">订单总数</div><div class="kpi-num"><%= h(ov.get("total_orders")) %></div></div>
            <div class="card kpi"><div class="muted">净营收</div><div class="kpi-num">￥<%= h(ov.get("total_income")) %></div></div>
            <div class="card kpi"><div class="muted">有效船票 / 已售座位</div><div class="kpi-num"><%= h(ov.get("sold_seats")) %></div></div>
            <div class="card kpi"><div class="muted">已检票订单（含已完成）</div><div class="kpi-num"><%= h(ov.get("checked_orders")) %></div></div>
            <div class="card kpi"><div class="muted">退票订单</div><div class="kpi-num"><%= h(ov.get("refunded_orders")) %></div></div>
            <div class="card kpi"><div class="muted">退款金额</div><div class="kpi-num">￥<%= h(ov.get("refund_amount")) %></div></div>
        </div>

        <div class="sec">
            <h3>按航线统计（GROUP BY + JOIN + SUM）</h3>
            <table class="data-table">
                <thead><tr><th>航线</th><th>港口</th><th>成交订单</th><th>已检票订单</th>
                    <th>净营收</th><th>退款金额</th><th>有效船票</th><th>已检票（票）</th></tr></thead>
                <tbody>
                <% if (byRoute.isEmpty()) { %>
                <tr><td colspan="8" class="muted">所选条件下暂无航线数据</td></tr>
                <% } %>
                <% for (Map<String,Object> r : byRoute) { %>
                <tr><td><%= h(r.get("route_name")) %></td>
                    <td><%= h(r.get("start_port")) %> → <%= h(r.get("end_port")) %></td>
                    <td><%= h(r.get("order_count")) %></td><td><%= h(r.get("checked_orders")) %></td>
                    <td>￥<%= h(r.get("income")) %></td><td>￥<%= h(r.get("refund_amount")) %></td>
                    <td><%= h(r.get("sold")) %></td><td><%= h(r.get("checked")) %></td></tr>
                <% } %>
                </tbody>
            </table>
        </div>

        <div class="sec">
            <h3>各班次上座率与预警</h3>
            <p class="muted">未来 24 小时内上座率低于 30% 标为“紧急”，48 小时内低于 60% 标为“关注”；已发船班次不触发预警。</p>
            <table class="data-table">
                <thead><tr><th>班次</th><th>航线</th><th>发船</th><th>状态</th><th>总座位</th>
                    <th>已售</th><th>上座率</th><th>已检票</th><th>预警</th></tr></thead>
                <tbody>
                <% if (bySchedule.isEmpty()) { %>
                <tr><td colspan="9" class="muted">所选条件下暂无班次数据</td></tr>
                <% } %>
                <% for (Map<String,Object> r : bySchedule) {
                    double rate = 0;
                    try { rate = Double.parseDouble(String.valueOf(r.get("rate"))); } catch (Exception ignored) {}
                    String level = String.valueOf(r.get("alert_level"));
                    String levelClass = "紧急".equals(level) ? "tag-bad"
                                      : "关注".equals(level) ? "tag-warn"
                                      : "正常".equals(level) ? "tag-ok" : "tag";
                %>
                <tr><td><%= h(r.get("schedule_id")) %></td><td><%= h(r.get("route_name")) %></td>
                    <td><%= h(r.get("depart_time")) %></td><td><%= h(r.get("status")) %></td>
                    <td><%= h(r.get("total_seats")) %></td><td><%= h(r.get("sold_seats")) %></td>
                    <td><span class="rate-bar"><i style="width:<%= rate %>%"></i></span> <%= h(r.get("rate")) %>%</td>
                    <td><%= h(r.get("checked")) %></td>
                    <td><span class="tag <%= levelClass %>"><%= h(level) %></span></td></tr>
                <% } %>
                </tbody>
            </table>
        </div>

        <div class="sec">
            <h3>售票员业绩排名</h3>
            <p class="muted">成交订单与净销售额按订单创建时间统计；检票数为所选区间内该账号实际成功检票的票数。</p>
            <table class="data-table">
                <thead><tr><th>姓名</th><th>账号</th><th>成交订单</th><th>有效船票</th>
                    <th>净销售额</th><th>退款单数</th><th>退款金额</th><th>检票数</th><th>平均单额</th></tr></thead>
                <tbody>
                <% if (sellers.isEmpty()) { %>
                <tr><td colspan="9" class="muted">暂无售票员数据</td></tr>
                <% } %>
                <% for (Map<String,Object> r : sellers) { %>
                <tr><td><%= h(r.get("real_name")) %></td><td><%= h(r.get("username")) %></td>
                    <td><%= h(r.get("order_count")) %></td><td><%= h(r.get("ticket_count")) %></td>
                    <td>￥<%= h(r.get("total_amount")) %></td><td><%= h(r.get("refund_count")) %></td>
                    <td>￥<%= h(r.get("refund_amount")) %></td><td><%= h(r.get("checked_count")) %></td>
                    <td>￥<%= h(r.get("avg_amount")) %></td></tr>
                <% } %>
                </tbody>
            </table>
        </div>

        <div class="sec">
            <h3>退票原因分布（按退款时间筛选）</h3>
            <table class="data-table">
                <thead><tr><th>退票原因</th><th>退票单数</th><th>原订单金额</th><th>实际退款</th></tr></thead>
                <tbody>
                <% if (refundReasons.isEmpty()) { %>
                <tr><td colspan="4" class="muted">所选条件下暂无退票记录</td></tr>
                <% } %>
                <% for (Map<String,Object> r : refundReasons) { %>
                <tr><td><%= h(r.get("reason")) %></td><td><%= h(r.get("refund_count")) %></td>
                    <td>￥<%= h(r.get("refunded_total")) %></td><td>￥<%= h(r.get("refund_amount")) %></td></tr>
                <% } %>
                </tbody>
            </table>
        </div>

        <div class="sec">
            <h3>订单状态分布（CASE WHEN 分类统计）</h3>
            <table class="data-table">
                <thead><tr><th>合计</th><th>待支付</th><th>已支付</th><th>已检票</th>
                    <th>已完成</th><th>已退票</th><th>已取消</th></tr></thead>
                <tbody>
                <% for (Map<String,Object> r : statusDist) { %>
                <tr><td><%= h(r.get("total")) %></td><td><%= h(r.get("pending")) %></td>
                    <td><%= h(r.get("paid")) %></td><td><%= h(r.get("checked")) %></td>
                    <td><%= h(r.get("completed")) %></td><td><%= h(r.get("refunded")) %></td>
                    <td><%= h(r.get("cancelled")) %></td></tr>
                <% } %>
                </tbody>
            </table>
        </div>
    </main>
</div>
</body>
</html>
