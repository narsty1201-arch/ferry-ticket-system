package com.ferry.dao;

import com.ferry.util.DBUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class StatDAOTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final StatDAO dao = new StatDAO();

    private String tag;
    private int passengerId;
    private int sellerId;
    private int routeId;
    private int shipId;
    private int scheduleId;
    private int otherSellerId;

    @Before
    public void setUp() throws Exception {
        tag = "TT" + Long.toString(System.currentTimeMillis(), 36)
                + SEQUENCE.incrementAndGet();
        try (Connection conn = DBUtil.getConnection()) {
            passengerId = insertUser(conn, "passenger", tag + "_passenger",
                    String.format("139%08d", SEQUENCE.incrementAndGet() % 100000000L));
            sellerId = insertUser(conn, "seller", tag + "_seller",
                    String.format("138%08d", SEQUENCE.incrementAndGet() % 100000000L));
            routeId = insert(conn, "INSERT INTO route"
                    + "(route_name,start_port,end_port,duration_min,status,is_deleted) "
                    + "OUTPUT INSERTED.id VALUES (?,?,?,?, 'active',0)",
                    "统计航线-" + tag, "统计起点-" + tag, "统计终点-" + tag, 120);
            shipId = insert(conn, "INSERT INTO ship"
                    + "(ship_name,total_seats,ship_type,status,is_deleted) "
                    + "OUTPUT INSERTED.id VALUES (?,30,N'普通船','active',0)",
                    "统计船舶-" + tag);
            scheduleId = insert(conn, "INSERT INTO schedule"
                    + "(route_id,ship_id,depart_time,arrive_time,price,total_seats,"
                    + "sold_seats,status,is_deleted) "
                    + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,30,2,N'售票中',0)",
                    routeId, shipId, hoursFromNow(48), hoursFromNow(50),
                    new BigDecimal("100.00"));
        }
    }

    @After
    public void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            if (scheduleId > 0) {
                execute(conn, "DELETE FROM payment_log WHERE order_id IN "
                        + "(SELECT id FROM [order] WHERE schedule_id=?)", scheduleId);
                execute(conn, "DELETE FROM check_in WHERE ticket_id IN "
                        + "(SELECT id FROM ticket WHERE schedule_id=?)", scheduleId);
                execute(conn, "DELETE FROM ticket WHERE schedule_id=?", scheduleId);
                execute(conn, "DELETE FROM [order] WHERE schedule_id=?", scheduleId);
                execute(conn, "DELETE FROM seat WHERE schedule_id=?", scheduleId);
                execute(conn, "DELETE FROM schedule WHERE id=?", scheduleId);
            }
            if (shipId > 0) execute(conn, "DELETE FROM ship WHERE id=?", shipId);
            if (routeId > 0) execute(conn, "DELETE FROM route WHERE id=?", routeId);
            if (otherSellerId > 0) execute(conn, "DELETE FROM [user] WHERE id=?", otherSellerId);
            if (passengerId > 0 || sellerId > 0) {
                execute(conn, "DELETE FROM [user] WHERE id IN (?,?)", passengerId, sellerId);
            }
        }
    }

    @Test
    public void reportsUseNetIncomeAndDoNotDuplicateSoldSeats() throws Exception {
        BigDecimal incomeBefore = money(dao.overview().get("total_income"));

        insertOrder("paid", new BigDecimal("100.00"), "已支付", BigDecimal.ZERO, null);
        insertOrder("refunded", new BigDecimal("160.00"), "已退票", new BigDecimal("130.00"),
                "统计测试");
        insertOrder("pending", new BigDecimal("999.00"), "待支付", BigDecimal.ZERO, null);
        insertOrder("cancelled", new BigDecimal("777.00"), "已取消", BigDecimal.ZERO, null);

        BigDecimal incomeAfter = money(dao.overview().get("total_income"));
        assertEquals("总净收入应只计入实收与未退部分",
                new BigDecimal("130.00"), incomeAfter.subtract(incomeBefore));

        Map<String, Object> route = routeRow(null, null);
        assertNotNull(route);
        assertEquals("待支付/取消不应计入成交订单", "2", route.get("order_count"));
        assertEquals("0", route.get("checked_orders"));
        assertEquals(0, new BigDecimal("130.00").compareTo(money(route.get("income"))));
        assertEquals("1", route.get("sold"));

        Map<String, Object> seller = sellerRow(null, null);
        assertNotNull(seller);
        assertEquals("2", seller.get("order_count"));
        assertEquals("1", seller.get("ticket_count"));
        assertEquals(0, new BigDecimal("130.00").compareTo(money(seller.get("total_amount"))));
        assertEquals(0, new BigDecimal("65.00").compareTo(money(seller.get("avg_amount"))));
    }

    @Test
    public void dateRangeAppliesToOrdersAndRefundReasonsSeparately() throws Exception {
        Timestamp rangeStart = hoursFromNow(-48);
        Timestamp rangeEnd = hoursFromNow(24);

        int paidId = insertOrder("recent-paid", new BigDecimal("100.00"), "已支付",
                BigDecimal.ZERO, null);
        updateOrderTime(paidId, hoursFromNow(-2), null);

        int oldPaidId = insertOrder("old-paid", new BigDecimal("200.00"), "已支付",
                BigDecimal.ZERO, null);
        updateOrderTime(oldPaidId, hoursFromNow(-120), null);

        int refundedId = insertOrder("recent-refunded", new BigDecimal("150.00"), "已退票",
                new BigDecimal("120.00"), "天气原因");
        updateOrderTime(refundedId, hoursFromNow(-24), hoursFromNow(-2));

        int oldRefundedId = insertOrder("old-refunded", new BigDecimal("180.00"), "已退票",
                new BigDecimal("150.00"), "个人原因");
        updateOrderTime(oldRefundedId, hoursFromNow(-240), hoursFromNow(-120));

        Map<String, Object> recent = dao.overview(rangeStart, rangeEnd, routeId);
        assertEquals("2", recent.get("total_orders"));
        assertEquals(0, new BigDecimal("130.00").compareTo(money(recent.get("total_income"))));
        assertEquals("1", recent.get("sold_seats"));
        assertEquals("1", recent.get("refunded_orders"));
        assertEquals(0, new BigDecimal("120.00").compareTo(money(recent.get("refund_amount"))));

        Map<String, Object> route = routeRow(rangeStart, rangeEnd);
        assertNotNull(route);
        assertEquals("2", route.get("order_count"));
        assertEquals("1", route.get("sold"));

        List<Map<String, Object>> reasons = dao.refundReasons(rangeStart, rangeEnd, routeId);
        assertEquals(1, reasons.size());
        assertEquals("天气原因", reasons.get(0).get("reason"));
        assertEquals("1", reasons.get(0).get("refund_count"));
        assertEquals(0, new BigDecimal("120.00")
                .compareTo(money(reasons.get(0).get("refund_amount"))));

        Map<String, Object> seller = sellerRow(rangeStart, rangeEnd);
        assertNotNull(seller);
        assertEquals("2", seller.get("order_count"));
        assertEquals("1", seller.get("refund_count"));
    }

    @Test
    public void completedOrdersCountAsCheckedInOverviewAndStatusReport() throws Exception {
        insertOrder("completed", new BigDecimal("80.00"), "已完成", BigDecimal.ZERO, null);

        Map<String, Object> ov = dao.overview(hoursFromNow(-24), hoursFromNow(24), routeId);
        assertEquals("1", ov.get("total_orders"));
        assertEquals("1", ov.get("checked_orders"));
        assertEquals("1", ov.get("completed_orders"));
        assertEquals("1", ov.get("sold_seats"));

        List<Map<String, Object>> rows = dao.orderStatus(hoursFromNow(-24), hoursFromNow(24), routeId);
        assertEquals(1, rows.size());
        assertEquals("1", rows.get(0).get("total"));
        assertEquals("1", rows.get(0).get("completed"));
    }

    @Test
    public void sellerSummaryIsScopedToTheLoggedInSeller() throws Exception {
        insertOrder("seller-a", new BigDecimal("100.00"), "已支付", BigDecimal.ZERO, null);
        insertOrder("seller-b", new BigDecimal("160.00"), "已退票", new BigDecimal("130.00"),
                "行程变更");

        try (Connection conn = DBUtil.getConnection()) {
            otherSellerId = insertUser(conn, "seller", tag + "_other",
                    String.format("137%08d", SEQUENCE.incrementAndGet() % 100000000L));
            int seatId = insert(conn, "INSERT INTO seat(schedule_id,seat_no,seat_status) "
                    + "OUTPUT INSERTED.id VALUES (?,N'T-OTHER',N'已售')", scheduleId);
            insert(conn, "INSERT INTO [order]"
                    + "(order_no,schedule_id,passenger_id,seller_id,total_amount,status) "
                    + "OUTPUT INSERTED.id VALUES (?,?,?,?,888.00,N'已支付')",
                    tag + "-other-order", scheduleId, passengerId, otherSellerId);
            execute(conn, "UPDATE [order] SET total_amount=888.00 WHERE seller_id=?",
                    otherSellerId);
            // 该辅助订单只验证作用域；清理时随班次删除，船票不参与当前统计。
            execute(conn, "DELETE FROM seat WHERE id=?", seatId);
        }

        Map<String, Object> summary = dao.sellerSummary(sellerId, null, null);
        assertEquals("2", summary.get("order_count"));
        assertEquals("1", summary.get("ticket_count"));
        assertEquals(0, new BigDecimal("130.00")
                .compareTo(money(summary.get("total_amount"))));
    }

    @Test
    public void scheduleAlertChangesWithDepartureTimeAndOccupancy() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            execute(conn, "UPDATE schedule SET depart_time=?, arrive_time=? WHERE id=?",
                    hoursFromNow(20), hoursFromNow(22), scheduleId);
        }
        Map<String, Object> row = scheduleRow();
        assertNotNull(row);
        assertEquals("紧急", row.get("alert_level"));

        try (Connection conn = DBUtil.getConnection()) {
            execute(conn, "UPDATE schedule SET depart_time=?, arrive_time=? WHERE id=?",
                    hoursFromNow(-1), hoursFromNow(1), scheduleId);
        }
        row = scheduleRow();
        assertNotNull(row);
        assertEquals("已结束", row.get("alert_level"));
    }

    @Test
    public void historicalRevenueSurvivesRouteSoftDelete() throws Exception {
        insertOrder("deleted-route", new BigDecimal("100.00"), "已支付",
                BigDecimal.ZERO, null);
        try (Connection conn = DBUtil.getConnection()) {
            execute(conn, "UPDATE route SET is_deleted=1 WHERE id=?", routeId);
        }

        Map<String, Object> overview = dao.overview(null, null, routeId);
        assertEquals("1", overview.get("total_orders"));
        assertEquals(0, new BigDecimal("100.00")
                .compareTo(money(overview.get("total_income"))));

        Map<String, Object> route = routeRow(null, null);
        assertNotNull("软删除航线的历史报表不能被隐藏", route);
        assertEquals(0, new BigDecimal("100.00").compareTo(money(route.get("income"))));
    }

    private Map<String, Object> routeRow(Timestamp start, Timestamp end) {
        List<Map<String, Object>> rows = dao.byRoute(start, end, routeId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> sellerRow(Timestamp start, Timestamp end) {
        List<Map<String, Object>> rows = dao.sellerPerformance(start, end, routeId);
        for (Map<String, Object> row : rows) {
            if ((tag + "_seller").equals(row.get("username"))) {
                return row;
            }
        }
        return null;
    }

    private Map<String, Object> scheduleRow() {
        List<Map<String, Object>> rows = dao.bySchedule(null, null, routeId);
        for (Map<String, Object> row : rows) {
            if (String.valueOf(scheduleId).equals(row.get("schedule_id"))) return row;
        }
        return null;
    }

    private int insertOrder(String suffix, BigDecimal amount, String status,
            BigDecimal refundAmount, String refundReason) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            int orderId = insert(conn, "INSERT INTO [order]"
                    + "(order_no,schedule_id,passenger_id,seller_id,total_amount,status,"
                    + "refund_amount,refund_reason,refund_time) OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,"
                    + "CASE WHEN ?=N'已退票' THEN ? ELSE NULL END,"
                    + "CASE WHEN ?=N'已退票' THEN ? ELSE NULL END,"
                    + "CASE WHEN ?=N'已退票' THEN GETDATE() ELSE NULL END)",
                    tag + "-" + suffix, scheduleId, passengerId, sellerId, amount, status,
                    status, refundAmount, status, refundReason, status);
            int seatId = insert(conn, "INSERT INTO seat(schedule_id,seat_no,seat_status) "
                    + "OUTPUT INSERTED.id VALUES (?,?,N'已售')",
                    scheduleId, "T" + SEQUENCE.incrementAndGet());
            String ticketStatus = "已完成".equals(status) ? "已检票" : status;
            insert(conn, "INSERT INTO ticket"
                    + "(order_id,schedule_id,seat_id,passenger_name,id_card,passenger_phone,"
                    + "price,status,qr_code) OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?)",
                    orderId, scheduleId, seatId, "统计乘客" + suffix,
                    String.format("11010119900101%04d", SEQUENCE.incrementAndGet() % 10000),
                    String.format("136%08d", SEQUENCE.incrementAndGet() % 100000000L),
                    amount, ticketStatus, "QR-" + tag + "-" + suffix);
            return orderId;
        }
    }

    private void updateOrderTime(int orderId, Timestamp createTime,
            Timestamp refundTime) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            if (refundTime == null) {
                execute(conn, "UPDATE [order] SET create_time=? WHERE id=?", createTime, orderId);
            } else {
                execute(conn, "UPDATE [order] SET create_time=?, refund_time=? WHERE id=?",
                        createTime, refundTime, orderId);
            }
        }
    }

    private int insertUser(Connection conn, String role, String username, String phone)
            throws SQLException {
        return insert(conn, "INSERT INTO [user]"
                + "(username,password,role,real_name,phone,status,fail_count) "
                + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,'normal',0)",
                username, "test-password", role, "测试" + role, phone);
    }

    private int insert(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private void execute(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        }
    }

    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            if (value instanceof Integer) {
                ps.setInt(i + 1, (Integer) value);
            } else if (value instanceof BigDecimal) {
                ps.setBigDecimal(i + 1, (BigDecimal) value);
            } else if (value instanceof Timestamp) {
                ps.setTimestamp(i + 1, (Timestamp) value);
            } else if (value == null) {
                ps.setNull(i + 1, java.sql.Types.VARCHAR);
            } else {
                ps.setString(i + 1, String.valueOf(value));
            }
        }
    }

    private BigDecimal money(Object value) {
        assertNotNull(value);
        return new BigDecimal(String.valueOf(value));
    }

    private Timestamp hoursFromNow(int hours) {
        return new Timestamp(System.currentTimeMillis() + hours * 3600000L);
    }
}
