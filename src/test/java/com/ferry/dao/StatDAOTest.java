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
    public void reportsUseNetIncomeAndDoNotDuplicateSoldSeats() throws Exception {
        BigDecimal incomeBefore = money(dao.overview().get("total_income"));

        insertOrder("paid", new BigDecimal("100.00"), "已支付", BigDecimal.ZERO);
        insertOrder("refunded", new BigDecimal("160.00"), "已退票", new BigDecimal("130.00"));
        insertOrder("pending", new BigDecimal("999.00"), "待支付", BigDecimal.ZERO);
        insertOrder("cancelled", new BigDecimal("777.00"), "已取消", BigDecimal.ZERO);

        BigDecimal incomeAfter = money(dao.overview().get("total_income"));
        assertEquals("总净收入应只计入实收与未退部分",
                new BigDecimal("130.00"), incomeAfter.subtract(incomeBefore));

        Map<String, Object> route = routeRow();
        assertNotNull(route);
        assertEquals("4", route.get("order_count"));
        assertEquals(0, new BigDecimal("130.00").compareTo(money(route.get("income"))));
        assertEquals("2", route.get("sold"));

        Map<String, Object> seller = sellerRow();
        assertNotNull(seller);
        assertEquals("2", seller.get("order_count"));
        assertEquals(0, new BigDecimal("130.00").compareTo(money(seller.get("total_amount"))));
        assertEquals(0, new BigDecimal("65.00").compareTo(money(seller.get("avg_amount"))));
    }

    private Map<String, Object> routeRow() {
        List<Map<String, Object>> rows = dao.byRoute();
        for (Map<String, Object> row : rows) {
            if (("统计起点-" + tag).equals(row.get("start_port"))
                    && ("统计终点-" + tag).equals(row.get("end_port"))) {
                return row;
            }
        }
        return null;
    }

    private Map<String, Object> sellerRow() {
        List<Map<String, Object>> rows = dao.sellerPerformance();
        for (Map<String, Object> row : rows) {
            if ((tag + "_seller").equals(row.get("username"))) {
                return row;
            }
        }
        return null;
    }

    private void insertOrder(String suffix, BigDecimal amount, String status,
            BigDecimal refundAmount) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO [order]"
                     + "(order_no,schedule_id,passenger_id,seller_id,total_amount,status,"
                     + "refund_amount,refund_reason,refund_time) VALUES (?,?,?,?,?,?,"
                     + "CASE WHEN ?=N'已退票' THEN ? ELSE NULL END,"
                     + "CASE WHEN ?=N'已退票' THEN N'统计测试' ELSE NULL END,"
                     + "CASE WHEN ?=N'已退票' THEN GETDATE() ELSE NULL END)")) {
            ps.setString(1, tag + "-" + suffix);
            ps.setInt(2, scheduleId);
            ps.setInt(3, passengerId);
            ps.setInt(4, sellerId);
            ps.setBigDecimal(5, amount);
            ps.setString(6, status);
            ps.setString(7, status);
            ps.setBigDecimal(8, refundAmount);
            ps.setString(9, status);
            ps.setString(10, status);
            ps.executeUpdate();
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
