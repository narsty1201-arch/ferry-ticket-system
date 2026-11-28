<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Order,com.ferry.util.Page,java.util.List,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    Page<Order> pageData = (Page<Order>) request.getAttribute("pageData");
    if (pageData == null) pageData = Page.empty(1, 10);
    List<Order> orders = pageData.getItems();
    String pageUrl = (String) request.getAttribute("pageUrl");
    if (pageUrl == null) pageUrl = "orders?page=";
    java.util.Map<Integer, java.util.List<String>> paymentLogs =
        (java.util.Map<Integer, java.util.List<String>>) request.getAttribute("paymentLogs");
    if (paymentLogs == null) paymentLogs = new java.util.HashMap<Integer, java.util.List<String>>();
    String st = (String) request.getAttribute("status"); if (st == null) st = "all";
    String keyword = (String) request.getAttribute("keyword"); if (keyword == null) keyword = "";
    String msg = (String) request.getAttribute("msg");
    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>订单管理 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        .form-row { display:flex; gap:12px; flex-wrap:wrap; align-items:end; margin-bottom:16px; }
        .form-row label { font-size:13px; display:block; color:#374151; }
        .form-row input,.form-row select { padding:8px 10px; border:1px solid #d1d5db; border-radius:6px; }
        .btn-sm { padding:6px 12px; border:0; border-radius:6px; cursor:pointer; font-size:13px; }
        .btn-add { background:#2563eb; color:#fff; }
        .tag { padding:2px 8px; border-radius:10px; font-size:12px; background:#e5e7eb; color:#374151; }
        .tag-paid { background:#dcfce7; color:#15803d; }
        .tag-warn { background:#fef3c7; color:#b45309; }
        .tag-bad { background:#fee2e2; color:#b91c1c; }
        .detail td { background:#f8fafc; font-size:13px; color:#374151; }
    </style>
</head>
<body class="admin">
<header class="admin-head">🚢 码头管理系统 · 订单管理　管理员：${sessionScope.userName}
    <a class="logout" href="${pageContext.request.contextPath}/logout">退出</a>
</header>
<div class="admin-layout">
    <aside class="admin-menu">
        <div><a href="${pageContext.request.contextPath}/admin/home">数据概览</a></div>
        <div><a href="route">航线管理</a></div>
        <div><a href="ship">船舶管理</a></div>
        <div><a href="schedule">班次管理</a></div>
        <div><a href="users">👥 用户管理</a></div>
        <div class="active">📋 订单管理</div>
        <div><a href="stats">📊 统计报表</a></div>
    </aside>
    <main class="admin-main">
        <h2>📋 订单管理</h2>
        <% if (msg != null && !msg.isEmpty()) { %><p class="msg ok"><%= msg %></p><% } %>

        <form class="form-row" method="get" action="orders">
            <div><label>订单状态</label>
                <select name="status">
                    <%
                        String[] statuses = {"all","待支付","已支付","已检票","已完成","已取消","已退票"};
                        for (String s : statuses) {
                            String label = "all".equals(s) ? "全部" : s;
                    %>
                    <option value="<%= s %>" <%= s.equals(st) ? "selected" : "" %>><%= label %></option>
                    <% } %>
                </select>
            </div>
            <div><label>关键字（订单号 / 乘客姓名 / 登录账号 / 手机号）</label>
                <input name="keyword" value="<%= keyword %>" placeholder="如 FD2026..."></div>
            <div><button type="submit" class="btn-sm btn-add">查询</button></div>
        </form>

        <table class="data-table">
            <thead>
            <tr><th>订单号</th><th>乘客</th><th>航线 / 班次</th><th>座位</th><th>金额</th>
                <th>状态</th><th>下单时间</th><th>操作</th></tr>
            </thead>
            <tbody>
            <% for (Order o : orders) {
                String cls = "已支付".equals(o.getStatus()) || "已检票".equals(o.getStatus()) || "已完成".equals(o.getStatus()) ? "tag-paid"
                           : "待支付".equals(o.getStatus()) ? "tag-warn"
                           : "已取消".equals(o.getStatus()) || "已退票".equals(o.getStatus()) ? "tag-bad" : "";
            %>
                <tr>
                    <td><%= o.getOrderNo() %></td>
                    <td><%= o.getPassengerName() == null ? "-" : o.getPassengerName() %></td>
                    <td><%= o.getRouteName() == null ? "-" : o.getRouteName() %><br>
                        <span class="muted"><%= o.getDepartTime() == null ? "" : fmt.format(o.getDepartTime()) %> · <%= o.getShipName() == null ? "" : o.getShipName() %></span></td>
                    <td><%= o.getSeatNo() == null ? "-" : o.getSeatNo() %></td>
                    <td>￥<%= o.getTotalAmount() %></td>
                    <td><span class="tag <%= cls %>"><%= o.getStatus() %></span></td>
                    <td><%= o.getCreateTime() == null ? "" : fmt.format(o.getCreateTime()) %></td>
                    <td><button type="button" class="btn-sm btn-add" onclick="toggleDetail(<%= o.getId() %>)">详情</button></td>
                </tr>
                <tr class="detail" id="detail-<%= o.getId() %>" style="display:none">
                    <td colspan="8">
                        班次ID：<%= o.getScheduleId() %>　乘客账号：<%= o.getPassengerId() == null ? "未注册乘客" : o.getPassengerId() %>　售票员ID：<%= o.getSellerId() == null ? "-" : o.getSellerId() %><br>
                        支付时间：<%= o.getPayTime() == null ? "-" : fmt.format(o.getPayTime()) %>　取消时间：<%= o.getCancelTime() == null ? "-" : fmt.format(o.getCancelTime()) %><br>
                        退款金额：<%= o.getRefundAmount() == null ? "-" : "￥" + o.getRefundAmount() %>　退款原因：<%= o.getRefundReason() == null ? "-" : o.getRefundReason() %>　退款时间：<%= o.getRefundTime() == null ? "-" : fmt.format(o.getRefundTime()) %><br>
                        预计到账：<%= o.getRefundEta() == null ? "-" : new java.text.SimpleDateFormat("yyyy-MM-dd").format(o.getRefundEta()) + " 前（3个工作日）" %><br>
                        <% java.util.List<String> logs = paymentLogs.get(o.getId());
                           if (logs != null && !logs.isEmpty()) { %>
                        资金流水：<% for (String log : logs) { %><br>　· <%= log %><% } %><% } %>
                    </td>
                </tr>
            <% } %>
            </tbody>
        </table>
        <%@ include file="/WEB-INF/pagination.jspf" %>
        <p class="muted">退款金额 / 原因 / 时间来自订单表留痕字段（任务书要求）。</p>
    </main>
</div>
<script>
    function toggleDetail(id) {
        var row = document.getElementById('detail-' + id);
        row.style.display = (row.style.display === 'none') ? '' : 'none';
    }
</script>
</body>
</html>
