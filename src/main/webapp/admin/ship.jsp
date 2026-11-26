<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Ship,java.util.List" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    List<Ship> list = (List<Ship>) request.getAttribute("list");
    if (list == null) list = new java.util.ArrayList<Ship>();
    String msg = (String) request.getAttribute("msg");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>船舶管理 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        .form-row { display: flex; gap: 12px; flex-wrap: wrap; align-items: end; margin-bottom: 16px; }
        .form-row label { font-size: 13px; display: block; color:#374151; }
        .form-row input,.form-row select { padding: 8px 10px; border:1px solid #d1d5db; border-radius:6px; }
        .btn-sm { padding:6px 12px; border:0; border-radius:6px; cursor:pointer; font-size:13px; }
        .btn-add { background:#2563eb; color:#fff; }
        .btn-del { background:#dc2626; color:#fff; }
    </style>
</head>
<body class="admin">
<header class="admin-head">🚢 码头管理系统 · 船舶管理　管理员：${sessionScope.userName}
    <a class="logout" href="${pageContext.request.contextPath}/logout">退出</a>
</header>
<div class="admin-layout">
    <aside class="admin-menu">
        <div><a href="home.jsp">数据概览</a></div>
        <div><a href="route">航线管理</a></div>
        <div class="active">船舶管理</div>
        <div><a href="schedule">班次管理</a></div>
        <div><a href="users">👥 用户管理</a></div>
        <div><a href="orders">📋 订单管理</a></div>
        <div><a href="stats">📊 统计报表</a></div>
    </aside>
    <main class="admin-main">
        <h2>船舶管理</h2>
        <% if (msg != null && !msg.isEmpty()) { %><p class="msg ok"><%= msg %></p><% } %>

        <form method="post" action="ship">
            <input type="hidden" name="action" id="action" value="add">
            <input type="hidden" name="id" id="id" value="">
            <div class="form-row">
                <div><label>船名</label><input name="shipName" id="shipName" required></div>
                <div><label>座位数</label><input name="totalSeats" id="totalSeats" type="number" min="1" value="100" required></div>
                <div><label>船型</label>
                    <select name="shipType" id="shipType">
                        <option>普通船</option><option>高速船</option><option>豪华船</option>
                    </select>
                </div>
                <div style="flex:1"><label>描述</label><input name="description" id="description" style="width:100%"></div>
                <div><button type="submit" class="btn-sm btn-add" id="submitBtn">新增船舶</button></div>
            </div>
        </form>

        <table class="data-table">
            <tr><th>ID</th><th>船名</th><th>座位数</th><th>船型</th><th>描述</th><th>状态</th><th>操作</th></tr>
            <% for (Ship s : list) { %>
                <tr>
                    <td><%= s.getId() %></td>
                    <td><%= s.getShipName() %></td>
                    <td><%= s.getTotalSeats() %></td>
                    <td><%= s.getShipType() %></td>
                    <td><%= s.getDescription()==null?"":s.getDescription() %></td>
                    <td><%= "active".equals(s.getStatus()) ? "启用" : "停用" %></td>
                    <td>
                        <button class="btn-sm btn-add" onclick="edit(<%= s.getId() %>,'<%= s.getShipName() %>',<%= s.getTotalSeats() %>,'<%= s.getShipType() %>','<%= s.getDescription()==null?"":s.getDescription() %>')">编辑</button>
                        <a href="ship?action=delete&id=<%= s.getId() %>" onclick="return confirm('确定删除该船舶？')"><button class="btn-sm btn-del">删除</button></a>
                    </td>
                </tr>
            <% } %>
        </table>
        <p class="muted">说明：新增船舶时会在一个事务里自动生成对应数量的船舶座位（ship_seat）。仍在用班次中的船舶不允许删除。</p>
    </main>
</div>
<script>
    function edit(id,name,seats,type,desc){
        document.getElementById('action').value='edit';
        document.getElementById('id').value=id;
        document.getElementById('shipName').value=name;
        document.getElementById('totalSeats').value=seats;
        document.getElementById('shipType').value=type;
        document.getElementById('description').value=desc;
        document.getElementById('submitBtn').innerText='保存修改';
        window.scrollTo(0,0);
    }
</script>
</body>
</html>
