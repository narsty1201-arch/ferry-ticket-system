<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>乘客端 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body class="mobile">
<div class="phone">
    <div class="phone-head"><span class="ico ico-ferry"></span>轮渡码头 · 乘客端</div>
    <div class="phone-body">
        <section class="home-hero">
            <h2>你好，${sessionScope.userName}</h2>
            <p>查询班次、选择座位，轻松开启水上行程。</p>
            <img src="${pageContext.request.contextPath}/images/ferry-header.png" alt="">
        </section>
        <a href="${pageContext.request.contextPath}/passenger/schedules"><div class="big-btn"><span class="ico ico-search"></span>查询班次 · 购票</div></a>
        <div class="quick-grid">
            <a class="quick-tile" href="${pageContext.request.contextPath}/passenger/schedules">
                <span class="ico ico-ferry"></span>
                <b>选择班次</b>
                <span>查看今日可售航线</span>
            </a>
            <a class="quick-tile" href="${pageContext.request.contextPath}/passenger/orders">
                <span class="ico ico-orders"></span>
                <b>我的订单</b>
                <span>支付、退票与改签</span>
            </a>
            <a class="quick-tile" href="${pageContext.request.contextPath}/passenger/profile">
                <span class="ico ico-user"></span>
                <b>个人中心</b>
                <span>核对实名购票资料</span>
            </a>
            <a class="quick-tile" href="${pageContext.request.contextPath}/passenger/tickets">
                <span class="ico ico-ticket"></span>
                <b>我的船票</b>
                <span>查看座位与检票票码</span>
            </a>
        </div>
    </div>
    <%@ include file="/WEB-INF/includes/passenger-tabbar.jspf" %>
</div>
</body>
</html>
