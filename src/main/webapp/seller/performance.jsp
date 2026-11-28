<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.Map" %>
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
    Map<String,Object> summary = (Map<String,Object>) request.getAttribute("summary");
    if (summary == null) summary = new java.util.HashMap<String,Object>();
    String startDate = (String) request.getAttribute("startDate");
    String endDate = (String) request.getAttribute("endDate");
    String dateError = (String) request.getAttribute("dateError");
    String exportUrl = (String) request.getAttribute("exportUrl");
    if (exportUrl == null) exportUrl = "performance?format=xlsx&startDate=&endDate=";
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>我的业绩 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body class="desk">
<%@ include file="/WEB-INF/includes/seller-topbar.jspf" %>
<div class="desk-layout">
    <%@ include file="/WEB-INF/includes/seller-nav.jspf" %>
    <main class="desk-main">
        <div class="page-kicker"><span class="ico ico-chart ico-sm"></span>个人绩效</div>
        <h2>我的业绩</h2>
        <p class="muted">仅展示当前登录售票员本人数据。成交订单和净销售额按下单时间统计，检票数按实际检票时间统计，退款金额已从净销售额中扣除。</p>

        <form class="form-row" method="get" action="${pageContext.request.contextPath}/seller/performance">
            <div><label>开始日期</label>
                <input type="date" name="startDate" value="<%= h(startDate) %>"></div>
            <div><label>结束日期（含当天）</label>
                <input type="date" name="endDate" value="<%= h(endDate) %>"></div>
            <div><button type="submit" class="btn-sm btn-add">查看区间业绩</button>
                <a class="btn-sm btn-secondary" href="${pageContext.request.contextPath}/seller/performance">重置</a>
                <a class="btn-sm btn-secondary" href="<%= h(exportUrl) %>">导出 Excel</a></div>
        </form>
        <% if (dateError != null && !dateError.isEmpty()) { %>
        <p class="msg err"><%= h(dateError) %></p>
        <% } %>

        <div class="kpi-row">
            <div class="card kpi"><div class="muted">成交订单</div>
                <div class="kpi-num"><%= h(summary.get("order_count")) %></div>
                <div class="muted">不含待支付和已取消</div></div>
            <div class="card kpi"><div class="muted">有效船票</div>
                <div class="kpi-num"><%= h(summary.get("ticket_count")) %></div>
                <div class="muted">已支付或已检票</div></div>
            <div class="card kpi"><div class="muted">净销售额</div>
                <div class="kpi-num">￥<%= h(summary.get("total_amount")) %></div>
                <div class="muted">已扣除实际退款</div></div>
            <div class="card kpi"><div class="muted">退款金额</div>
                <div class="kpi-num">￥<%= h(summary.get("refund_amount")) %></div>
                <div class="muted">共 <%= h(summary.get("refund_count")) %> 单</div></div>
            <div class="card kpi"><div class="muted">本人成功检票</div>
                <div class="kpi-num"><%= h(summary.get("checked_count")) %></div>
                <div class="muted">按票计</div></div>
            <div class="card kpi"><div class="muted">平均单额</div>
                <div class="kpi-num">￥<%= h(summary.get("avg_amount")) %></div>
                <div class="muted">净销售额 / 成交订单</div></div>
        </div>

        <div class="sec">
            <h3>口径说明</h3>
            <table class="data-table">
                <thead><tr><th>指标</th><th>统计方式</th></tr></thead>
                <tbody>
                <tr><td>成交订单</td><td>状态为已支付、已检票、已完成或已退票的经办订单</td></tr>
                <tr><td>有效船票</td><td>所属成交订单中状态为已支付或已检票的船票</td></tr>
                <tr><td>净销售额</td><td>实收订单全额 + 退票订单未退部分；不包含待支付与已取消订单</td></tr>
                <tr><td>本人成功检票</td><td>当前账号在所选区间内成功检票的票数，不按售票归属计算</td></tr>
                </tbody>
            </table>
        </div>
    </main>
</div>
</body>
</html>
