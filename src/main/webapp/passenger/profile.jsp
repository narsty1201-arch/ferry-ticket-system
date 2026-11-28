<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.User,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    User p = (User) request.getAttribute("profile");
    if (p == null) p = (User) session.getAttribute("loginUser");
    String msg = (String) request.getAttribute("msg");
    SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd");
    String ctx = request.getContextPath();

    String displayName = p.getRealName();
    if (displayName == null || displayName.trim().isEmpty()) displayName = p.getUsername();
    if (displayName == null || displayName.trim().isEmpty()) displayName = "乘客";
    displayName = displayName.trim();
    String avatarText = displayName.substring(0, 1);

    String roleLabel = "admin".equals(p.getRole()) ? "管理员"
            : "seller".equals(p.getRole()) ? "售票员" : "乘客";

    String phone = p.getPhone() == null ? "" : p.getPhone().trim();
    String maskedPhone = phone.isEmpty() ? "未填写"
            : (phone.length() >= 7 ? phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4) : "****");

    String idCard = p.getIdCard() == null ? "" : p.getIdCard().trim();
    String maskedId = idCard.isEmpty() ? "未填写"
            : (idCard.length() >= 4 ? idCard.substring(0, 4) + "********" : "****");

    String createdDate = p.getCreateTime() == null ? "-" : dayFmt.format(p.getCreateTime());
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
    <div class="phone-head"><span class="ico ico-user"></span>个人中心</div>
    <div class="phone-body">
        <% if (msg != null && !msg.isEmpty()) { %>
        <div class="msg ok"><span class="ico ico-check-circle ico-sm"></span><span><%= msg %></span></div>
        <% } %>

        <section class="profile-hero" aria-label="身份信息">
            <div class="profile-identity">
                <div class="profile-avatar"><%= avatarText %></div>
                <div class="profile-identity-text">
                    <div class="profile-name-line">
                        <h2><%= displayName %></h2>
                        <span class="profile-role"><%= roleLabel %></span>
                    </div>
                    <div class="profile-sub">登录账号：<%= p.getUsername() %></div>
                    <div class="profile-sub">注册时间：<%= createdDate %></div>
                </div>
            </div>
            <div class="profile-quick">
                <a class="profile-quick-item" href="<%= ctx %>/passenger/orders">
                    <span class="profile-quick-icon"><span class="ico ico-orders"></span></span>
                    <span>查看订单</span>
                </a>
                <button type="button" class="profile-quick-item" onclick="openProfileSection('infoFold','infoCard','infoToggle')">
                    <span class="profile-quick-icon"><span class="ico ico-id"></span></span>
                    <span>实名资料</span>
                </button>
                <button type="button" class="profile-quick-item" onclick="openProfileSection('passwordFold','passwordCard','passwordToggle')">
                    <span class="profile-quick-icon"><span class="ico ico-check-circle"></span></span>
                    <span>账户安全</span>
                </button>
            </div>
        </section>

        <section class="profile-info-card">
            <h3>账户信息</h3>
            <div class="profile-info-row">
                <span class="profile-info-icon"><span class="ico ico-phone"></span></span>
                <span class="profile-info-label">手机号</span>
                <span class="profile-info-value"><%= maskedPhone %></span>
            </div>
            <div class="profile-info-row">
                <span class="profile-info-icon"><span class="ico ico-id"></span></span>
                <span class="profile-info-label">身份证</span>
                <span class="profile-info-value"><%= maskedId %></span>
            </div>
            <div class="profile-info-row">
                <span class="profile-info-icon"><span class="ico ico-calendar"></span></span>
                <span class="profile-info-label">注册时间</span>
                <span class="profile-info-value"><%= createdDate %></span>
            </div>
        </section>

        <section class="profile-fold-card" id="infoCard">
            <button type="button" class="profile-fold-head" id="infoToggle"
                    aria-expanded="false" onclick="toggleProfileSection('infoFold','infoCard')">
                <span class="profile-fold-title">实名资料</span>
                <span class="profile-fold-action">修改<span class="ico ico-arrow fold-arrow"></span></span>
            </button>
            <div class="profile-fold" id="infoFold" hidden>
                <form method="post" action="<%= ctx %>/passenger/profile">
                    <input type="hidden" name="action" value="info">
                    <div class="form-line"><label>姓名</label><input name="realName" value="<%= p.getRealName() == null ? "" : p.getRealName() %>" required></div>
                    <div class="form-line"><label>手机号</label><input name="phone" value="<%= p.getPhone() == null ? "" : p.getPhone() %>" maxlength="11" required></div>
                    <div class="form-line"><label>身份证号（选填）</label><input name="idCard" value="<%= p.getIdCard() == null ? "" : p.getIdCard() %>" maxlength="18"></div>
                    <button type="submit" class="btn btn-block">保存资料</button>
                </form>
            </div>
        </section>

        <section class="profile-fold-card" id="passwordCard">
            <button type="button" class="profile-fold-head" id="passwordToggle"
                    aria-expanded="false" onclick="toggleProfileSection('passwordFold','passwordCard')">
                <span class="profile-fold-title">账户安全</span>
                <span class="profile-fold-action">修改密码<span class="ico ico-arrow fold-arrow"></span></span>
            </button>
            <div class="profile-fold" id="passwordFold" hidden>
                <form method="post" action="<%= ctx %>/passenger/profile" onsubmit="return checkPwd()">
                    <input type="hidden" name="action" value="password">
                    <div class="form-line"><label>原密码</label><input type="password" name="oldPassword" required></div>
                    <div class="form-line"><label>新密码（至少6位）</label><input type="password" name="newPassword" id="newPassword" required></div>
                    <div class="form-line"><label>确认新密码</label><input type="password" name="confirmPassword" id="confirmPassword" required></div>
                    <button type="submit" class="btn btn-block">修改密码</button>
                </form>
            </div>
        </section>

        <a class="big-btn danger profile-logout" href="<%= ctx %>/logout">
            退出登录
        </a>
    </div>
    <%@ include file="/WEB-INF/includes/passenger-tabbar.jspf" %>
</div>
<script>
    function toggleProfileSection(foldId, cardId) {
        var fold = document.getElementById(foldId);
        var card = document.getElementById(cardId);
        var head = card.querySelector('.profile-fold-head');
        var willOpen = fold.hidden;
        fold.hidden = !willOpen;
        card.classList.toggle('is-open', willOpen);
        head.setAttribute('aria-expanded', willOpen ? 'true' : 'false');
    }
    function openProfileSection(foldId, cardId, toggleId) {
        var fold = document.getElementById(foldId);
        if (fold.hidden) {
            toggleProfileSection(foldId, cardId);
        }
        var card = document.getElementById(cardId);
        if (card && card.scrollIntoView) {
            card.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
        var toggle = document.getElementById(toggleId);
        if (toggle) toggle.focus({ preventScroll: true });
    }
    function checkPwd(){
        var a = document.getElementById('newPassword').value;
        var b = document.getElementById('confirmPassword').value;
        if (a !== b) { alert('两次输入的新密码不一致'); return false; }
        return true;
    }
</script>
</body>
</html>
