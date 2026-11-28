<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.util.Page,java.util.List,java.util.Map" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    Page<Map<String, Object>> pageData = (Page<Map<String, Object>>) request.getAttribute("pageData");
    if (pageData == null) pageData = Page.empty(1, 15);
    List<Map<String,Object>> records = pageData.getItems();
    String pageUrl = (String) request.getAttribute("pageUrl");
    if (pageUrl == null) pageUrl = "checkin?action=records&page=";
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>检票记录 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<%
    boolean isAdmin = "admin".equals(session.getAttribute("role"));
    String ctx = request.getContextPath();
%>
<body class="desk">
<%@ include file="/WEB-INF/includes/staff-topbar.jspf" %>
<div class="desk-layout">
    <%@ include file="/WEB-INF/includes/staff-nav.jspf" %>
    <main class="desk-main">
        <div class="page-kicker"><span class="ico ico-clipboard ico-sm"></span>检票管理</div>
        <h2>检票记录 <span class="muted" style="font-size:14px">共 <%= pageData.getTotal() %> 条</span></h2>
        <table class="data-table">
            <tr><th>序号</th><th>订单号</th><th>乘客</th><th>身份证</th><th>座位</th><th>航线</th><th>发船</th><th>检票时间</th><th>检票员</th><th>状态</th></tr>
            <% for (int i = 0; i < records.size(); i++) { Map<String,Object> r = records.get(i); %>
            <tr>
                <td><%= i+1 %></td>
                <td><%= r.get("order_no") %></td>
                <td><%= r.get("passenger_name") %></td>
                <td><%= r.get("id_card") %></td>
                <td><%= r.get("seat_no") %></td>
                <td><%= r.get("route_name") %></td>
                <td><%= r.get("depart_time") %></td>
                <td><%= r.get("check_time") %></td>
                <td><%= r.get("seller_name") %></td>
                <td><%= r.get("status") %></td>
            </tr>
            <% } %>
            <% if (records.isEmpty()) { %><tr><td colspan="10" style="text-align:center;color:#9ca3af">暂无检票记录</td></tr><% } %>
        </table>
        <%@ include file="/WEB-INF/pagination.jspf" %>
    </main>
</div>
</body>
</html>
