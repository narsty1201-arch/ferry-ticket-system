package com.ferry.dao;

import com.ferry.entity.Order;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OrderPaymentAndCancelTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final BigDecimal DEFAULT_AMOUNT = new BigDecimal("100.00");

    private final OrderDAO orderDAO = new OrderDAO();
    private final ScheduleDAO scheduleDAO = new ScheduleDAO();

    private String tag;
    private String idCard;
    private String phone;
    private int passengerId;
    private int sellerId;
    private int routeId;
    private int shipId;
    private int scheduleId;
    private int seatId;
    private int orderId;

    @Before
    public void setUp() throws Exception {
        long sequence = SEQUENCE.incrementAndGet();
        long seed = System.currentTimeMillis();
        tag = "TP" + Long.toString(seed, 36) + sequence;
        idCard = idCard(sequence);
        phone = String.format("137%08d", sequence % 100000000L);

        try (Connection conn = DBUtil.getConnection()) {
            passengerId = insertUser(conn, "passenger", tag + "_passenger", phone, idCard);
            sellerId = insertUser(conn, "seller", tag + "_seller", nextPhone(), nextIdCard());
            routeId = insertRoute(conn);
            shipId = insertShip(conn);
            scheduleId = insertSchedule(conn, 10, 0);
            seatId = insertSeat(conn, "A1", "锁定");
            orderId = insertOrder(conn, "A1-order", DEFAULT_AMOUNT, "待支付");
            insertTicket(conn, orderId, seatId, idCard, DEFAULT_AMOUNT, "待支付");
        }
    }

    @After
    public void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            if (scheduleId > 0) {
                execute(conn, "DELETE FROM payment_log WHERE order_id IN "
                        + "(SELECT id FROM [order] WHERE schedule_id=?)", scheduleId);
                execute(conn, "DELETE ci FROM check_in ci JOIN ticket t ON t.id=ci.ticket_id "
                        + "WHERE t.schedule_id=?", scheduleId);
                execute(conn, "DELETE FROM change_log WHERE order_id IN "
                        + "(SELECT id FROM [order] WHERE schedule_id=?)", scheduleId);
                execute(conn, "DELETE FROM ticket WHERE schedule_id=?", scheduleId);
                execute(conn, "DELETE FROM [order] WHERE schedule_id=?", scheduleId);
                execute(conn, "DELETE FROM seat WHERE schedule_id=?", scheduleId);
                execute(conn, "DELETE FROM schedule WHERE id=?", scheduleId);
            }
            if (shipId > 0) execute(conn, "DELETE FROM ship WHERE id=?", shipId);
            if (routeId > 0) execute(conn, "DELETE FROM route WHERE id=?", routeId);
            if (passengerId > 0 || sellerId > 0) {
                execute(conn, "DELETE FROM [user] WHERE id IN (?,?)", passengerId, sellerId);
            }
        }
    }

    @Test
    public void payMakesOrderTicketSeatAndRevenueConsistent() throws Exception {
        assertNull(orderDAO.pay(orderId));

        assertEquals("已支付", orderStatus(orderId));
        assertEquals("已支付", ticketStatus(orderId));
        assertEquals("已售", seatStatus(seatId));
        assertEquals(1, soldSeats(scheduleId));
        assertEquals(1, paymentLogCount(orderId, "支付"));
        assertMoney(DEFAULT_AMOUNT, paymentAmount(orderId, "支付"));
    }

    @Test
    public void departedScheduleCannotBePaid() throws Exception {
        update("UPDATE schedule SET status=N'已发船' WHERE id=?", scheduleId);

        String error = orderDAO.pay(orderId);

        assertNotNull(error);
        assertTrue(error, error.contains("发船"));
        assertUnpaidStateUnchanged(0);
    }

    @Test
    public void releasedSeatCannotBePaid() throws Exception {
        update("UPDATE seat SET seat_status=N'可售', lock_time=NULL WHERE id=?", seatId);

        String error = orderDAO.pay(orderId);

        assertNotNull(error);
        assertTrue(error, error.contains("座位已释放"));
        assertEquals("待支付", orderStatus(orderId));
        assertEquals("待支付", ticketStatus(orderId));
        assertEquals(0, soldSeats(scheduleId));
        assertEquals(0, paymentLogCount(orderId, "支付"));
    }

    @Test
    public void soldOutScheduleCannotBePaid() throws Exception {
        update("UPDATE schedule SET total_seats=1, sold_seats=1 WHERE id=?", scheduleId);

        String error = orderDAO.pay(orderId);

        assertNotNull(error);
        assertTrue(error, error.contains("余票不足"));
        assertUnpaidStateUnchanged(1);
    }

    @Test
    public void repeatedPayDoesNotCreateDuplicatePayment() throws Exception {
        assertNull(orderDAO.pay(orderId));

        String error = orderDAO.pay(orderId);

        assertNotNull(error);
        assertTrue(error, error.contains("不能支付"));
        assertEquals(1, soldSeats(scheduleId));
        assertEquals(1, paymentLogCount(orderId, "支付"));
    }

    @Test
    public void cancelPendingOrderReleasesSeatAndMarksCanceled() throws Exception {
        assertNull(orderDAO.cancelPending(orderId));

        assertEquals("已取消", orderStatus(orderId));
        assertEquals("已取消", ticketStatus(orderId));
        assertEquals("可售", seatStatus(seatId));
        assertEquals(0, soldSeats(scheduleId));
        assertEquals(0, paymentLogCount(orderId, "支付"));
    }

    @Test
    public void cancelPaidOrderIsRejected() throws Exception {
        assertNull(orderDAO.pay(orderId));

        String error = orderDAO.cancelPending(orderId);

        assertNotNull(error);
        assertTrue(error, error.contains("不能取消"));
        assertEquals("已支付", orderStatus(orderId));
        assertEquals("已支付", ticketStatus(orderId));
        assertEquals("已售", seatStatus(seatId));
    }

    @Test
    public void payAfterLockTimeoutIsRejected() throws Exception {
        update("UPDATE seat SET lock_time=DATEADD(MINUTE,-6,GETDATE()) WHERE id=?", seatId);

        String error = orderDAO.pay(orderId);

        assertNotNull(error);
        assertTrue(error, error.contains("超时"));
        assertUnpaidStateUnchanged(0);
    }

    @Test
    public void pendingOrderExposesLockRemainSeconds() throws Exception {
        List<Order> orders = orderDAO.findByPassenger(passengerId);
        Order target = null;
        for (Order order : orders) {
            if (order.getId() == orderId) target = order;
        }

        assertNotNull(target);
        assertTrue("remaining=" + target.getLockRemainSeconds(),
                target.getLockRemainSeconds() > 290 && target.getLockRemainSeconds() <= 300);
    }

    @Test
    public void expiredPendingOrderWithReleasedSeatIsCanceled() throws Exception {
        update("UPDATE seat SET seat_status=N'可售', lock_time=NULL WHERE id=?", seatId);

        int expired = orderDAO.releaseExpiredLocks();

        assertTrue(expired >= 1);
        assertEquals("已取消", orderStatus(orderId));
        assertEquals("已取消", ticketStatus(orderId));
        assertEquals("可售", seatStatus(seatId));
    }

    @Test
    public void refundBeforeDepartureReturnsFullAmountAndReleasesSeat() throws Exception {
        assertNull(orderDAO.pay(orderId));
        assertNull(orderDAO.checkRefund(orderId));

        BigDecimal refund = orderDAO.refund(orderId, "测试退票");

        assertMoney(DEFAULT_AMOUNT, refund);
        assertEquals("已退票", orderStatus(orderId));
        assertEquals("已退票", ticketStatus(orderId));
        assertEquals("可售", seatStatus(seatId));
        assertEquals(0, soldSeats(scheduleId));
        assertMoney(DEFAULT_AMOUNT, refundAmount(orderId));
        assertEquals(1, paymentLogCount(orderId, "退票退款"));
        assertMoney(DEFAULT_AMOUNT, paymentAmount(orderId, "退票退款"));
    }

    @Test
    public void cancelBeforeDepartureRefundsAndReleasesEverything() throws Exception {
        int paidOrderId = createOrder("A2", "A2-order", new BigDecimal("120.00"),
                "已支付", "已支付", "已售");
        int checkedOrderId = createOrder("A3", "A3-order", new BigDecimal("80.00"),
                "已检票", "已检票", "已售");
        update("UPDATE schedule SET sold_seats=2 WHERE id=?", scheduleId);

        assertNull(scheduleDAO.cancelSchedule(scheduleId));

        assertEquals("已取消", scheduleStatus(scheduleId));
        assertEquals(1, scheduleDeleted(scheduleId));
        assertEquals(0, soldSeats(scheduleId));
        assertEquals("已取消", orderStatus(orderId));
        assertEquals("已取消", ticketStatus(orderId));
        assertEquals("已退票", orderStatus(paidOrderId));
        assertEquals("已退票", ticketStatus(paidOrderId));
        assertEquals("已退票", orderStatus(checkedOrderId));
        assertEquals("已退票", ticketStatus(checkedOrderId));
        assertMoney(new BigDecimal("120.00"), refundAmount(paidOrderId));
        assertMoney(new BigDecimal("80.00"), refundAmount(checkedOrderId));
        assertEquals(0, unavailableSeatCount(scheduleId));
        assertEquals(2, refundLogCount(scheduleId));
        assertMoney(new BigDecimal("200.00"), refundLogAmount(scheduleId));
    }

    @Test
    public void cancelAfterDepartureFailsWithoutChangingData() throws Exception {
        update("UPDATE schedule SET depart_time=DATEADD(MINUTE,-1,GETDATE()) WHERE id=?", scheduleId);

        String error = scheduleDAO.cancelSchedule(scheduleId);

        assertNotNull(error);
        assertTrue(error, error.contains("不能取消"));
        assertEquals("售票中", scheduleStatus(scheduleId));
        assertEquals(0, scheduleDeleted(scheduleId));
        assertEquals("待支付", orderStatus(orderId));
        assertEquals("待支付", ticketStatus(orderId));
        assertEquals("锁定", seatStatus(seatId));
        assertEquals(0, soldSeats(scheduleId));
    }

    private void assertUnpaidStateUnchanged(int expectedSoldSeats) throws SQLException {
        assertEquals("待支付", orderStatus(orderId));
        assertEquals("待支付", ticketStatus(orderId));
        assertEquals("锁定", seatStatus(seatId));
        assertEquals(expectedSoldSeats, soldSeats(scheduleId));
        assertEquals(0, paymentLogCount(orderId, "支付"));
    }

    private int createOrder(String seatNo, String orderSuffix, BigDecimal amount,
            String orderStatus, String ticketStatus, String seatStatus) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            int newSeatId = insertSeat(conn, seatNo, seatStatus);
            int newOrderId = insertOrder(conn, orderSuffix, amount, orderStatus);
            insertTicket(conn, newOrderId, newSeatId, nextIdCard(), amount, ticketStatus);
            return newOrderId;
        }
    }

    private int insertUser(Connection conn, String role, String username, String userPhone,
            String userIdCard) throws SQLException {
        return insert(conn, "INSERT INTO [user]"
                        + "(username,password,role,real_name,phone,id_card,status,fail_count) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,'normal',0)",
                username, "test-password", role, "测试" + role, userPhone, userIdCard);
    }

    private int insertRoute(Connection conn) throws SQLException {
        return insert(conn, "INSERT INTO route"
                        + "(route_name,start_port,end_port,duration_min,status,is_deleted) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,?, 'active',0)",
                "测试航线-" + tag, "测试起点-" + tag, "测试终点-" + tag, 120);
    }

    private int insertShip(Connection conn) throws SQLException {
        return insert(conn, "INSERT INTO ship"
                        + "(ship_name,total_seats,ship_type,status,is_deleted) "
                        + "OUTPUT INSERTED.id VALUES (?,20,N'普通船','active',0)",
                "测试船舶-" + tag);
    }

    private int insertSchedule(Connection conn, int totalSeats, int soldSeats)
            throws SQLException {
        return insert(conn, "INSERT INTO schedule"
                        + "(route_id,ship_id,depart_time,arrive_time,price,total_seats,"
                        + "sold_seats,status,is_deleted) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,N'售票中',0)",
                routeId, shipId, hoursFromNow(48), hoursFromNow(50), DEFAULT_AMOUNT,
                totalSeats, soldSeats);
    }

    private int insertSeat(Connection conn, String seatNo, String status) throws SQLException {
        return insert(conn, "INSERT INTO seat"
                        + "(schedule_id,seat_no,seat_status,lock_time) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,"
                        + "CASE WHEN ?=N'锁定' THEN GETDATE() ELSE NULL END)",
                scheduleId, seatNo, status, status);
    }

    private int insertOrder(Connection conn, String suffix, BigDecimal amount, String status)
            throws SQLException {
        String orderNo = tag + "-" + suffix;
        return insert(conn, "INSERT INTO [order]"
                        + "(order_no,schedule_id,passenger_id,seller_id,total_amount,status,pay_time) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,"
                        + "CASE WHEN ? IN (N'已支付',N'已检票') THEN GETDATE() ELSE NULL END)",
                orderNo, scheduleId, passengerId, sellerId, amount, status, status);
    }

    private void insertTicket(Connection conn, int targetOrderId, int targetSeatId,
            String passengerIdCard, BigDecimal amount, String status) throws SQLException {
        insert(conn, "INSERT INTO ticket"
                        + "(order_id,schedule_id,seat_id,passenger_name,id_card,"
                        + "passenger_phone,price,status,qr_code) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?)",
                targetOrderId, scheduleId, targetSeatId, "测试乘客-" + tag,
                passengerIdCard, phone, amount, status,
                "QR-" + tag + "-" + targetSeatId);
    }

    private String orderStatus(int targetOrderId) throws SQLException {
        return queryString("SELECT status FROM [order] WHERE id=?", targetOrderId);
    }

    private String ticketStatus(int targetOrderId) throws SQLException {
        return queryString("SELECT status FROM ticket WHERE order_id=?", targetOrderId);
    }

    private String seatStatus(int targetSeatId) throws SQLException {
        return queryString("SELECT seat_status FROM seat WHERE id=?", targetSeatId);
    }

    private BigDecimal refundAmount(int targetOrderId) throws SQLException {
        return queryBigDecimal("SELECT refund_amount FROM [order] WHERE id=?", targetOrderId);
    }

    private String scheduleStatus(int targetScheduleId) throws SQLException {
        return queryString("SELECT status FROM schedule WHERE id=?", targetScheduleId);
    }

    private int scheduleDeleted(int targetScheduleId) throws SQLException {
        return queryInt("SELECT is_deleted FROM schedule WHERE id=?", targetScheduleId);
    }

    private int soldSeats(int targetScheduleId) throws SQLException {
        return queryInt("SELECT sold_seats FROM schedule WHERE id=?", targetScheduleId);
    }

    private int unavailableSeatCount(int targetScheduleId) throws SQLException {
        return queryInt("SELECT COUNT(*) FROM seat WHERE schedule_id=? AND seat_status<>N'可售'",
                targetScheduleId);
    }

    private int paymentLogCount(int targetOrderId, String type) throws SQLException {
        return queryInt("SELECT COUNT(*) FROM payment_log WHERE order_id=? AND type=?",
                targetOrderId, type);
    }

    private BigDecimal paymentAmount(int targetOrderId, String type) throws SQLException {
        return queryBigDecimal("SELECT SUM(amount) FROM payment_log WHERE order_id=? AND type=?",
                targetOrderId, type);
    }

    private int refundLogCount(int targetScheduleId) throws SQLException {
        return queryInt("SELECT COUNT(*) FROM payment_log p JOIN [order] o ON o.id=p.order_id "
                + "WHERE o.schedule_id=? AND p.type=N'退票退款' AND p.direction='out'",
                targetScheduleId);
    }

    private BigDecimal refundLogAmount(int targetScheduleId) throws SQLException {
        return queryBigDecimal("SELECT SUM(p.amount) FROM payment_log p "
                + "JOIN [order] o ON o.id=p.order_id "
                + "WHERE o.schedule_id=? AND p.type=N'退票退款' AND p.direction='out'",
                targetScheduleId);
    }

    private String queryString(String sql, Object... params) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("未查到测试数据：" + sql, rs.next());
                return rs.getString(1);
            }
        }
    }

    private int queryInt(String sql, Object... params) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("未查到测试数据：" + sql, rs.next());
                return rs.getInt(1);
            }
        }
    }

    private BigDecimal queryBigDecimal(String sql, Object... params) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("未查到测试数据：" + sql, rs.next());
                return rs.getBigDecimal(1);
            }
        }
    }

    private void update(String sql, Object... params) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            execute(conn, sql, params);
        }
    }

    private int insert(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("插入测试数据未返回主键", rs.next());
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
            } else {
                ps.setString(i + 1, String.valueOf(value));
            }
        }
    }

    private String idCard(long sequence) {
        return String.format("99%016d", Math.abs(sequence) % 10000000000000000L);
    }

    private String nextIdCard() {
        return String.format("88%016d", SEQUENCE.incrementAndGet() % 10000000000000000L);
    }

    private String nextPhone() {
        return String.format("136%08d", SEQUENCE.incrementAndGet() % 100000000L);
    }

    private Timestamp hoursFromNow(int hours) {
        return new Timestamp(System.currentTimeMillis() + hours * 3600000L);
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals("金额不一致，期望 " + expected + "，实际 " + actual,
                0, expected.compareTo(actual));
    }
}
