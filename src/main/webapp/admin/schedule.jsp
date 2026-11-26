<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Schedule,com.ferry.entity.Route,com.ferry.entity.Ship,java.util.List,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    List<Schedule> list = (List<Schedule>) request.getAttribute("list");
    List<Route> routes = (List<Route>) request.getAttribute("routes");
    List<Ship> ships = (List<Ship>) request.getAttribute("ships");
    if (list == null) list = new java.util.ArrayList<Schedule>();
    if (routes == null) routes = new java.util.ArrayList<Route>();
    if (ships == null) ships = new java.util.ArrayList<Ship>();
    String msg = (String) request.getAttribute("msg");
    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>班次管理 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        .form-row { display: flex; gap: 12px; flex-wrap: wrap; align-items: end; margin-bottom: 16px; }
        .form-row label { font-size: 13px; display: block; color:#374151; }
        .form-row input,.form-row select { padding: 8px 10px; border:1px solid #d1d5db; border-radius:6px; }
        .btn-sm { padding:6px 12px; border:0; border-radius:6px; cursor:pointer; font-size:13px; }
        .btn-add { background:#2563eb; color:#fff; }
        .btn-del { background:#dc2626; color:#fff; }
        .btn-status { background:#0891b2; color:#fff; }
        .btn-ok { background:#16a34a; color:#fff; }
        .op-group { display:flex; gap:4px; flex-wrap:wrap; }
    </style>
</head>
<body class="admin">
<header class="admin-head">🚢 码头管理系统 · 班次管理　管理员：${sessionScope.userName}
    <a class="logout" href="${pageContext.request.contextPath}/logout">退出</a>
</header>
<div class="admin-layout">
    <aside class="admin-menu">
        <div><a href="home.jsp">数据概览</a></div>
        <div><a href="route">航线管理</a></div>
        <div><a href="ship">船舶管理</a></div>
        <div class="active">班次管理</div>
        <div><a href="users">👥 用户管理</a></div>
        <div><a href="orders">📋 订单管理</a></div>
        <div><a href="stats">📊 统计报表</a></div>
    </aside>
    <main class="admin-main">
        <h2>班次管理</h2>
        <% if (msg != null && !msg.isEmpty()) { %><p class="msg ok"><%= msg %></p><% } %>

        <form method="post" action="schedule">
            <input type="hidden" name="action" id="action" value="add">
            <input type="hidden" name="id" id="id" value="">
            <div class="form-row">
                <div><label>航线</label><select name="routeId" id="routeId">
                    <% for (Route r : routes) { %><option value="<%= r.getId() %>"><%= r.getRouteName() %>（<%= r.getStartPort() %>→<%= r.getEndPort() %>）</option><% } %>
                </select></div>
                <div><label>船舶</label><select name="shipId" id="shipId">
                    <% for (Ship s : ships) { %><option value="<%= s.getId() %>"><%= s.getShipName() %>（<%= s.getTotalSeats() %>座）</option><% } %>
                </select></div>
                <div><label>发船时间</label><input name="departTime" id="departTime" placeholder="2026-11-25 08:00" required></div>
                <div><label>票价</label><input name="price" id="price" type="number" step="0.01" min="0.01" value="80" required></div>
                <div><label>座位数</label><input name="totalSeats" id="totalSeats" type="number" min="1" value="100" required></div>
                <div><button type="submit" class="btn-sm btn-add" id="submitBtn">新增班次</button></div>
            </div>
        </form>

        <table class="data-table">
            <tr><th>ID</th><th>航线</th><th>船舶</th><th>发船</th><th>到达</th><th>票价</th><th>已售/总数</th><th>状态</th><th>操作</th></tr>
            <% for (Schedule s : list) { %>
                <tr>
                    <td><%= s.getId() %></td>
                    <td><%= s.getRouteName() %></td>
                    <td><%= s.getShipName() %></td>
                    <td><%= fmt.format(s.getDepartTime()) %></td>
                    <td><%= fmt.format(s.getArriveTime()) %></td>
                    <td>￥<%= s.getPrice() %></td>
                    <td><%= s.getSoldSeats() %>/<%= s.getTotalSeats() %></td>
                    <td><%= s.getStatus() %></td>
                    <td>
                        <div class="op-group">
                        <% if (!"已发船".equals(s.getStatus()) && !"已完成".equals(s.getStatus())) { %>
                            <a href="schedule?action=status&id=<%= s.getId() %>&to=<%= java.net.URLEncoder.encode("已发船","UTF-8") %>" onclick="return confirm('强制发船后该班次立即停止售票，确定？')"><button class="btn-sm btn-status">强制发船</button></a>
                        <% } %>
                        <% if (!"已完成".equals(s.getStatus())) { %>
                            <a href="schedule?action=status&id=<%= s.getId() %>&to=<%= java.net.URLEncoder.encode("已完成","UTF-8") %>" onclick="return confirm('置为已完成后，已检票订单同步闭环，确定？')"><button class="btn-sm btn-ok">置为完成</button></a>
                        <% } %>
                        <% if ("已发船".equals(s.getStatus()) || "待发布".equals(s.getStatus())) { %>
                            <a href="schedule?action=status&id=<%= s.getId() %>&to=<%= java.net.URLEncoder.encode("售票中","UTF-8") %>" onclick="return confirm('恢复售票后乘客可重新购买（发船时间已过会被拒绝），确定？')"><button class="btn-sm btn-add">恢复售票</button></a>
                        <% } %>
                        <a href="schedule?action=cancel&id=<%= s.getId() %>" onclick="return confirm('取消班次将自动全额退票，确定？')"><button class="btn-sm btn-del">取消班次</button></a>
                        </div>
                    </td>
                </tr>
            <% } %>
        </table>
        <p class="muted">说明：创建班次时一个事务里自动初始化该船全部座位，到达时间按航线时长自动算。取消班次用事务完成退票+座位回滚+逻辑删除。同一艘船同一发船时间不允许重复班次。<br>
        状态流转：待发布 → 售票中 → 已发船 → 已完成。系统每 60 秒自动按发船/到达时间流转；这里也可手动强制切换（演示用）。恢复售票会在发船时间已过时被拒绝。置为完成会把已检票订单同步改为“已完成”。</p>
    </main>
</div>
</body>
</html>
