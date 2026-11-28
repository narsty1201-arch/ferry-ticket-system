<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.Map" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    @SuppressWarnings("unchecked")
    Map<String,Object> d = (Map<String,Object>) request.getAttribute("detail");
    if (d == null) d = new java.util.HashMap<String,Object>();
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>检票成功 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body class="desk">
<%@ include file="/WEB-INF/includes/seller-topbar.jspf" %>
<div class="desk-layout">
    <%@ include file="/WEB-INF/includes/seller-nav.jspf" %>
    <main class="desk-main">
        <div class="ticket">
            <div class="top"><span class="ico ico-check-circle"></span><h2>检票成功 · 准予登船</h2></div>
            <div class="body">
                <div class="row"><span>订单号</span><span><%= d.get("order_no") %></span></div>
                <div class="row"><span>航线</span><span><%= d.get("start_port") %> → <%= d.get("end_port") %></span></div>
                <div class="row"><span>班次</span><span><%= d.get("route_name") %> <%= d.get("depart_time") %></span></div>
                <div class="row"><span>乘客</span><span><%= d.get("passenger_name") %></span></div>
                <div class="row"><span>身份证</span><span><%= d.get("id_card") %></span></div>
                <div class="row"><span>座位</span><span><%= d.get("seat_no") %></span></div>
                <div class="row"><span>检票时间</span><span><%= d.get("check_time") %></span></div>
                <div class="row"><span>检票员</span><span><%= d.get("seller_name") %></span></div>
                <div class="act">
                    <a href="${pageContext.request.contextPath}/seller/checkin"><span class="ico ico-check ico-sm"></span>继续检票下一位</a>
                </div>
            </div>
        </div>
    </main>
</div>
</body>
</html>
