package com.ferry.dao;

import com.ferry.entity.Order;
import com.ferry.entity.Ticket;
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

/**
 * D+E 集成测试：可空 passenger_id、身份证匹配、分页边界。
 * 测试数据使用 T 前缀并在 @After 中按主键清理。
 */
public class PassengerLinkAndPagingTest {
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final OrderDAO orderDAO = new OrderDAO();
    private final UserDAO userDAO = new UserDAO();

    private String tag;
    private String phone;
    private int sellerId;
    private int passengerId;
    private int routeId;
    private int shipId;
    private int scheduleId;
    private int seatId;
    private int orderId;

    @Before
    public void setUp() throws Exception {
        long sequence = SEQUENCE.incrementAndGet();
        tag = "TD" + Long.toString(System.currentTimeMillis(), 36) + sequence;
        phone = String.format("136%08d", sequence % 100000000L);
        try (Connection conn = DBUtil.getConnection()) {
            sellerId = insertUser(conn, "seller", tag + "_seller", nextPhone(), null);
            routeId = insertRoute(conn);
            shipId = insertShip(conn);
            scheduleId = insertSchedule(conn, hoursFromNow(24), hoursFromNow(26));
            seatId = insertSeat(conn, "A1", "可售");
        }
    }

    @After
    public void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            if (orderId > 0) {
                execute(conn, "DELETE FROM payment_log WHERE order_id=?", orderId);
                execute(conn, "DELETE FROM change_log WHERE order_id=?", orderId);
                execute(conn, "DELETE ci FROM check_in ci JOIN ticket t ON t.id=ci.ticket_id "
                        + "WHERE t.order_id=?", orderId);
                execute(conn, "DELETE FROM ticket WHERE order_id=?", orderId);
                execute(conn, "DELETE FROM [order] WHERE id=?", orderId);
            }
            execute(conn, "DELETE FROM payment_log WHERE order_id IN "
                    + "(SELECT id FROM [order] WHERE order_no LIKE ?)", tag + "%");
            execute(conn, "DELETE FROM change_log WHERE order_id IN "
                    + "(SELECT id FROM [order] WHERE order_no LIKE ?)", tag + "%");
            execute(conn, "DELETE ci FROM check_in ci JOIN ticket t ON t.id=ci.ticket_id "
                    + "WHERE t.order_id IN (SELECT id FROM [order] WHERE order_no LIKE ?)", tag + "%");
            execute(conn, "DELETE FROM ticket WHERE order_id IN "
                    + "(SELECT id FROM [order] WHERE order_no LIKE ?)", tag + "%");
            execute(conn, "DELETE FROM [order] WHERE order_no LIKE ?", tag + "%");
            if (seatId > 0) execute(conn, "DELETE FROM seat WHERE id=?", seatId);
            if (scheduleId > 0) execute(conn, "DELETE FROM schedule WHERE id=?", scheduleId);
            if (shipId > 0) execute(conn, "DELETE FROM ship WHERE id=?", shipId);
            if (routeId > 0) execute(conn, "DELETE FROM route WHERE id=?", routeId);
            execute(conn, "DELETE FROM [user] WHERE username LIKE ?", tag + "%");
        }
    }

    @Test
    public void registeredPassengerIsMatchedByNormalizedIdCard() throws Exception {
        String lowerCard = String.format("87%015d", SEQUENCE.incrementAndGet() % 1000000000000000L) + "x";
        try (Connection conn = DBUtil.getConnection()) {
            passengerId = insertUser(conn, "passenger", tag + "_passenger", phone, lowerCard);
        }

        Integer matched = userDAO.findPassengerIdByIdCard(lowerCard.toUpperCase());

        assertNotNull("大小写不同的身份证应能匹配到乘客账号", matched);
        assertEquals(passengerId, matched.intValue());
        assertNull("不存在的身份证应返回 null", userDAO.findPassengerIdByIdCard("000000000000000000"));
    }

    @Test
    public void sellerOrderWithoutRegisteredPassengerStoresNull() throws Exception {
        Order o = new Order();
        o.setScheduleId(scheduleId);
        o.setPassengerId(null);
        o.setSellerId(sellerId);
        o.setTotalAmount(new BigDecimal("88.00"));

        Ticket t = new Ticket();
        t.setSeatId(seatId);
        t.setPassengerName("未注册乘客");
        t.setIdCard("990000000000000001");
        t.setPassengerPhone("13500000001");
        t.setPrice(new BigDecimal("88.00"));

        orderId = orderDAO.lockAndCreate(o, t);
        assertTrue("下单应成功", orderId > 0);

        Order saved = orderDAO.findById(orderId);
        assertNotNull(saved);
        assertNull("未注册乘客的 passenger_id 应为 NULL", saved.getPassengerId());
        assertEquals(sellerId, saved.getSellerId().intValue());
        assertEquals("未注册乘客", saved.getPassengerName());
    }

    @Test
    public void adminPagingCountsAndClampsPageBoundary() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            for (int i = 1; i <= 3; i++) {
                insertOrder(conn, tag + "-" + i, scheduleId, null, sellerId,
                        new BigDecimal("100.00"), "已支付");
            }
        }

        assertEquals(3, orderDAO.countForAdmin("已支付", tag));
        List<Order> first = orderDAO.findForAdmin("已支付", tag, 1, 2);
        List<Order> second = orderDAO.findForAdmin("已支付", tag, 2, 2);
        assertEquals(2, first.size());
        assertEquals(1, second.size());
        assertEquals(2, com.ferry.util.Page.clamp(99, 3, 2));

        try (Connection conn = DBUtil.getConnection()) {
            for (int i = 1; i <= 3; i++) {
                insertUser(conn, "passenger", tag + "_u" + i, nextPhone(), null);
            }
        }
        assertEquals(3, userDAO.countAll("passenger", tag));
        assertEquals(2, userDAO.findAll("passenger", tag, 1, 2).size());
        assertEquals(1, userDAO.findAll("passenger", tag, 2, 2).size());
    }

    private int insertUser(Connection conn, String role, String username, String userPhone,
            String idCard) throws SQLException {
        return insert(conn, "INSERT INTO [user]"
                        + "(username,password,role,real_name,phone,id_card,status,fail_count) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?, 'normal',0)",
                username, "test-password", role, "测试" + role, userPhone, idCard);
    }

    private int insertRoute(Connection conn) throws SQLException {
        return insert(conn, "INSERT INTO route"
                        + "(route_name,start_port,end_port,duration_min,status,is_deleted) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,120,'active',0)",
                "测试航线-" + tag, "测试起点-" + tag, "测试终点-" + tag);
    }

    private int insertShip(Connection conn) throws SQLException {
        return insert(conn, "INSERT INTO ship"
                        + "(ship_name,total_seats,ship_type,status,is_deleted) "
                        + "OUTPUT INSERTED.id VALUES (?,20,N'普通船','active',0)",
                "测试船舶-" + tag);
    }

    private int insertSchedule(Connection conn, Timestamp depart, Timestamp arrive)
            throws SQLException {
        return insert(conn, "INSERT INTO schedule"
                        + "(route_id,ship_id,depart_time,arrive_time,price,total_seats,"
                        + "sold_seats,status,is_deleted) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,?,100.00,20,0,N'售票中',0)",
                routeId, shipId, depart, arrive);
    }

    private int insertSeat(Connection conn, String seatNo, String status) throws SQLException {
        return insert(conn, "INSERT INTO seat"
                        + "(schedule_id,seat_no,seat_status,lock_time) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,NULL)",
                scheduleId, seatNo, status);
    }

    private int insertOrder(Connection conn, String orderNo, int targetScheduleId,
            Integer targetPassengerId, Integer targetSellerId, BigDecimal amount, String status)
            throws SQLException {
        return insert(conn, "INSERT INTO [order]"
                        + "(order_no,schedule_id,passenger_id,seller_id,total_amount,status,pay_time) "
                        + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,GETDATE())",
                orderNo, targetScheduleId, targetPassengerId, targetSellerId, amount, status);
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
            if (value == null) {
                ps.setObject(index, null);
            } else if (value instanceof Integer) {
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
        return String.format("134%08d", SEQUENCE.incrementAndGet() % 100000000L);
    }

    private Timestamp hoursFromNow(int hours) {
        return new Timestamp(System.currentTimeMillis() + hours * 3600000L);
    }
}
