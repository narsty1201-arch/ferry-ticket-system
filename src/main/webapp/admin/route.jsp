<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Route,java.util.List" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    List<Route> list = (List<Route>) request.getAttribute("list");
    if (list == null) list = new java.util.ArrayList<Route>();
    String msg = (String) request.getAttribute("msg");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>航线管理 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        .form-row { display: flex; gap: 12px; flex-wrap: wrap; align-items: end; margin-bottom: 16px; }
        .form-row label { font-size: 13px; display: block; color:#374151; }
        .form-row input { padding: 8px 10px; border:1px solid #d1d5db; border-radius:6px; }
        .btn-sm { padding:6px 12px; border:0; border-radius:6px; cursor:pointer; font-size:13px; }
        .btn-add { background:#2563eb; color:#fff; }
        .btn-del { background:#dc2626; color:#fff; }
        .btn-toggle { background:#f59e0b; color:#fff; }
    </style>
</head>
<body class="admin">
<header class="admin-head">🚢 码头管理系统 · 航线管理　管理员：${sessionScope.userName}
    <a class="logout" href="${pageContext.request.contextPath}/logout">退出</a>
</header>
<div class="admin-layout">
    <aside class="admin-menu">
        <div><a href="${pageContext.request.contextPath}/admin/home">数据概览</a></div>
        <div class="active">航线管理</div>
        <div><a href="ship">船舶管理</a></div>
        <div><a href="schedule">班次管理</a></div>
        <div><a href="users">👥 用户管理</a></div>
        <div><a href="orders">📋 订单管理</a></div>
        <div><a href="stats">📊 统计报表</a></div>
    </aside>
    <main class="admin-main">
        <h2>航线管理</h2>
        <% if (msg != null && !msg.isEmpty()) { %><p class="msg ok"><%= msg %></p><% } %>

        <form method="post" action="route">
            <input type="hidden" name="action" id="action" value="add">
            <input type="hidden" name="id" id="id" value="">
            <div class="form-row">
                <div><label>航线名称</label><input name="routeName" id="routeName" required></div>
                <div><label>出发港</label><input name="startPort" id="startPort" required></div>
                <div><label>到达港</label><input name="endPort" id="endPort" required></div>
                <div><label>航行时长(分钟)</label><input name="durationMin" id="durationMin" type="number" min="1" value="90" required></div>
                <div style="flex:1"><label>描述</label><input name="description" id="description" style="width:100%"></div>
                <div><button type="submit" class="btn-sm btn-add" id="submitBtn">新增航线</button></div>
            </div>
        </form>

        <table class="data-table">
            <tr><th>ID</th><th>航线名称</th><th>出发港</th><th>到达港</th><th>时长(分)</th><th>状态</th><th>操作</th></tr>
            <% for (Route r : list) { %>
                <tr>
                    <td><%= r.getId() %></td>
                    <td><%= r.getRouteName() %></td>
                    <td><%= r.getStartPort() %></td>
                    <td><%= r.getEndPort() %></td>
                    <td><%= r.getDurationMin() %></td>
                    <td><%= "active".equals(r.getStatus()) ? "启用" : "停开" %></td>
                    <td>
                        <button class="btn-sm btn-add" onclick="edit(<%= r.getId() %>,'<%= r.getRouteName() %>','<%= r.getStartPort() %>','<%= r.getEndPort() %>',<%= r.getDurationMin() %>,'<%= r.getDescription()==null?"":r.getDescription() %>')">编辑</button>
                        <a href="route?action=status&id=<%= r.getId() %>&to=<%= r.getStatus() %>"><button class="btn-sm btn-toggle"><%= "active".equals(r.getStatus()) ? "停开" : "启用" %></button></a>
                        <a href="route?action=delete&id=<%= r.getId() %>" onclick="return confirm('确定删除该航线？')"><button class="btn-sm btn-del">删除</button></a>
                    </td>
                </tr>
            <% } %>
        </table>
        <p class="muted">说明：删除为逻辑删除（is_deleted=1），不会物理删除数据。航线名称+出发港+到达港不可重复。</p>
    </main>
</div>
<script>
    function edit(id,name,sp,ep,dur,desc){
        document.getElementById('action').value='edit';
        document.getElementById('id').value=id;
        document.getElementById('routeName').value=name;
        document.getElementById('startPort').value=sp;
        document.getElementById('endPort').value=ep;
        document.getElementById('durationMin').value=dur;
        document.getElementById('description').value=desc;
        document.getElementById('submitBtn').innerText='保存修改';
        window.scrollTo(0,0);
    }
</script>
</body>
</html>
