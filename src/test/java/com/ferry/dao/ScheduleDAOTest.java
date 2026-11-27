package com.ferry.dao;

import com.ferry.entity.Schedule;
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ScheduleDAOTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final ScheduleDAO dao = new ScheduleDAO();

    private String tag;
    private int routeId;
    private int shipId;
    private int scheduleId;

    @Before
    public void setUp() throws Exception {
        tag = "TS" + Long.toString(System.currentTimeMillis(), 36)
                + SEQUENCE.incrementAndGet();
        try (Connection conn = DBUtil.getConnection()) {
            routeId = insert(conn, "INSERT INTO route"
                    + "(route_name,start_port,end_port,duration_min,status,is_deleted) "
                    + "OUTPUT INSERTED.id VALUES (?,?,?,?, 'active',0)",
                    "测试航线-" + tag, "测试起点-" + tag, "测试终点-" + tag, 120);
            shipId = insert(conn, "INSERT INTO ship"
                    + "(ship_name,total_seats,ship_type,status,is_deleted) "
                    + "OUTPUT INSERTED.id VALUES (?,10,N'普通船','active',0)",
                    "测试船舶-" + tag);
            scheduleId = insert(conn, "INSERT INTO schedule"
                    + "(route_id,ship_id,depart_time,arrive_time,price,total_seats,"
                    + "sold_seats,status,is_deleted) "
                    + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,5,1,N'售票中',0)",
                    routeId, shipId, hoursFromNow(48), hoursFromNow(50),
                    new BigDecimal("100.00"));
            for (int i = 1; i <= 5; i++) {
                String status = i == 1 ? "已售" : "可售";
                insert(conn, "INSERT INTO seat"
                        + "(schedule_id,seat_no,seat_status) OUTPUT INSERTED.id VALUES (?,?,?)",
                        scheduleId, "A" + i, status);
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            if (scheduleId > 0) {
                execute(conn, "DELETE FROM seat WHERE schedule_id=?", scheduleId);
                execute(conn, "DELETE FROM schedule WHERE id=?", scheduleId);
            }
            if (shipId > 0) execute(conn, "DELETE FROM ship WHERE id=?", shipId);
            if (routeId > 0) execute(conn, "DELETE FROM route WHERE id=?", routeId);
        }
    }

    @Test
    public void updateRecomputesArrivalAndAddsMissingSeats() throws Exception {
        Timestamp newDepart = hoursFromNow(72);
        Schedule s = schedule(newDepart, 8);

        assertEquals(1, dao.update(s));

        Timestamp expectedArrival = new Timestamp(newDepart.getTime() + 120L * 60L * 1000L);
        assertTrue(Math.abs(expectedArrival.getTime() - arrivalTime(scheduleId).getTime()) < 1000L);
        assertEquals(8, totalSeats(scheduleId));
        assertEquals(8, seatCount(scheduleId));
        assertEquals("已售", seatStatus(scheduleId, "A1"));
        assertEquals("可售", seatStatus(scheduleId, "A8"));
    }

    @Test
    public void updateCannotReduceSeatsAfterCreation() throws Exception {
        Schedule s = schedule(hoursFromNow(72), 4);

        try {
            dao.update(s);
            fail("减少座位数应被拒绝");
        } catch (SQLException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("不能减少座位数"));
        }

        assertEquals(5, totalSeats(scheduleId));
        assertEquals(5, seatCount(scheduleId));
    }

    @Test
    public void updateCannotExceedShipCapacity() throws Exception {
        Schedule s = schedule(hoursFromNow(72), 11);

        try {
            dao.update(s);
            fail("超过船舶容量应被拒绝");
        } catch (SQLException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("不能超过船舶容量"));
        }

        assertEquals(5, totalSeats(scheduleId));
        assertEquals(5, seatCount(scheduleId));
    }

    private Schedule schedule(Timestamp depart, int totalSeats) {
        Schedule s = new Schedule();
        s.setId(scheduleId);
        s.setRouteId(routeId);
        s.setShipId(shipId);
        s.setDepartTime(depart);
        s.setPrice(new BigDecimal("120.00"));
        s.setTotalSeats(totalSeats);
        s.setStatus("售票中");
        return s;
    }

    private Timestamp arrivalTime(int targetScheduleId) throws SQLException {
        return queryTimestamp("SELECT arrive_time FROM schedule WHERE id=?", targetScheduleId);
    }

    private int totalSeats(int targetScheduleId) throws SQLException {
        return queryInt("SELECT total_seats FROM schedule WHERE id=?", targetScheduleId);
    }

    private int seatCount(int targetScheduleId) throws SQLException {
        return queryInt("SELECT COUNT(*) FROM seat WHERE schedule_id=?", targetScheduleId);
    }

    private String seatStatus(int targetScheduleId, String seatNo) throws SQLException {
        return queryString("SELECT seat_status FROM seat WHERE schedule_id=? AND seat_no=?",
                targetScheduleId, seatNo);
    }

    private Timestamp queryTimestamp(String sql, Object... params) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getTimestamp(1);
            }
        }
    }

    private int queryInt(String sql, Object... params) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private String queryString(String sql, Object... params) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
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

    private Timestamp hoursFromNow(int hours) {
        return new Timestamp(System.currentTimeMillis() + hours * 3600000L);
    }
}
