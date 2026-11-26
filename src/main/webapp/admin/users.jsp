<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.User,java.util.List,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    List<User> users = (List<User>) request.getAttribute("users");
    if (users == null) users = new java.util.ArrayList<User>();
    String role = (String) request.getAttribute("role"); if (role == null) role = "all";
    String keyword = (String) request.getAttribute("keyword"); if (keyword == null) keyword = "";
    String msg = (String) request.getAttribute("msg");
    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>用户管理 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        .form-row { display:flex; gap:12px; flex-wrap:wrap; align-items:end; margin-bottom:16px; }
        .form-row label { font-size:13px; display:block; color:#374151; }
        .form-row input,.form-row select { padding:8px 10px; border:1px solid #d1d5db; border-radius:6px; }
        .btn-sm { padding:6px 12px; border:0; border-radius:6px; cursor:pointer; font-size:13px; }
        .btn-add { background:#2563eb; color:#fff; }
        .btn-del { background:#dc2626; color:#fff; }
        .tag { padding:2px 8px; border-radius:10px; font-size:12px; }
        .tag-ok { background:#dcfce7; color:#15803d; }
        .tag-no { background:#fee2e2; color:#b91c1c; }
    </style>
</head>
<body class="admin">
<header class="admin-head">🚢 码头管理系统 · 用户管理　管理员：${sessionScope.userName}
    <a class="logout" href="${pageContext.request.contextPath}/logout">退出</a>
</header>
<div class="admin-layout">
    <aside class="admin-menu">
        <div><a href="home.jsp">数据概览</a></div>
        <div><a href="route">航线管理</a></div>
        <div><a href="ship">船舶管理</a></div>
        <div><a href="schedule">班次管理</a></div>
        <div class="active">👥 用户管理</div>
        <div><a href="orders">📋 订单管理</a></div>
        <div><a href="stats">📊 统计报表</a></div>
    </aside>
    <main class="admin-main">
        <h2>👥 用户管理</h2>
        <% if (msg != null && !msg.isEmpty()) { %><p class="msg ok"><%= msg %></p><% } %>

        <form class="form-row" method="get" action="users">
            <div><label>角色</label>
                <select name="role">
                    <option value="all" <%= "all".equals(role) ? "selected" : "" %>>全部</option>
                    <option value="admin" <%= "admin".equals(role) ? "selected" : "" %>>管理员</option>
                    <option value="seller" <%= "seller".equals(role) ? "selected" : "" %>>售票员</option>
                    <option value="passenger" <%= "passenger".equals(role) ? "selected" : "" %>>乘客</option>
                </select>
            </div>
            <div><label>关键字（用户名 / 姓名 / 手机）</label><input name="keyword" value="<%= keyword %>"></div>
            <div><button type="submit" class="btn-sm btn-add">查询</button></div>
        </form>

        <table class="data-table">
            <thead>
            <tr><th>ID</th><th>用户名</th><th>姓名</th><th>角色</th><th>手机</th>
                <th>状态</th><th>注册时间</th><th>操作</th></tr>
            </thead>
            <tbody>
            <% for (User u : users) { %>
                <tr>
                    <td><%= u.getId() %></td>
                    <td><%= u.getUsername() %></td>
                    <td><%= u.getRealName() %></td>
                    <td><%= "admin".equals(u.getRole()) ? "管理员" : "seller".equals(u.getRole()) ? "售票员" : "乘客" %></td>
                    <td><%= u.getPhone() %></td>
                    <td>
                        <span class="tag <%= "normal".equals(u.getStatus()) ? "tag-ok" : "tag-no" %>">
                            <%= "normal".equals(u.getStatus()) ? "正常" : "已禁用" %></span>
                        <% if (u.getFailCount() >= 5) { %><br><span class="tag tag-no">密码已锁定</span><% } %>
                    </td>
                    <td><%= u.getCreateTime() == null ? "" : fmt.format(u.getCreateTime()) %></td>
                    <td>
                        <form method="post" action="users" style="display:inline">
                            <input type="hidden" name="action" value="status">
                            <input type="hidden" name="id" value="<%= u.getId() %>">
                            <input type="hidden" name="to" value="<%= "normal".equals(u.getStatus()) ? "disabled" : "normal" %>">
                            <button type="submit" class="btn-sm <%= "normal".equals(u.getStatus()) ? "btn-del" : "btn-add" %>"
                                onclick="return confirm('<%= "normal".equals(u.getStatus()) ? "确定禁用该用户？" : "确定启用该用户？" %>')">
                                <%= "normal".equals(u.getStatus()) ? "禁用" : "启用" %></button>
                        </form>
                        <form method="post" action="users" style="display:inline">
                            <input type="hidden" name="action" value="reset">
                            <input type="hidden" name="id" value="<%= u.getId() %>">
                            <button type="submit" class="btn-sm btn-add"
                                onclick="return confirm('确定将该用户密码重置为 123456？')">重置密码</button>
                        </form>
                    </td>
                </tr>
            <% } %>
            </tbody>
        </table>
        <p class="muted">说明：共 <%= users.size() %> 条记录。禁用后该账号无法登录；重置密码默认为 123456。</p>
    </main>
</div>
</body>
</html>
