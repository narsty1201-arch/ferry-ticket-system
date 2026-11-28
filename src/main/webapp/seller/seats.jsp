<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ferry.entity.Schedule,com.ferry.entity.SeatCell,java.util.List,java.util.Map,java.util.LinkedHashMap,java.util.ArrayList,java.text.SimpleDateFormat" %>
<%
    if (session.getAttribute("loginUser") == null) { response.sendRedirect(request.getContextPath() + "/login.jsp"); return; }
    Schedule s = (Schedule) request.getAttribute("sched");
    if (s == null) { response.sendRedirect(request.getContextPath() + "/seller/sell"); return; }
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
    String msg = (String) request.getAttribute("msg");
    String preselect = (String) request.getAttribute("seatNo");
    if (preselect == null) preselect = "";
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>代客选座 - 轮渡码头</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body class="desk">
<%@ include file="/WEB-INF/includes/seller-topbar.jspf" %>
<div class="desk-layout">
    <%@ include file="/WEB-INF/includes/seller-nav.jspf" %>
    <main class="desk-main">
        <div class="page-kicker"><span class="ico ico-sale ico-sm"></span>代客售票</div>
        <h2>代客选座 · <%= s.getRouteName() %></h2>
        <div class="seller-seat-layout">
            <section class="seller-seat-map">
                <div class="trip-banner">
                    <div class="trip-main">
                        <span><%= s.getStartPort() %></span><span class="port-arrow">→</span><span><%= s.getEndPort() %></span>
                        <span class="dot">·</span><span class="ship-name"><%= s.getShipName() %></span>
                    </div>
                    <div class="trip-meta">
                        <span><%= departText %></span><span class="dot">·</span><span>票价 <b>￥<%= s.getPrice() %></b></span>
                    </div>
                </div>

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
                                    onclick="pick(this)"><%= cell.getSeatNo() %></button>
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
            </section>

            <aside class="seller-seat-form">
                <div class="card">
                    <div class="panel-title">乘客信息</div>
                    <div class="seat-summary" id="seatSummary" aria-live="polite">
                        <span id="seatSummaryText">尚未选择座位</span>
                        <a class="seat-refresh" href="<%= ctx %>/book?action=seats&amp;scheduleId=<%= s.getId() %>"><span class="ico ico-refresh ico-sm"></span>刷新余票</a>
                    </div>

                    <% if (msg != null && !msg.isEmpty()) { %>
                    <p class="msg err"><span class="ico ico-info ico-sm"></span><span><%= msg %></span></p>
                    <% } %>

                    <form method="post" action="<%= ctx %>/book" onsubmit="return checkForm()">
                        <input type="hidden" name="action" value="order">
                        <input type="hidden" name="scheduleId" value="<%= s.getId() %>">
                        <input type="hidden" name="seatNo" id="seatNo" value="">

                        <% for (int i = 1; i <= 5; i++) { %>
                        <div class="passenger-card" <%= i > 1 ? "hidden" : "" %>>
                            <div class="passenger-card-title">乘客 <%= i %> <span class="muted">（对应一个座位）</span></div>
                            <div class="form-line"><label>姓名</label><input name="passengerName" maxlength="20" placeholder="请输入乘船人姓名"></div>
                            <div class="form-line"><label>身份证号（18位）</label><input name="idCard" maxlength="18" placeholder="请输入 18 位身份证号"></div>
                            <div class="form-line"><label>手机号（11位）</label><input name="phone" maxlength="11" placeholder="13800000000"></div>
                        </div>
                        <% } %>
                        <p class="muted">代客购票最多 5 人，每位乘客对应一个座位；须填写真实信息（身份证需 18 位且校验位正确、手机 11 位），系统会在服务端校验。提交后座位锁定 <b>5 分钟</b>，请在时限内到“销售订单”支付，超时自动释放。</p>
                        <p class="msg err seat-form-error" id="formError" hidden><span class="ico ico-info ico-sm"></span><span id="formErrorText"></span></p>
                        <button type="submit" class="btn-primary btn-block"><span class="ico ico-seat"></span>确认锁座下单</button>
                    </form>
                </div>
            </aside>
        </div>
    </main>
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
    function showFormError(text){
        var box = document.getElementById('formError');
        document.getElementById('formErrorText').textContent = text;
        box.hidden = false;
        box.scrollIntoView({ block: 'nearest' });
    }
    function checkForm(){
        document.getElementById('formError').hidden = true;
        if (!picked.length){ alert('请至少选择一个座位'); return false; }
        var cards = document.querySelectorAll('.passenger-card');
        for (var i = 0; i < picked.length; i++) {
            var name = cards[i].querySelector('input[name="passengerName"]').value.trim();
            var id = cards[i].querySelector('input[name="idCard"]').value.trim();
            var phone = cards[i].querySelector('input[name="phone"]').value.trim();
            if (name.length < 2 || name.length > 20){ showFormError('第 ' + (i + 1) + ' 位乘客姓名需为 2~20 个字符'); return false; }
            if (!idCardValid(id)){ showFormError('第 ' + (i + 1) + ' 位乘客身份证号需为 18 位且校验位正确'); return false; }
            if (!/^1\d{10}$/.test(phone)){ showFormError('第 ' + (i + 1) + ' 位乘客手机号需为 11 位且以 1 开头'); return false; }
        }
        return true;
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
