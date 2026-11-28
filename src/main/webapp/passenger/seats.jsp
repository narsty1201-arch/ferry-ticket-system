<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Schedule,com.ferry.entity.SeatCell,java.util.List,java.util.Map,java.util.LinkedHashMap,java.util.ArrayList,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    Schedule s = (Schedule) request.getAttribute("sched");
    if (s == null) { response.sendRedirect(request.getContextPath() + "/passenger/schedules"); return; }
    @SuppressWarnings("unchecked")
    List<SeatCell> seatMap = (List<SeatCell>) request.getAttribute("seatMap");
    if (seatMap == null) seatMap = new ArrayList<SeatCell>();
    Integer availableAttr = (Integer) request.getAttribute("availableCount");
    int availableCount = availableAttr == null ? 0 : availableAttr;
    LinkedHashMap<String, List<SeatCell>> rows = new LinkedHashMap<String, List<SeatCell>>();
    for (SeatCell cell : seatMap) {
        if (availableAttr == null && cell.isAvailable()) availableCount++;
        List<SeatCell> row = rows.get(cell.getRow());
        if (row == null) { row = new ArrayList<SeatCell>(); rows.put(cell.getRow(), row); }
        row.add(cell);
    }
    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    String ctx = request.getContextPath();
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
        <div class="seat-hero">
            <img src="<%= ctx %>/images/ferry-header.png" alt="轮渡码头插画">
        </div>

        <div class="card">
            <div><b><%= s.getStartPort() %> → <%= s.getEndPort() %></b>　<%= s.getShipName() %></div>
            <div class="muted">发船 <%= fmt.format(s.getDepartTime()) %>　票价 ￥<%= s.getPrice() %></div>
        </div>

        <% String msg = (String) request.getAttribute("msg"); if (msg != null && !msg.isEmpty()) { %>
        <p class="msg err"><%= msg %></p><% } %>

        <div class="seat-head">
            <h3>选择座位</h3>
            <span class="seat-count">余票 <b><%= availableCount %></b> / <%= seatMap.size() %></span>
        </div>

        <% if (seatMap.isEmpty()) { %>
        <div class="seat-empty">
            <img src="<%= ctx %>/images/empty-seats.png" alt="暂无座位" loading="lazy">
            <p>该班次暂未生成座位，请稍后再试。</p>
        </div>
        <% } else { %>
        <div class="cabin">
            <div class="cabin-bow">▲ 船头</div>
            <div class="cabin-body">
                <% for (Map.Entry<String, List<SeatCell>> entry : rows.entrySet()) {
                       List<SeatCell> row = entry.getValue(); %>
                <div class="seat-row">
                    <span class="row-label"><%= entry.getKey() %></span>
                    <% for (int i = 0; i < row.size(); i++) {
                           if (i == 5) { %><span class="seat-aisle" aria-hidden="true"></span><% }
                           SeatCell cell = row.get(i);
                           String state = cell.isAvailable() ? "available"
                                   : ("已售".equals(cell.getStatus()) ? "sold" : "locked"); %>
                    <button type="button" class="seat <%= state %>" data-seat="<%= cell.getSeatNo() %>"
                            aria-label="<%= cell.getSeatNo() %> <%= cell.getStatus() %>"
                            <%= cell.isAvailable() ? "" : "disabled" %>
                            onclick="pick(this)"><%= cell.getNumber() %></button>
                    <% } %>
                </div>
                <% } %>
            </div>
        </div>
        <div class="seat-legend">
            <span><i class="lg-available"></i>可售</span>
            <span><i class="lg-selected"></i>已选</span>
            <span><i class="lg-sold"></i>已售</span>
            <span><i class="lg-locked"></i>锁定</span>
        </div>
        <% } %>

        <div class="seat-summary" id="seatSummary" aria-live="polite">
            <span id="seatSummaryText">尚未选择座位</span>
            <a class="seat-refresh" href="<%= ctx %>/book?action=seats&amp;scheduleId=<%= s.getId() %>">刷新余票</a>
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
        <a href="${pageContext.request.contextPath}/passenger/home"><span>🏠 首页</span></a>
        <a href="${pageContext.request.contextPath}/passenger/schedules"><span class="active">🎫 班次</span></a>
        <a href="${pageContext.request.contextPath}/passenger/orders"><span>📋 订单</span></a>
        <a href="${pageContext.request.contextPath}/passenger/profile"><span>👤 我的</span></a>
    </div>
</div>
<script>
    var picked = null;
    var price = '<%= s.getPrice() %>';
    function pick(el){
        if (picked) picked.classList.remove('sel');
        el.classList.add('sel');
        picked = el;
        var seat = el.getAttribute('data-seat');
        document.getElementById('seatNo').value = seat;
        document.getElementById('seatSummaryText').textContent = '已选 ' + seat + ' · ￥' + price;
        document.getElementById('seatSummary').classList.add('has-seat');
    }
    function checkForm(){
        if (!document.getElementById('seatNo').value){ alert('请先选择一个座位'); return false; }
        return true;
    }
</script>
</body>
</html>
