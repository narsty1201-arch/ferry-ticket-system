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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** 多票订单：下单、支付、退票必须整单原子完成，库存按票数变化。 */
public class OrderMultiTicketTest {

    private static final AtomicLong SEQUENCE = new AtomicLong(5000);

    private final OrderDAO orderDAO = new OrderDAO();

    private String tag;
    private int passengerId;
    private int routeId;
    private int shipId;
    private int scheduleId;
    private final List<Integer> seatIds = new ArrayList<Integer>();

    @Before
    public void setUp() throws Exception {
        tag = "MTP" + System.currentTimeMillis() + SEQUENCE.incrementAndGet();
        try (Connection conn = DBUtil.getConnection()) {
            passengerId = insert(conn, "INSERT INTO [user]"
                    + "(username,password,role,real_name,phone,id_card,status,fail_count) "
                    + "OUTPUT INSERTED.id VALUES (?,?, 'passenger',?,?,?,'normal',0)",
                    tag, "test-password", "多票乘客",
                    "139" + String.format("%08d", SEQUENCE.get() % 100000000L),
                    idCard(SEQUENCE.get()));
            routeId = insert(conn, "INSERT INTO route"
                    + "(route_name,start_port,end_port,duration_min,status,is_deleted) "
                    + "OUTPUT INSERTED.id VALUES (?,?,?,120,'active',0)",
                    "多票航线-" + tag, "A港", "B港");
            shipId = insert(conn, "INSERT INTO ship"
                    + "(ship_name,total_seats,ship_type,status,is_deleted) "
                    + "OUTPUT INSERTED.id VALUES (?,20,N'普通船','active',0)",
                    "多票船-" + tag);
            scheduleId = insert(conn, "INSERT INTO schedule"
                    + "(route_id,ship_id,depart_time,arrive_time,price,total_seats,sold_seats,status,is_deleted) "
                    + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,20,0,N'售票中',0)",
                    routeId, shipId, hoursFromNow(48), hoursFromNow(50), new BigDecimal("100.00"));
            for (int i = 1; i <= 3; i++) {
                seatIds.add(insert(conn, "INSERT INTO seat(schedule_id,seat_no,seat_status) "
                        + "OUTPUT INSERTED.id VALUES (?,?,N'可售')", scheduleId, "A" + i));
            }
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
                execute(conn, "DELETE FROM ticket WHERE schedule_id=?", scheduleId);
                execute(conn, "DELETE FROM [order] WHERE schedule_id=?", scheduleId);
                execute(conn, "DELETE FROM seat WHERE schedule_id=?", scheduleId);
                execute(conn, "DELETE FROM schedule WHERE id=?", scheduleId);
            }
            if (shipId > 0) execute(conn, "DELETE FROM ship WHERE id=?", shipId);
            if (routeId > 0) execute(conn, "DELETE FROM route WHERE id=?", routeId);
            if (passengerId > 0) execute(conn, "DELETE FROM [user] WHERE id=?", passengerId);
        }
    }

    @Test
    public void multiTicketOrderPaysAndRefundsAsOneTransaction() throws Exception {
        Order order = new Order();
        order.setScheduleId(scheduleId);
        order.setPassengerId(passengerId);
        List<Ticket> tickets = tickets();

        int orderId = orderDAO.lockAndCreate(order, tickets);
        assertTrue("order result=" + orderId, orderId > 0);
        assertEquals(new BigDecimal("300.00"), totalAmount(orderId));
        assertEquals(3, ticketCount(orderId));
        for (int seatId : seatIds) assertEquals("锁定", seatStatus(seatId));
        for (Ticket ticket : tickets) assertTrue(ticket.getSeatId() > 0);

        assertNull(orderDAO.pay(orderId));
        assertEquals("已支付", orderStatus(orderId));
        assertEquals(3, ticketStatusCount(orderId, "已支付"));
        assertEquals(3, soldSeats());
        for (int seatId : seatIds) assertEquals("已售", seatStatus(seatId));

        BigDecimal refund = orderDAO.refund(orderId, "多票整单退票");
        assertEquals(new BigDecimal("300.00"), refund);
        assertEquals("已退票", orderStatus(orderId));
        assertEquals(3, ticketStatusCount(orderId, "已退票"));
        assertEquals(0, soldSeats());
        for (int seatId : seatIds) assertEquals("可售", seatStatus(seatId));
    }

    @Test
    public void multiTicketOrderCanBeCanceledBeforePayment() throws Exception {
        Order order = new Order();
        order.setScheduleId(scheduleId);
        order.setPassengerId(passengerId);
        int orderId = orderDAO.lockAndCreate(order, tickets());
        assertTrue(orderId > 0);

        assertNull(orderDAO.cancelPending(orderId));
        assertEquals("已取消", orderStatus(orderId));
        assertEquals(3, ticketStatusCount(orderId, "已取消"));
        assertEquals(0, soldSeats());
        for (int seatId : seatIds) assertEquals("可售", seatStatus(seatId));
    }

    @Test
    public void multiTicketOrderChecksInAllTicketsAndShowsAllDetails() throws Exception {
        Order order = new Order();
        order.setScheduleId(scheduleId);
        order.setPassengerId(passengerId);
        int orderId = orderDAO.lockAndCreate(order, tickets());
        assertTrue(orderId > 0);
        assertNull(orderDAO.pay(orderId));

        CheckInDAO checkInDAO = new CheckInDAO();
        assertNull(checkInDAO.checkIn(order.getOrderNo(), passengerId));
        assertEquals("已检票", orderStatus(orderId));
        assertEquals(3, queryInt("SELECT COUNT(*) FROM check_in ci "
                + "JOIN ticket t ON t.id=ci.ticket_id WHERE t.order_id=? AND ci.status=N'成功'", orderId));
        assertEquals(3, checkInDAO.findTicketDetails(order.getOrderNo()).size());
        assertNotNull(checkInDAO.checkIn(order.getOrderNo(), passengerId));
    }

    @Test
    public void duplicatePassengerInOneOrderIsRejectedAtomically() throws Exception {
        Order order = new Order();
        order.setScheduleId(scheduleId);
        order.setPassengerId(passengerId);
        List<Ticket> tickets = new ArrayList<Ticket>();
        Ticket first = ticket(seatIds.get(0), idCard(1));
        Ticket samePerson = ticket(seatIds.get(1), idCard(1));
        tickets.add(first);
        tickets.add(samePerson);

        int result = orderDAO.lockAndCreate(order, tickets);
        assertEquals(-2, result);
        assertEquals(0, orderCount());
        for (int seatId : seatIds) assertEquals("可售", seatStatus(seatId));
    }

    private List<Ticket> tickets() {
        List<Ticket> tickets = new ArrayList<Ticket>();
        for (int i = 0; i < seatIds.size(); i++) {
            tickets.add(ticket(seatIds.get(i), idCard(100 + i)));
        }
        return tickets;
    }

    private Ticket ticket(int seatId, String idCard) {
        Ticket t = new Ticket();
        t.setSeatId(seatId);
        t.setPassengerName("多票乘客" + seatId);
        t.setIdCard(idCard);
        t.setPassengerPhone("138" + String.format("%08d", seatId % 100000000L));
        t.setPrice(new BigDecimal("100.00"));
        return t;
    }

    private String idCard(long seed) {
        String base = "11010119900101" + String.format("%03d", seed % 1000);
        int[] w = {7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2};
        char[] c = {'1','0','X','9','8','7','6','5','4','3','2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) sum += (base.charAt(i) - '0') * w[i];
        return base + c[sum % 11];
    }

    private Timestamp hoursFromNow(int hours) {
        return new Timestamp(System.currentTimeMillis() + hours * 3600L * 1000L);
    }

    private int insert(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private void execute(Connection conn, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }

    private String queryString(String sql, Object... args) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private int queryInt(String sql, Object... args) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private BigDecimal totalAmount(int orderId) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT total_amount FROM [order] WHERE id=?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getBigDecimal(1);
            }
        }
    }

    private int ticketCount(int orderId) throws SQLException {
        return queryInt("SELECT COUNT(*) FROM ticket WHERE order_id=?", orderId);
    }

    private int ticketStatusCount(int orderId, String status) throws SQLException {
        return queryInt("SELECT COUNT(*) FROM ticket WHERE order_id=? AND status=?", orderId, status);
    }

    private int orderCount() throws SQLException {
        return queryInt("SELECT COUNT(*) FROM [order] WHERE schedule_id=?", scheduleId);
    }

    private int soldSeats() throws SQLException {
        return queryInt("SELECT sold_seats FROM schedule WHERE id=?", scheduleId);
    }

    private String orderStatus(int orderId) throws SQLException {
        return queryString("SELECT status FROM [order] WHERE id=?", orderId);
    }

    private String seatStatus(int seatId) throws SQLException {
        return queryString("SELECT seat_status FROM seat WHERE id=?", seatId);
    }
}
