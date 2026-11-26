<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Schedule,java.util.List,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    Schedule s = (Schedule) request.getAttribute("sched");
    if (s == null) { response.sendRedirect(request.getContextPath() + "/seller/sell"); return; }
    @SuppressWarnings("unchecked")
    List<String> seats = (List<String>) request.getAttribute("seats");
    if (seats == null) seats = new java.util.ArrayList<String>();
    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    String ctx = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>代客选座 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <style>
        .seat { display:inline-block; width:46px; height:40px; line-height:40px; text-align:center;
                border:1px solid #93c5fd; border-radius:6px; margin:4px; cursor:pointer; background:#eff6ff; }
        .seat.sel { background:#2563eb; color:#fff; }
        .form-line { margin:10px 0; font-size:14px; }
        .form-line label { display:block; color:#374151; margin-bottom:4px; }
        .form-line input { width:100%; max-width:420px; padding:10px; border:1px solid #d1d5db; border-radius:8px; font-size:15px; }
    </style>
</head>
<body class="desk">
<div class="desk-head">码头售票系统 · 代客选座　售票员：${sessionScope.userName}
    <a class="logout" href="<%= ctx %>/logout">退出</a>
</div>
<div class="desk-layout">
    <aside class="desk-menu">
        <div onclick="location.href='<%= ctx %>/seller/home.jsp'">工作台</div>
        <div class="active">🎫 售票</div>
        <div onclick="location.href='<%= ctx %>/seller/orders'">📋 销售订单</div>
        <div onclick="location.href='<%= ctx %>/seller/checkin'">▶ 检票</div>
        <div onclick="location.href='<%= ctx %>/seller/checkin?action=records'">检票记录</div>
        <div onclick="location.href='<%= ctx %>/seller/checkin?action=stats'">检票统计</div>
    </aside>
    <main class="desk-main">
        <h2>🪑 代客选座 · <%= s.getRouteName() %></h2>
        <div class="card">
            <div><b><%= s.getStartPort() %> → <%= s.getEndPort() %></b>　<%= s.getShipName() %></div>
            <div class="muted">发船 <%= fmt.format(s.getDepartTime()) %>　票价 ￥<%= s.getPrice() %></div>
        </div>

        <% String msg = (String) request.getAttribute("msg"); if (msg != null && !msg.isEmpty()) { %>
        <p class="msg err"><%= msg %></p><% } %>

        <h3>选择座位（蓝色可售）</h3>
        <div>
            <% for (String seat : seats) { %>
            <span class="seat" onclick="pick(this,'<%= seat %>')"><%= seat %></span>
            <% } %>
            <% if (seats.isEmpty()) { %><p class="muted">暂无可售座位</p><% } %>
        </div>

        <form method="post" action="<%= ctx %>/book" onsubmit="return checkForm()" style="margin-top:18px">
            <input type="hidden" name="action" value="order">
            <input type="hidden" name="scheduleId" value="<%= s.getId() %>">
            <input type="hidden" name="seatNo" id="seatNo" value="">

            <div class="form-line"><label>乘客姓名</label><input name="passengerName" required placeholder="请输入乘船人姓名"></div>
            <div class="form-line"><label>身份证号（18位）</label><input name="idCard" maxlength="18" required placeholder="110101199001010011"></div>
            <div class="form-line"><label>手机号（11位）</label><input name="phone" maxlength="11" required placeholder="13800000000"></div>
            <p class="muted">代客购票须填写真实乘客信息（身份证需 18 位且校验位正确、手机 11 位），系统会在服务端校验。提交后座位锁定 <b>5 分钟</b>，请在时限内到"销售订单"支付，超时自动释放。</p>
            <button type="submit" class="btn-sm btn-add" style="padding:10px 20px;font-size:15px">确认锁座下单</button>
        </form>
    </main>
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
