<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.User,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    User p = (User) request.getAttribute("profile");
    if (p == null) p = (User) session.getAttribute("loginUser");
    String msg = (String) request.getAttribute("msg");
    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    String ctx = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>我的 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body class="mobile">
<div class="phone">
    <div class="phone-head">👤 个人中心</div>
    <div class="phone-body">
        <h2>你好，<%= p.getRealName() %></h2>
        <% if (msg != null && !msg.isEmpty()) { %><p class="msg ok"><%= msg %></p><% } %>

        <div class="card">
            <div class="muted">登录账号</div><div style="font-size:16px"><%= p.getUsername() %></div>
            <div class="muted" style="margin-top:8px">角色</div><div><%= "admin".equals(p.getRole()) ? "管理员" : "seller".equals(p.getRole()) ? "售票员" : "乘客" %></div>
            <div class="muted" style="margin-top:8px">注册时间</div><div><%= p.getCreateTime() == null ? "-" : fmt.format(p.getCreateTime()) %></div>
        </div>

        <h3>修改资料</h3>
        <div class="card">
            <form method="post" action="<%= ctx %>/passenger/profile">
                <input type="hidden" name="action" value="info">
                <div class="form-line"><label>姓名</label><input name="realName" value="<%= p.getRealName() %>" required></div>
                <div class="form-line"><label>手机号</label><input name="phone" value="<%= p.getPhone() %>" maxlength="11" required></div>
                <div class="form-line"><label>身份证号（选填）</label><input name="idCard" value="<%= p.getIdCard() == null ? "" : p.getIdCard() %>" maxlength="18"></div>
                <button type="submit" class="btn">保存资料</button>
            </form>
        </div>

        <h3>修改密码</h3>
        <div class="card">
            <form method="post" action="<%= ctx %>/passenger/profile" onsubmit="return checkPwd()">
                <input type="hidden" name="action" value="password">
                <div class="form-line"><label>原密码</label><input type="password" name="oldPassword" required></div>
                <div class="form-line"><label>新密码（至少6位）</label><input type="password" name="newPassword" id="newPassword" required></div>
                <div class="form-line"><label>确认新密码</label><input type="password" name="confirmPassword" id="confirmPassword" required></div>
                <button type="submit" class="btn">修改密码</button>
            </form>
        </div>
        <div class="big-btn ghost" onclick="location.href='<%= ctx %>/logout'">退出登录</div>
    </div>
    <div class="tabbar">
        <a href="<%= ctx %>/passenger/home"><span>🏠 首页</span></a>
        <a href="<%= ctx %>/passenger/schedules"><span>🎫 班次</span></a>
        <a href="<%= ctx %>/passenger/orders"><span>📋 订单</span></a>
        <a href="<%= ctx %>/passenger/profile"><span class="active">👤 我的</span></a>
    </div>
</div>
<script>
    function checkPwd(){
        var a = document.getElementById('newPassword').value;
        var b = document.getElementById('confirmPassword').value;
        if (a !== b) { alert('两次输入的新密码不一致'); return false; }
        return true;
    }
</script>
</body>
</html>
