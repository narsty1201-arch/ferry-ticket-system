<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Schedule,java.util.List,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    Schedule s = (Schedule) request.getAttribute("sched");
    if (s == null) { response.sendRedirect("schedules.jsp"); return; }
    @SuppressWarnings("unchecked")
    List<String> seats = (List<String>) request.getAttribute("seats");
    if (seats == null) seats = new java.util.ArrayList<String>();
    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>选座购票 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body class="mobile">
<div class="phone">
    <div class="phone-head">🪑 选座 · <%= s.getRouteName() %></div>
    <div class="phone-body">
        <div class="card">
            <div><b><%= s.getStartPort() %> → <%= s.getEndPort() %></b>　<%= s.getShipName() %></div>
            <div class="muted">发船 <%= fmt.format(s.getDepartTime()) %>　票价 ￥<%= s.getPrice() %></div>
        </div>

        <% String msg = (String) request.getAttribute("msg"); if (msg != null && !msg.isEmpty()) { %>
        <p class="msg err"><%= msg %></p><% } %>

        <h3>选择座位（蓝色可售）</h3>
        <div class="seat-grid">
            <% for (String seat : seats) { %>
            <span class="seat" onclick="pick(this,'<%= seat %>')"><%= seat %></span>
            <% } %>
            <% if (seats.isEmpty()) { %><p class="muted">暂无可售座位</p><% } %>
        </div>

        <form method="post" action="${pageContext.request.contextPath}/book" onsubmit="return checkForm()">
            <input type="hidden" name="action" value="order">
            <input type="hidden" name="scheduleId" value="<%= s.getId() %>">
            <input type="hidden" name="seatNo" id="seatNo" value="">

            <div class="form-line"><label>乘客姓名（本人档案）</label><input value="${sessionScope.userName}" readonly></div>
            <div class="form-line"><label>身份证号（本人档案）</label><input value="${sessionScope.userCard}" readonly></div>
            <div class="form-line"><label>手机号（本人档案）</label><input value="${sessionScope.userPhone}" readonly></div>
            <p class="muted">实名购票：船票身份信息取自您的个人档案，不可填写他人信息。如需修改请到"我的 → 个人中心"。<br>提交后座位锁定 <b>5 分钟</b>，请在时限内到"我的订单"支付，超时自动释放。</p>
            <button type="submit" class="btn btn-block">🎫 确认锁座下单</button>
        </form>
    </div>
    <div class="tabbar">
        <a href="${pageContext.request.contextPath}/passenger/home.jsp"><span>🏠 首页</span></a>
        <a href="${pageContext.request.contextPath}/passenger/schedules.jsp"><span class="active">🎫 班次</span></a>
        <a href="${pageContext.request.contextPath}/passenger/orders"><span>📋 订单</span></a>
        <a href="${pageContext.request.contextPath}/passenger/profile"><span>👤 我的</span></a>
    </div>
</div>
<script>
    var picked = null;
    function pick(el, seat){
        if (picked) picked.classList.remove('sel');
        el.classList.add('sel');
        picked = el;
        document.getElementById('seatNo').value = seat;
    }
    function checkForm(){
        if (!document.getElementById('seatNo').value){ alert('请先选择一个座位'); return false; }
        return true;
    }
</script>
</body>
</html>
