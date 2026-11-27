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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * OrderDAO.change 的集成测试。
 *
 * 测试会向 FerryDB 写入带 T 前缀的临时数据，并在每个用例结束后按主键精确删除。
 * 运行前请确保本机 SQL Server 与 FerryDB 已启动。
 */
public class OrderDAOTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final BigDecimal DEFAULT_OLD_PRICE = new BigDecimal("100.00");
    private static final BigDecimal DEFAULT_NEW_PRICE = new BigDecimal("150.00");

    private final OrderDAO dao = new OrderDAO();

    private String tag;
    private String idCard;
    private String phone;
    private int passengerId;
    private int operatorId;
    private int routeId;
    private int shipId;
    private int oldScheduleId;
    private int newScheduleId;
    private int conflictScheduleId;
    private int oldSeatId;
    private int newSeatId;
    private int conflictSeatId;
    private int orderId;
    private int conflictOrderId;

    @Before
    public void setUp() throws Exception {
        long sequence = SEQUENCE.incrementAndGet();
        long seed = System.currentTimeMillis();
        tag = "T" + Long.toString(seed, 36) + sequence;
        idCard = String.format("99%016d", Math.abs((seed * 31 + sequence) % 10000000000000000L));
        phone = String.format("139%08d", sequence % 100000000L);

        try (Connection conn = DBUtil.getConnection()) {
            passengerId = insertUser(conn, "passenger", tag + "_passenger", phone);
            operatorId = insertUser(conn, "seller", tag + "_operator", nextPhone());
            routeId = insertRoute(conn);
            shipId = insertShip(conn);

            oldScheduleId = insertSchedule(conn, hoursFromNow(24), hoursFromNow(26),
                    DEFAULT_OLD_PRICE, 1);
            newScheduleId = insertSchedule(conn, hoursFromNow(48), hoursFromNow(50),
                    DEFAULT_NEW_PRICE, 0);
            conflictScheduleId = insertSchedule(conn, hoursFromNow(72), hoursFromNow(74),
                    DEFAULT_OLD_PRICE, 1);

            oldSeatId = insertSeat(conn, oldScheduleId, "A1", "已售");
            newSeatId = insertSeat(conn, newScheduleId, "B1", "可售");
            conflictSeatId = insertSeat(conn, conflictScheduleId, "C1", "已售");

            orderId = insertOrder(conn, oldScheduleId, oldSeatId, DEFAULT_OLD_PRICE, "已支付");
            insertTicket(conn, orderId, oldScheduleId, oldSeatId, DEFAULT_OLD_PRICE, "已支付");

            conflictOrderId = insertOrder(conn, conflictScheduleId, conflictSeatId,
                    DEFAULT_OLD_PRICE, "已支付");
            insertTicket(conn, conflictOrderId, conflictScheduleId, conflictSeatId,
                    DEFAULT_OLD_PRICE, "已支付");
        }
    }

    @After
    public void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            execute(conn, "DELETE FROM payment_log WHERE order_id IN (?,?)", orderId, conflictOrderId);
            execute(conn, "DELETE FROM change_log WHERE order_id IN (?,?)", orderId, conflictOrderId);
            execute(conn, "DELETE ci FROM check_in ci JOIN ticket t ON t.id=ci.ticket_id "
                    + "WHERE t.order_id IN (?,?)", orderId, conflictOrderId);
            execute(conn, "DELETE FROM ticket WHERE order_id IN (?,?)", orderId, conflictOrderId);
            execute(conn, "DELETE FROM [order] WHERE id IN (?,?)", orderId, conflictOrderId);
            execute(conn, "DELETE FROM seat WHERE id IN (?,?,?)", oldSeatId, newSeatId, conflictSeatId);
            execute(conn, "DELETE FROM schedule WHERE id IN (?,?,?)",
                    oldScheduleId, newScheduleId, conflictScheduleId);
            execute(conn, "DELETE FROM ship WHERE id=?", shipId);
            execute(conn, "DELETE FROM route WHERE id=?", routeId);
            execute(conn, "DELETE FROM [user] WHERE id IN (?,?)", passengerId, operatorId);
        }
    }

    @Test
    public void changeToMoreExpensiveScheduleRecordsSupplementPayment() throws Exception {
        String result = dao.change(orderId, newScheduleId, newSeatId, operatorId, "单元测试改签");

        assertNotNull(result);
        assertTrue(result, result.contains("改签成功"));
        assertTrue(result, result.contains("已补差价 ￥50.00"));
        assertSuccessfulChange(DEFAULT_NEW_PRICE, new BigDecimal("50.00"));
        assertPaymentLog("改签补款", "in", new BigDecimal("50.00"));
    }

    @Test
    public void changeToCheaperScheduleRecordsRefund() throws Exception {
        updateOrderPrice(new BigDecimal("150.00"), new BigDecimal("100.00"));

        String result = dao.change(orderId, newScheduleId, newSeatId, operatorId, "单元测试改签");

        assertNotNull(result);
        assertTrue(result, result.contains("已退差价 ￥50.00"));
        assertSuccessfulChange(new BigDecimal("100.00"), new BigDecimal("-50.00"));
        assertPaymentLog("改签退款", "out", new BigDecimal("50.00"));
    }

    @Test
    public void changeToSamePriceScheduleDoesNotWritePaymentLog() throws Exception {
        updateOrderPrice(new BigDecimal("120.00"), new BigDecimal("120.00"));

        String result = dao.change(orderId, newScheduleId, newSeatId, operatorId, "单元测试改签");

        assertNotNull(result);
        assertTrue(result, result.contains("票价相同无需补退差价"));
        assertSuccessfulChange(new BigDecimal("120.00"), new BigDecimal("0.00"));
        assertEquals(0, countOrderRows("payment_log", orderId));
    }

    @Test
    public void unpaidOrderCannotBeChanged() throws Exception {
        update("UPDATE [order] SET status=N'待支付', pay_time=NULL WHERE id=?", orderId);
        update("UPDATE ticket SET status=N'待支付' WHERE order_id=?", orderId);
        update("UPDATE seat SET seat_status=N'锁定', lock_time=GETDATE() WHERE id=?", oldSeatId);

        String result = dao.change(orderId, newScheduleId, newSeatId, operatorId, "单元测试改签");

        assertNull(result);
        assertFailedWithoutChanges("锁定", "可售");
        assertEquals("待支付", orderStatus());
        assertEquals("待支付", ticketStatus());
    }

    @Test
    public void unavailableTargetSeatCannotBeChanged() throws Exception {
        update("UPDATE seat SET seat_status=N'已售', lock_time=NULL WHERE id=?", newSeatId);

        String result = dao.change(orderId, newScheduleId, newSeatId, operatorId, "单元测试改签");

        assertNull(result);
        assertFailedWithoutChanges("已售", "已售");
    }

    @Test
    public void departedTargetScheduleCannotBeChanged() throws Exception {
        update("UPDATE schedule SET status=N'已发船' WHERE id=?", newScheduleId);

        String result = dao.change(orderId, newScheduleId, newSeatId, operatorId, "单元测试改签");

        assertNull(result);
        assertFailedWithoutChanges("已售", "可售");
    }

    @Test
    public void overlappingTicketCannotBeChanged() throws Exception {
        update("UPDATE schedule SET depart_time=?, arrive_time=? WHERE id=?",
                hoursFromNow(49), hoursFromNow(51), conflictScheduleId);

        String result = dao.change(orderId, newScheduleId, newSeatId, operatorId, "单元测试改签");

        assertNull(result);
        assertFailedWithoutChanges("已售", "可售");
    }

    private void assertSuccessfulChange(BigDecimal newPrice, BigDecimal expectedDiff)
            throws SQLException {
        assertEquals("可售", seatStatus(oldSeatId));
        assertEquals("已售", seatStatus(newSeatId));
        assertEquals(0, soldSeats(oldScheduleId));
        assertEquals(1, soldSeats(newScheduleId));
        assertOrderAndTicket(newScheduleId, newSeatId, newPrice);
        assertEquals("已支付", orderStatus());
        assertEquals("已支付", ticketStatus());
        assertChangeLog(newScheduleId, expectedDiff);
    }

    private void assertFailedWithoutChanges(String expectedOldSeatStatus,
            String expectedNewSeatStatus) throws SQLException {
        assertEquals(expectedOldSeatStatus, seatStatus(oldSeatId));
        assertEquals(expectedNewSeatStatus, seatStatus(newSeatId));
        assertEquals(1, soldSeats(oldScheduleId));
        assertEquals(0, soldSeats(newScheduleId));
        assertOrderAndTicket(oldScheduleId, oldSeatId, DEFAULT_OLD_PRICE);
        assertEquals(0, countOrderRows("change_log", orderId));
        assertEquals(0, countOrderRows("payment_log", orderId));
    }

    private void assertOrderAndTicket(int expectedScheduleId, int expectedSeatId,
            BigDecimal expectedPrice) throws SQLException {
        String sql = "SELECT o.schedule_id AS order_schedule, o.total_amount, "
                + "t.schedule_id AS ticket_schedule, t.seat_id, t.price "
                + "FROM [order] o JOIN ticket t ON t.order_id=o.id WHERE o.id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("订单或船票不存在", rs.next());
                assertEquals(expectedScheduleId, rs.getInt("order_schedule"));
                assertEquals(expectedScheduleId, rs.getInt("ticket_schedule"));
                assertEquals(expectedSeatId, rs.getInt("seat_id"));
                assertMoney(expectedPrice, rs.getBigDecimal("total_amount"));
                assertMoney(expectedPrice, rs.getBigDecimal("price"));
            }
        }
    }

    private void assertChangeLog(int expectedNewScheduleId, BigDecimal expectedDiff)
            throws SQLException {
        String sql = "SELECT old_schedule_id, new_schedule_id, old_seat_id, new_seat_id, "
                + "price_diff, operator_id, reason FROM change_log WHERE order_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("缺少改签日志", rs.next());
                assertEquals(oldScheduleId, rs.getInt("old_schedule_id"));
                assertEquals(expectedNewScheduleId, rs.getInt("new_schedule_id"));
                assertEquals(oldSeatId, rs.getInt("old_seat_id"));
                assertEquals(newSeatId, rs.getInt("new_seat_id"));
                assertEquals(operatorId, rs.getInt("operator_id"));
                assertEquals("单元测试改签", rs.getString("reason"));
                assertMoney(expectedDiff, rs.getBigDecimal("price_diff"));
                assertTrue("改签日志应只有一条", !rs.next());
            }
        }
    }

    private void assertPaymentLog(String expectedType, String expectedDirection,
            BigDecimal expectedAmount) throws SQLException {
        String sql = "SELECT type, direction, amount FROM payment_log WHERE order_id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("缺少差价资金流水", rs.next());
                assertEquals(expectedType, rs.getString("type"));
                assertEquals(expectedDirection, rs.getString("direction"));
                assertMoney(expectedAmount, rs.getBigDecimal("amount"));
                assertTrue("改签差价流水应只有一条", !rs.next());
            }
        }
    }

    private void assertMoney(BigDecimal expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals("金额不一致，期望 " + expected + "，实际 " + actual,
                0, expected.compareTo(actual));
    }

    private String seatStatus(int seatId) throws SQLException {
        return queryString("SELECT seat_status FROM seat WHERE id=?", seatId);
    }

    private int soldSeats(int scheduleId) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT sold_seats FROM schedule WHERE id=?")) {
            ps.setInt(1, scheduleId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private String orderStatus() throws SQLException {
        return queryString("SELECT status FROM [order] WHERE id=?", orderId);
    }

    private String ticketStatus() throws SQLException {
        return queryString("SELECT status FROM ticket WHERE order_id=?", orderId);
    }

    private String queryString(String sql, int id) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue("未查到测试数据：" + sql, rs.next());
                return rs.getString(1);
            }
        }
    }

    private int countOrderRows(String table, int id) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE order_id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void updateOrderPrice(BigDecimal oldPrice, BigDecimal newPrice) throws Exception {
        update("UPDATE ticket SET price=? WHERE order_id=?", oldPrice, orderId);
        update("UPDATE [order] SET total_amount=? WHERE id=?", oldPrice, orderId);
        update("UPDATE schedule SET price=? WHERE id=?", oldPrice, oldScheduleId);
        update("UPDATE schedule SET price=? WHERE id=?", newPrice, newScheduleId);
    }

    private void update(String sql, Object... params) throws SQLException {
        try (Connection conn = DBUtil.getConnection()) {
            execute(conn, sql, params);
        }
    }

    private int insertUser(Connection conn, String role, String username, String userPhone)
            throws SQLException {
        return insert(conn, "INSERT INTO [user]"
                        + "(username,password,role,real_name,phone,status,fail_count) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,'normal',0)",
                username, "test-password", role, "测试" + role, userPhone);
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

    private int insertSchedule(Connection conn, Timestamp depart, Timestamp arrive,
            BigDecimal price, int soldSeats) throws SQLException {
        return insert(conn, "INSERT INTO schedule"
                        + "(route_id,ship_id,depart_time,arrive_time,price,total_seats,"
                        + "sold_seats,status,is_deleted) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,20,?,N'售票中',0)",
                routeId, shipId, depart, arrive, price, soldSeats);
    }

    private int insertSeat(Connection conn, int scheduleId, String seatNo, String status)
            throws SQLException {
        return insert(conn, "INSERT INTO seat"
                        + "(schedule_id,seat_no,seat_status,lock_time) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,NULL)",
                scheduleId, seatNo, status);
    }

    private int insertOrder(Connection conn, int scheduleId, int seatId, BigDecimal price,
            String status) throws SQLException {
        String orderNo = "T" + System.currentTimeMillis()
                + String.format("%03d", SEQUENCE.incrementAndGet() % 1000);
        return insert(conn, "INSERT INTO [order]"
                        + "(order_no,schedule_id,passenger_id,seller_id,total_amount,status,pay_time) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,"
                        + "CASE WHEN ?=N'已支付' THEN GETDATE() ELSE NULL END)",
                orderNo, scheduleId, passengerId, operatorId, price, status, status);
    }

    private void insertTicket(Connection conn, int targetOrderId, int scheduleId, int seatId,
            BigDecimal price, String status) throws SQLException {
        insert(conn, "INSERT INTO ticket"
                        + "(order_id,schedule_id,seat_id,passenger_name,id_card,"
                        + "passenger_phone,price,status,qr_code) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,?,?,?)",
                targetOrderId, scheduleId, seatId, "测试乘客-" + tag, idCard, phone,
                price, status, "QR-" + tag + "-" + seatId);
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
            int index = i + 1;
            if (value instanceof Integer) {
                ps.setInt(index, (Integer) value);
            } else if (value instanceof BigDecimal) {
                ps.setBigDecimal(index, (BigDecimal) value);
            } else if (value instanceof Timestamp) {
                ps.setTimestamp(index, (Timestamp) value);
            } else {
                ps.setString(index, String.valueOf(value));
            }
        }
    }

    private String nextPhone() {
        return String.format("138%08d", SEQUENCE.incrementAndGet() % 100000000L);
    }

    private Timestamp hoursFromNow(int hours) {
        return new Timestamp(System.currentTimeMillis() + hours * 3600000L);
    }
}
