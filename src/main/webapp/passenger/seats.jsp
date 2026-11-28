<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Schedule,com.ferry.entity.SeatCell,com.ferry.entity.User,java.util.List,java.util.Map,java.util.LinkedHashMap,java.util.ArrayList,java.text.SimpleDateFormat" %>
<%!
    private static String h(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    User loginUser = (User) session.getAttribute("loginUser");
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
    SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
    String departText = dayFmt.format(s.getDepartTime()).equals(dayFmt.format(new java.util.Date()))
            ? "今天 " + timeFmt.format(s.getDepartTime())
            : fmt.format(s.getDepartTime());
    String ctx = request.getContextPath();
    String preselect = (String) request.getAttribute("seatNo");
    if (preselect == null) preselect = "";
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
    <div class="phone-head"><span class="ico ico-seat"></span>选座 · <%= s.getRouteName() %></div>
    <div class="phone-body seat-page has-action-bar">
        <div class="trip-banner">
            <div class="trip-main">
                <span><%= s.getStartPort() %></span><span class="port-arrow">→</span><span><%= s.getEndPort() %></span>
                <span class="dot">·</span><span class="ship-name"><%= s.getShipName() %></span>
            </div>
            <div class="trip-meta">
                <span><%= departText %></span><span class="dot">·</span><span>票价 <b>￥<%= s.getPrice() %></b></span>
            </div>
        </div>

        <% String msg = (String) request.getAttribute("msg"); if (msg != null && !msg.isEmpty()) { %>
        <p class="msg err"><span class="ico ico-info ico-sm"></span><span><%= msg %></span></p><% } %>

        <div class="seat-head">
            <h3>选择座位</h3>
            <span class="seat-count">余票 <b><%= availableCount %></b>/<%= seatMap.size() %></span>
        </div>

        <% if (seatMap.isEmpty()) { %>
        <div class="seat-empty">
            <img src="<%= ctx %>/images/empty-seats.png" alt="暂无座位" loading="lazy">
            <p>该班次暂未生成座位，请稍后再试。</p>
        </div>
        <% } else { %>
        <div class="cabin">
            <div class="cabin-bow">船头 ▲</div>
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

        <div class="notice-line">
            <span class="ico ico-info ico-sm"></span>
            <span>本人信息取自个人档案，同行人需填写真实实名信息。最多可选 5 个座位，座位锁定后请在 5 分钟内完成支付。</span>
        </div>

        <form id="orderForm" method="post" action="${pageContext.request.contextPath}/book" onsubmit="return checkForm()">
        <input type="hidden" name="action" value="order">
        <input type="hidden" name="scheduleId" value="<%= s.getId() %>">
        <input type="hidden" name="seatNo" id="seatNo" value="">
        <div class="card" id="passengerBox">
            <div class="panel-title">乘客信息 <span class="muted" id="passengerHint">请先选择座位</span></div>
            <div class="passenger-card">
                <div class="passenger-card-title">乘客 1（本人）</div>
                <div class="form-line"><label>姓名</label>
                    <input name="passengerName" value="<%= h(loginUser.getRealName()) %>" readonly></div>
                <div class="form-line"><label>身份证号</label>
                    <input name="idCard" value="<%= h(loginUser.getIdCard()) %>" readonly></div>
                <div class="form-line"><label>手机号</label>
                    <input name="phone" value="<%= h(loginUser.getPhone()) %>" readonly></div>
            </div>
            <% for (int i = 2; i <= 5; i++) { %>
            <div class="passenger-card" hidden>
                <div class="passenger-card-title">乘客 <%= i %>（同行人）</div>
                <div class="form-line"><label>姓名</label>
                    <input name="passengerName" maxlength="20" placeholder="请输入同行人姓名"></div>
                <div class="form-line"><label>身份证号</label>
                    <input name="idCard" maxlength="18" placeholder="请输入 18 位身份证号"></div>
                <div class="form-line"><label>手机号</label>
                    <input name="phone" maxlength="11" placeholder="13800000000"></div>
            </div>
            <% } %>
        </div>
        </form>
    </div>
    <div class="seat-action">
        <div class="seat-action-line" id="seatSummary" aria-live="polite">
            <span class="seat-badge"><span class="ico ico-seat"></span></span>
            <span id="seatSummaryText">尚未选择座位</span>
            <a class="seat-refresh" href="<%= ctx %>/book?action=seats&amp;scheduleId=<%= s.getId() %>"><span class="ico ico-refresh ico-sm"></span>刷新</a>
        </div>
        <button type="submit" form="orderForm" class="btn-primary">确认锁座下单</button>
    </div>
    <%@ include file="/WEB-INF/includes/passenger-tabbar.jspf" %>
</div>
<script>
    var picked = [];
    var price = Number('<%= s.getPrice() %>');
    function pick(el){
        var seat = el.getAttribute('data-seat');
        var index = picked.indexOf(seat);
        if (index >= 0) {
            picked.splice(index, 1);
            el.classList.remove('sel');
        } else {
            if (picked.length >= 5) { alert('一个订单最多选择 5 个座位'); return; }
            picked.push(seat);
            el.classList.add('sel');
        }
        picked.sort();
        document.getElementById('seatNo').value = picked.join(',');
        var summary = document.getElementById('seatSummary');
        var text = document.getElementById('seatSummaryText');
        if (picked.length === 0) {
            text.innerHTML = '尚未选择座位';
            summary.classList.remove('has-seat');
        } else {
            text.innerHTML = '<b>已选 ' + picked.join('、') + '</b> · ￥' +
                (price * picked.length).toFixed(2);
            summary.classList.add('has-seat');
        }
        syncPassengerCards();
    }
    function syncPassengerCards(){
        var cards = document.querySelectorAll('.passenger-card');
        var count = Math.max(picked.length, 1);
        for (var i = 0; i < cards.length; i++) cards[i].hidden = i >= count;
        document.getElementById('passengerHint').textContent =
            picked.length ? '已选 ' + picked.length + ' 人，请完善同行人信息' : '请先选择座位';
    }
    function checkForm(){
        if (!picked.length){ alert('请至少选择一个座位'); return false; }
        var cards = document.querySelectorAll('.passenger-card');
        for (var i = 0; i < picked.length; i++) {
            var name = cards[i].querySelector('input[name="passengerName"]').value.trim();
            var id = cards[i].querySelector('input[name="idCard"]').value.trim();
            var phone = cards[i].querySelector('input[name="phone"]').value.trim();
            if (name.length < 2 || name.length > 20) { alert('第 ' + (i + 1) + ' 位乘客姓名需为 2~20 个字符'); return false; }
            if (!/^\d{17}[0-9Xx]$/.test(id) || !idCardValid(id)) { alert('第 ' + (i + 1) + ' 位乘客身份证号不正确'); return false; }
            if (!/^1\d{10}$/.test(phone)) { alert('第 ' + (i + 1) + ' 位乘客手机号需为 11 位'); return false; }
        }
        return true;
    }
    function idCardValid(v){
        v = (v || '').toUpperCase();
        if (!/^\d{17}[0-9X]$/.test(v)) return false;
        var w = [7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2];
        var c = ['1','0','X','9','8','7','6','5','4','3','2'];
        var sum = 0;
        for (var i = 0; i < 17; i++) sum += parseInt(v.charAt(i), 10) * w[i];
        return c[sum % 11] === v.charAt(17);
    }
    (function(){
        var pre = '<%= preselect %>';
        if (!pre) return;
        var el = document.querySelector('.seat.available[data-seat="' + pre + '"]');
        if (el) pick(el);
    })();
</script>
</body>
</html>
