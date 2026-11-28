<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.User,com.ferry.util.Page,java.util.List,java.util.Map,java.text.SimpleDateFormat" %>
<%!
    private String h(Object value) {
        return com.ferry.util.HtmlUtil.escape(value);
    }
    private String roleLabel(String role) {
        if ("admin".equals(role)) return "管理员";
        if ("seller".equals(role)) return "售票员";
        return "乘客";
    }
%>
<%
    if (session.getAttribute("loginUser") == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp");
        return;
    }
    @SuppressWarnings("unchecked")
    Page<User> pageData = (Page<User>) request.getAttribute("pageData");
    if (pageData == null) pageData = Page.empty(1, 10);
    List<User> users = pageData.getItems();
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> adminLogs = (List<Map<String,Object>>) request.getAttribute("adminLogs");
    if (adminLogs == null) adminLogs = new java.util.ArrayList<Map<String,Object>>();
    String pageUrl = (String) request.getAttribute("pageUrl");
    if (pageUrl == null) pageUrl = "users?page=";
    String role = (String) request.getAttribute("role");
    if (role == null) role = "all";
    String keyword = (String) request.getAttribute("keyword");
    if (keyword == null) keyword = "";
    String msg = (String) request.getAttribute("msg");
    String formMode = (String) request.getAttribute("formMode");
    String formError = (String) request.getAttribute("formError");
    User formUser = (User) request.getAttribute("formUser");
    if (formUser == null) formUser = new User();
    User current = (User) session.getAttribute("loginUser");
    boolean showForm = "add".equals(formMode) || "edit".equals(formMode);
    boolean editing = "edit".equals(formMode);
    boolean editingSelf = editing && current.getId() == formUser.getId();
    SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
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
        <% if (msg != null && !msg.isEmpty()) { %><p class="msg ok"><%= h(msg) %></p><% } %>

        <% if (showForm) { %>
        <div class="card" style="padding:18px;margin-bottom:18px">
            <div class="page-kicker"><span class="ico <%= editing ? "ico-users" : "ico-add" %> ico-sm"></span>
                <%= editing ? "编辑账号" : "新增账号" %></div>
            <% if (formError != null && !formError.isEmpty()) { %>
            <p class="msg err"><%= h(formError) %></p>
            <% } %>
            <form method="post" action="${pageContext.request.contextPath}/admin/users">
                <input type="hidden" name="action" value="<%= editing ? "update" : "create" %>">
                <% if (editing) { %><input type="hidden" name="id" value="<%= formUser.getId() %>"><% } %>
                <div class="form-row">
                    <div><label>用户名</label>
                        <% if (editing) { %>
                        <input value="<%= h(formUser.getUsername()) %>" readonly>
                        <span class="muted">用户名创建后不可修改</span>
                        <% } else { %>
                        <input name="username" value="<%= h(formUser.getUsername()) %>"
                               required maxlength="20" pattern="[A-Za-z0-9_]{4,20}"
                               placeholder="4-20位字母、数字或下划线">
                        <% } %>
                    </div>
                    <div><label>角色</label>
                        <% if (editingSelf) { %>
                        <input value="<%= roleLabel(formUser.getRole()) %>" readonly>
                        <input type="hidden" name="role" value="<%= h(formUser.getRole()) %>">
                        <span class="muted">不能修改自己的角色</span>
                        <% } else { %>
                        <select name="role" id="userRole">
                            <option value="passenger" <%= "passenger".equals(formUser.getRole()) ? "selected" : "" %>>乘客</option>
                            <option value="seller" <%= "seller".equals(formUser.getRole()) ? "selected" : "" %>>售票员</option>
                            <option value="admin" <%= "admin".equals(formUser.getRole()) ? "selected" : "" %>>管理员</option>
                        </select>
                        <% } %>
                    </div>
                    <div><label>姓名</label>
                        <input name="realName" value="<%= h(formUser.getRealName()) %>"
                               required maxlength="20" placeholder="2-20个字符"></div>
                    <div><label>手机号</label>
                        <input name="phone" value="<%= h(formUser.getPhone()) %>"
                               required maxlength="11" pattern="1[0-9]{10}" placeholder="11位手机号"></div>
                </div>
                <div class="form-row">
                    <div><label>身份证号（乘客必填，其他角色可留空）</label>
                        <input name="idCard" id="userIdCard" value="<%= h(formUser.getIdCard()) %>"
                               maxlength="18" placeholder="18位身份证号"></div>
                    <div><label><%= editing ? "新密码（留空则不修改）" : "初始密码" %></label>
                        <input type="password" name="<%= editing ? "newPassword" : "password" %>"
                               <%= editing ? "" : "required" %> minlength="6"
                               autocomplete="new-password" placeholder="至少6位"></div>
                    <div><label>确认密码</label>
                        <input type="password" name="confirmPassword"
                               <%= editing ? "" : "required" %> minlength="6"
                               autocomplete="new-password" placeholder="再次输入密码"></div>
                    <div style="align-self:end;display:flex;gap:8px">
                        <button type="submit" class="btn-sm btn-add">保存</button>
                        <a class="btn-sm btn-secondary" href="${pageContext.request.contextPath}/admin/users">取消</a>
                    </div>
                </div>
                <p class="muted">编辑时填写新密码会同时重置密码并解除登录失败锁定；留空则保留原密码。</p>
            </form>
        </div>
        <% } %>

        <div style="display:flex;gap:10px;align-items:end;justify-content:space-between;flex-wrap:wrap">
            <form class="form-row" method="get" action="${pageContext.request.contextPath}/admin/users">
                <div><label>角色</label>
                    <select name="role">
                        <option value="all" <%= "all".equals(role) ? "selected" : "" %>>全部</option>
                        <option value="admin" <%= "admin".equals(role) ? "selected" : "" %>>管理员</option>
                        <option value="seller" <%= "seller".equals(role) ? "selected" : "" %>>售票员</option>
                        <option value="passenger" <%= "passenger".equals(role) ? "selected" : "" %>>乘客</option>
                    </select>
                </div>
                <div><label>关键字（用户名 / 姓名 / 手机）</label>
                    <input name="keyword" value="<%= h(keyword) %>"></div>
                <div><button type="submit" class="btn-sm btn-add">查询</button></div>
            </form>
            <% if (!showForm) { %>
            <a class="btn-sm btn-add" href="${pageContext.request.contextPath}/admin/users?action=add">新增账号</a>
            <% } %>
        </div>

        <table class="data-table">
            <thead>
            <tr><th>ID</th><th>用户名</th><th>姓名</th><th>角色</th><th>手机</th>
                <th>状态</th><th>注册时间</th><th>操作</th></tr>
            </thead>
            <tbody>
            <% if (users.isEmpty()) { %>
            <tr><td colspan="8" class="muted">没有符合条件的用户</td></tr>
            <% } %>
            <% for (User u : users) { %>
                <tr>
                    <td><%= u.getId() %></td>
                    <td><%= h(u.getUsername()) %></td>
                    <td><%= h(u.getRealName()) %></td>
                    <td><%= roleLabel(u.getRole()) %></td>
                    <td><%= h(u.getPhone()) %></td>
                    <td>
                        <span class="tag <%= "normal".equals(u.getStatus()) ? "tag-ok" : "tag-no" %>">
                            <%= "normal".equals(u.getStatus()) ? "正常" : "已禁用" %></span>
                        <% if (u.getFailCount() >= 5) { %><br><span class="tag tag-no">密码已锁定</span><% } %>
                    </td>
                    <td><%= u.getCreateTime() == null ? "" : fmt.format(u.getCreateTime()) %></td>
                    <td>
                        <a class="btn-sm btn-add"
                           href="${pageContext.request.contextPath}/admin/users?action=edit&id=<%= u.getId() %>">编辑</a>
                        <% if (current.getId() != u.getId()) { %>
                        <form method="post" action="${pageContext.request.contextPath}/admin/users" style="display:inline">
                            <input type="hidden" name="action" value="status">
                            <input type="hidden" name="id" value="<%= u.getId() %>">
                            <input type="hidden" name="to" value="<%= "normal".equals(u.getStatus()) ? "disabled" : "normal" %>">
                            <button type="submit" class="btn-sm <%= "normal".equals(u.getStatus()) ? "btn-del" : "btn-add" %>"
                                onclick="return confirm('<%= "normal".equals(u.getStatus()) ? "确定禁用该用户？" : "确定启用该用户？" %>')">
                                <%= "normal".equals(u.getStatus()) ? "禁用" : "启用" %></button>
                        </form>
                        <% } else { %>
                        <span class="muted">当前账号</span>
                        <% } %>
                    </td>
                </tr>
            <% } %>
            </tbody>
        </table>
        <%@ include file="/WEB-INF/pagination.jspf" %>

        <div class="sec">
            <h3>账号管理审计（最近 20 条）</h3>
            <table class="data-table">
                <thead><tr><th>时间</th><th>操作人</th><th>目标账号</th><th>动作</th><th>说明</th></tr></thead>
                <tbody>
                <% if (adminLogs.isEmpty()) { %>
                <tr><td colspan="5" class="muted">暂无账号管理操作记录</td></tr>
                <% } %>
                <% for (Map<String,Object> log : adminLogs) { %>
                <tr><td><%= h(log.get("create_time")) %></td><td><%= h(log.get("operator_name")) %></td>
                    <td><%= h(log.get("target_name")) %></td>
                    <td><span class="tag tag-warn"><%= h(log.get("action")) %></span></td>
                    <td><%= h(log.get("detail")) %></td></tr>
                <% } %>
                </tbody>
            </table>
        </div>
        <p class="muted">密码使用课程要求的摘要方式保存；管理员不能禁用或降级自身，也不能禁用/降级最后一个可用管理员。</p>
    </main>
</div>
<script>
    (function () {
        var role = document.getElementById('userRole');
        var idCard = document.getElementById('userIdCard');
        if (!role || !idCard) return;
        function syncRequired() {
            idCard.required = role.value === 'passenger';
        }
        role.addEventListener('change', syncRequired);
        syncRequired();
    })();
</script>
</body>
</html>
