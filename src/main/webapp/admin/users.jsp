<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.User,com.ferry.util.Page,java.util.List,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    Page<User> pageData = (Page<User>) request.getAttribute("pageData");
    if (pageData == null) pageData = Page.empty(1, 10);
    List<User> users = pageData.getItems();
    String pageUrl = (String) request.getAttribute("pageUrl");
    if (pageUrl == null) pageUrl = "users?page=";
    String role = (String) request.getAttribute("role"); if (role == null) role = "all";
    String keyword = (String) request.getAttribute("keyword"); if (keyword == null) keyword = "";
    String msg = (String) request.getAttribute("msg");
    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>用户管理 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body class="admin">
<%@ include file="/WEB-INF/includes/admin-topbar.jspf" %>
<div class="admin-layout">
    <%@ include file="/WEB-INF/includes/admin-nav.jspf" %>
    <main class="admin-main">
        <div class="page-kicker"><span class="ico ico-users ico-sm"></span>账号管理</div>
        <h2>用户管理</h2>
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
        <%@ include file="/WEB-INF/pagination.jspf" %>
        <p class="muted">说明：禁用后该账号无法登录；重置密码默认为 123456。</p>
    </main>
</div>
</body>
</html>
