<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Schedule,java.util.List,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    @SuppressWarnings("unchecked")
    List<Schedule> list = (List<Schedule>) request.getAttribute("schedules");
    if (list == null) list = new java.util.ArrayList<Schedule>();
    SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
    String msg = (String) request.getAttribute("msg");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>查询班次 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body class="mobile">
<div class="phone">
    <div class="phone-head"><span class="ico ico-search"></span>可购票班次 · 共 <%= list.size() %> 个</div>
    <div class="phone-body">
        <h2>你好，${sessionScope.userName}</h2>
        <p class="muted">选择一条航线，查看实时余票与座位。</p>
        <% if (msg != null && !msg.isEmpty()) { %><p class="msg err"><%= msg %></p><% } %>
        <% if (list.isEmpty()) { %>
        <div class="card muted"><span class="ico ico-info"></span> 暂无可购票班次，请稍后再来</div>
        <% } %>
        <% for (Schedule s : list) { %>
        <div class="card schedule-card">
            <div class="route-name"><%= s.getRouteName() %></div>
            <div class="muted"><%= s.getStartPort() %> → <%= s.getEndPort() %>　<%= s.getShipName() %></div>
            <div class="facts">
                <span>发船 <b><%= fmt.format(s.getDepartTime()) %></b></span>
                <span>票价 <b class="price">￥<%= s.getPrice() %></b></span>
            </div>
            <div class="remain">剩余座位 <%= s.getRemainSeats() %>/<%= s.getTotalSeats() %></div>
            <a href="${pageContext.request.contextPath}/book?action=seats&scheduleId=<%= s.getId() %>">
                <div class="big-btn"><span class="ico ico-seat"></span>选择座位</div>
            </a>
        </div>
        <% } %>
        <div class="big-btn ghost" onclick="location.href='${pageContext.request.contextPath}/passenger/home'"><span class="ico ico-back"></span>返回首页</div>
    </div>
    <%@ include file="/WEB-INF/includes/passenger-tabbar.jspf" %>
</div>
</body>
</html>
