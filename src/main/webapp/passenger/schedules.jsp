<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.dao.ScheduleDAO,com.ferry.entity.Schedule,java.util.List,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    // 下单前先触发一次超时锁释放
    new com.ferry.dao.OrderDAO().releaseExpiredLocks();
    List<Schedule> all = new ScheduleDAO().findAll();
    // 只保留真正可购票的班次（售票中 + 有余票），标题数量与列表一致
    List<Schedule> list = new java.util.ArrayList<Schedule>();
    for (Schedule s : all) {
        if ("售票中".equals(s.getStatus()) && s.getRemainSeats() > 0) list.add(s);
    }
    SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
    String msg = request.getParameter("msg");
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
    <div class="phone-head">🔍 可购票班次 · 共 <%= list.size() %> 个</div>
    <div class="phone-body">
        <h2>你好，${sessionScope.userName}</h2>
        <% if (msg != null && !msg.isEmpty()) { %><p class="msg err"><%= msg %></p><% } %>
        <% if (list.isEmpty()) { %>
        <div class="card muted">暂无可购票班次，请稍后再来</div>
        <% } %>
        <% for (Schedule s : list) { %>
        <div class="card">
            <div class="route-name"><%= s.getRouteName() %></div>
            <div class="muted"><%= s.getStartPort() %> → <%= s.getEndPort() %>　<%= s.getShipName() %></div>
            <div class="facts">
                <span>发船 <b><%= fmt.format(s.getDepartTime()) %></b></span>
                <span>票价 <b class="price">￥<%= s.getPrice() %></b></span>
            </div>
            <div class="remain">剩余座位 <%= s.getRemainSeats() %>/<%= s.getTotalSeats() %></div>
            <a href="${pageContext.request.contextPath}/book?action=seats&scheduleId=<%= s.getId() %>">
                <div class="big-btn">选座购票</div>
            </a>
        </div>
        <% } %>
        <div class="big-btn ghost" onclick="location.href='home.jsp'">返回首页</div>
    </div>
    <div class="tabbar">
        <a href="${pageContext.request.contextPath}/passenger/home.jsp"><span>🏠 首页</span></a>
        <a href="${pageContext.request.contextPath}/passenger/schedules.jsp"><span class="active">🎫 班次</span></a>
        <a href="${pageContext.request.contextPath}/passenger/orders"><span>📋 订单</span></a>
        <a href="${pageContext.request.contextPath}/passenger/profile"><span>👤 我的</span></a>
    </div>
</div>
</body>
</html>
