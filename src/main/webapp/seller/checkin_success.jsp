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
    <title>检票成功 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        .ticket { max-width:520px; margin:26px auto; background:#fff; border-radius:14px; overflow:hidden; box-shadow:0 2px 14px rgba(0,0,0,.08); }
        .ticket .top { background:#16a34a; color:#fff; padding:20px; text-align:center; }
        .ticket .top h2 { margin:0; font-size:22px; }
        .ticket .body { padding:22px; }
        .ticket .row { display:flex; justify-content:space-between; padding:9px 0; border-bottom:1px dashed #e5e7eb; font-size:14px; }
        .ticket .row span:first-child { color:#6b7280; }
        .ticket .row span:last-child { font-weight:bold; color:#1f2937; }
        .ticket .act { margin-top:18px; text-align:center; }
        .ticket .act a { display:inline-block; padding:10px 22px; background:#2563eb; color:#fff; border-radius:8px; text-decoration:none; }
    </style>
</head>
<body class="desk">
<div class="desk-head">码头售票系统 · 检票模式　售票员：${sessionScope.userName}
    <a class="logout" href="${pageContext.request.contextPath}/logout">退出</a>
</div>
<div class="desk-layout">
    <aside class="desk-menu">
        <div onclick="location.href='${pageContext.request.contextPath}/seller/checkin'">▶ 继续检票</div>
        <div onclick="location.href='${pageContext.request.contextPath}/seller/checkin?action=records'">检票记录</div>
    </aside>
    <main class="desk-main">
        <div class="ticket">
            <div class="top"><h2>✅ 检票成功 · 准予登船</h2></div>
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
                    <a href="${pageContext.request.contextPath}/seller/checkin">继续检票下一位</a>
                </div>
            </div>
        </div>
    </main>
</div>
</body>
</html>
