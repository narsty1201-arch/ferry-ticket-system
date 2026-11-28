package com.ferry.dao;

import com.ferry.entity.Order;
import com.ferry.entity.SeatCell;
import com.ferry.entity.Ticket;
import com.ferry.util.DBUtil;

import java.math.BigDecimal;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单 DAO —— 阶段4核心技术（任务书考核重点）：
 * 1) 锁座下单：UPDLOCK 行锁 + 事务，防止两个用户抢同一座位（防超卖）
 * 2) 5 分钟支付时限：锁座记录 lock_time，超时订单自动取消、座位释放
 * 3) 支付事务：订单/船票状态流转 + sold_seats+1
 * 4) 退票事务：手续费计算 + 退款金额/原因留痕 + 座位释放 + sold_seats-1
 * 5) 改签事务：新旧班次座位调整 + 差价 + change_log 日志
 */
public class OrderDAO {

    public static final int MAX_TICKETS_PER_ORDER = 5;

    /**
     * 锁座下单（单票兼容入口）。
     */
    public int lockAndCreate(Order o, Ticket t) throws SQLException {
        List<Ticket> tickets = new ArrayList<Ticket>();
        if (t != null) tickets.add(t);
        return lockAndCreate(o, tickets);
    }

    /**
     * 锁座下单（并发核心，支持一个订单最多 5 张票）。
     * 关键：SELECT ... WITH (UPDLOCK, HOLDLOCK) 在事务内锁住座位行，
     * 两个并发请求同时抢一座时，第二个会阻塞直到第一个提交，然后看到 seat_status 已是“锁定”而失败。
     *
     * 订单内所有座位按 seat_id 升序加锁，降低多票并发时的死锁概率。任一乘客校验失败、
     * 任一座位不可售或唯一索引冲突都会整单回滚，不会出现“部分出票”。
     */
    public int lockAndCreate(Order o, List<Ticket> tickets) throws SQLException {
        if (o == null || tickets == null || tickets.isEmpty()
                || tickets.size() > MAX_TICKETS_PER_ORDER) {
            return -5;   // -5 表示订单票数不合法
        }

        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            // 1. 先锁班次，再按座位 ID 顺序锁座位，保证多票订单的加锁顺序一致。
            String schedSql = "SELECT status, depart_time FROM schedule WITH (UPDLOCK, HOLDLOCK) WHERE id=? AND is_deleted=0";
            Timestamp newDepart = null;
            try (PreparedStatement ps = conn.prepareStatement(schedSql)) {
                ps.setInt(1, o.getScheduleId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return -4; }
                    String st = rs.getString("status");
                    newDepart = rs.getTimestamp("depart_time");
                    if (!"售票中".equals(st) || newDepart == null || !newDepart.after(new Timestamp(System.currentTimeMillis()))) {
                        conn.rollback();
                        return -4;   // -4 表示班次已发船 / 不可售票
                    }
                }
            }

            List<Integer> seatIds = new ArrayList<Integer>();
            for (Ticket t : tickets) {
                if (t == null || t.getSeatId() <= 0) {
                    conn.rollback();
                    return -1;
                }
                if (seatIds.contains(t.getSeatId())) {
                    conn.rollback();
                    return -5;
                }
                seatIds.add(t.getSeatId());
            }
            List<Integer> sortedSeatIds = new ArrayList<Integer>(seatIds);
            java.util.Collections.sort(sortedSeatIds);

            String lockSql = "SELECT id, seat_status FROM seat WITH (UPDLOCK, HOLDLOCK) WHERE id=? AND schedule_id=?";
            for (int seatId : sortedSeatIds) {
                boolean sellable = false;
                try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
                    ps.setInt(1, seatId);
                    ps.setInt(2, o.getScheduleId());
                    try (ResultSet rs = ps.executeQuery()) {
                        sellable = rs.next() && "可售".equals(rs.getString("seat_status"));
                    }
                }
                if (!sellable) {
                    conn.rollback();
                    return -1;   // -1 表示座位已被占/不存在
                }
            }

            // 2. 每位乘客分别校验“一人一班次一票”和时段重叠。
            //    同一订单内也不允许重复证件，避免把一张票卖给同一个人两次。
            List<String> idCards = new ArrayList<String>();
            for (Ticket t : tickets) {
                String idCard = t.getIdCard() == null ? "" : t.getIdCard().trim().toUpperCase();
                if (idCards.contains(idCard)) {
                    conn.rollback();
                    return -2;
                }
                idCards.add(idCard);

                String dupSql = "SELECT COUNT(*) FROM ticket WITH (UPDLOCK, HOLDLOCK) "
                              + "WHERE id_card=? AND schedule_id=? "
                              + "AND status IN (N'待支付',N'已支付',N'已检票')";
                try (PreparedStatement ps = conn.prepareStatement(dupSql)) {
                    ps.setString(1, idCard);
                    ps.setInt(2, o.getScheduleId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            conn.rollback();
                            return -2;   // -2 表示该证件已买过此班次
                        }
                    }
                }

                String overlapSql = "SELECT COUNT(*) FROM ticket t WITH (UPDLOCK, HOLDLOCK) "
                                  + "JOIN schedule s ON s.id=t.schedule_id "
                                  + "JOIN schedule ns ON ns.id=? "
                                  + "WHERE t.id_card=? AND t.status IN (N'待支付',N'已支付',N'已检票') "
                                  + "AND s.depart_time < ns.arrive_time AND s.arrive_time > ns.depart_time";
                try (PreparedStatement ps = conn.prepareStatement(overlapSql)) {
                    ps.setInt(1, o.getScheduleId());
                    ps.setString(2, idCard);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            conn.rollback();
                            return -3;   // -3 表示该证件已有同一时段的船票
                        }
                    }
                }
            }

            // 3. 全部座位改为“锁定”，记录锁定时间（5分钟倒计时起点）
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE seat SET seat_status=N'锁定', lock_time=GETDATE() WHERE id=?")) {
                for (int seatId : sortedSeatIds) {
                    ps.setInt(1, seatId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // 4. 生成订单号：FD + 时间戳 + 3位随机数
            String orderNo = "FD" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                           + String.format("%03d", (int)(Math.random() * 1000));
            o.setOrderNo(orderNo);

            // 5. 插入订单（待支付），金额由票面单价 × 票数计算，避免前端篡改。
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (Ticket t : tickets) {
                if (t.getPrice() == null || t.getPrice().signum() <= 0) {
                    conn.rollback();
                    return -5;
                }
                totalAmount = totalAmount.add(t.getPrice());
            }
            o.setTotalAmount(totalAmount);
            int orderId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO [order](order_no,schedule_id,passenger_id,seller_id,total_amount,status) "
                  + "VALUES (?,?,?,?,?,N'待支付')", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, orderNo);
                ps.setInt(2, o.getScheduleId());
                if (o.getPassengerId() == null) ps.setNull(3, Types.INTEGER);
                else ps.setInt(3, o.getPassengerId());
                if (o.getSellerId() == null) ps.setNull(4, Types.INTEGER);
                else ps.setInt(4, o.getSellerId());
                ps.setBigDecimal(5, o.getTotalAmount());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    orderId = keys.getInt(1);
                }
            }

            // 6. 插入船票（待支付），绑定锁定的座位
            int ticketNo = 1;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ticket(order_id,schedule_id,seat_id,passenger_name,id_card,passenger_phone,price,status,qr_code) "
                  + "VALUES (?,?,?,?,?,?,?,N'待支付',?)")) {
                for (Ticket t : tickets) {
                    ps.setInt(1, orderId);
                    ps.setInt(2, o.getScheduleId());
                    ps.setInt(3, t.getSeatId());
                    ps.setString(4, t.getPassengerName());
                    ps.setString(5, t.getIdCard());
                    ps.setString(6, t.getPassengerPhone());
                    ps.setBigDecimal(7, t.getPrice());
                    ps.setString(8, "QR-" + orderNo + "-" + ticketNo);
                    ps.addBatch();
                    ticketNo++;
                }
                ps.executeBatch();
            } catch (SQLException e) {
                // 并发下两个请求可能同时通过前置重复检查，最终由筛选唯一索引兜底。
                // 这里只处理船票插入，避免把订单号冲突误报成重复购票。
                if (isDuplicateTicketError(e)) {
                    conn.rollback();
                    return -2;
                }
                throw e;
            }

            conn.commit();
            return orderId;
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                DBUtil.close(conn);
            }
        }
    }

    /** SQL Server 唯一键/唯一索引冲突；调用点仅限 ticket 插入。 */
    private boolean isDuplicateTicketError(SQLException e) {
        return e.getErrorCode() == 2601 || e.getErrorCode() == 2627;
    }

    /**
     * 在事务内锁定并读取订单的全部船票和座位状态。
     * 订单内所有票按 ticket.id 排序，便于后续批量更新时保持稳定输出。
     */
    private List<Ticket> findOrderTicketsForUpdate(Connection conn, int orderId) throws SQLException {
        List<Ticket> tickets = new ArrayList<Ticket>();
        String sql = "SELECT t.id, t.order_id, t.schedule_id, t.seat_id, t.passenger_name, "
                   + "t.id_card, t.passenger_phone, t.price, t.status, t.qr_code, "
                   + "se.seat_no, se.seat_status, "
                   + "CASE WHEN se.lock_time IS NULL OR se.lock_time < DATEADD(MINUTE,-5,GETDATE()) "
                   + "THEN 1 ELSE 0 END AS lock_expired "
                   + "FROM ticket t WITH (UPDLOCK, HOLDLOCK) "
                   + "JOIN seat se WITH (UPDLOCK, HOLDLOCK) ON se.id=t.seat_id "
                   + "WHERE t.order_id=? ORDER BY t.id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Ticket t = new Ticket();
                    t.setId(rs.getInt("id"));
                    t.setOrderId(rs.getInt("order_id"));
                    t.setScheduleId(rs.getInt("schedule_id"));
                    t.setSeatId(rs.getInt("seat_id"));
                    t.setPassengerName(rs.getString("passenger_name"));
                    t.setIdCard(rs.getString("id_card"));
                    t.setPassengerPhone(rs.getString("passenger_phone"));
                    t.setPrice(rs.getBigDecimal("price"));
                    t.setStatus(rs.getString("status"));
                    t.setQrCode(rs.getString("qr_code"));
                    t.setSeatNo(rs.getString("seat_no"));
                    t.setSeatStatus(rs.getString("seat_status"));
                    t.setLockExpired(rs.getInt("lock_expired") == 1);
                    tickets.add(t);
                }
            }
        }
        return tickets;
    }

    /**
     * 释放超时锁座（5分钟）：把锁定超过5分钟的座位恢复可售，
     * 并把对应的“待支付”订单/船票置为“已取消”。下单前调用即可实现自动过期。
     */
    public int releaseExpiredLocks() throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // 找出锁定超时的座位对应的订单
            String findSql = "SELECT o.id, t.seat_id FROM ticket t JOIN [order] o ON o.id=t.order_id "
                           + "JOIN seat s ON s.id=t.seat_id "
                           + "WHERE t.status=N'待支付' AND s.seat_status<>N'已售' "
                           + "AND (s.lock_time IS NULL OR s.lock_time < DATEADD(MINUTE,-5,GETDATE()))";
            List<Integer> expiredOrders = new ArrayList<>();
            List<Integer> expiredSeats = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(findSql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    expiredOrders.add(rs.getInt("id"));
                    expiredSeats.add(rs.getInt("seat_id"));
                }
            }
            if (!expiredOrders.isEmpty()) {
                for (int seatId : expiredSeats) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE seat SET seat_status=N'可售', lock_time=NULL WHERE id=? AND seat_status=N'锁定'")) {
                        ps.setInt(1, seatId);
                        ps.executeUpdate();
                    }
                }
                for (int oid : expiredOrders) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE [order] SET status=N'已取消', cancel_time=GETDATE() WHERE id=? AND status=N'待支付'")) {
                        ps.setInt(1, oid);
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE ticket SET status=N'已取消' WHERE order_id=? AND status=N'待支付'")) {
                        ps.setInt(1, oid);
                        ps.executeUpdate();
                    }
                }
            }
            conn.commit();
            return expiredOrders.size();
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                DBUtil.close(conn);
            }
        }
    }

    /**
     * 取消待支付订单：订单/船票→已取消，座位→可售。
     * 与支付、超时释放共用行锁，先到先得。
     *
     * @return null 表示成功；否则返回不能取消的原因
     */
    public String cancelPending(int orderId) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            String orderStatus;
            String sql = "SELECT status FROM [order] WITH (UPDLOCK, HOLDLOCK) WHERE id=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return "订单不存在或数据异常";
                    }
                    orderStatus = rs.getString("status");
                }
            }
            if (!"待支付".equals(orderStatus)) {
                conn.rollback();
                return "该订单已支付或已取消，不能取消";
            }

            List<Ticket> tickets = findOrderTicketsForUpdate(conn, orderId);
            if (tickets.isEmpty()) {
                conn.rollback();
                return "订单船票数据异常";
            }
            for (Ticket ticket : tickets) {
                if (!"待支付".equals(ticket.getStatus()) || "已售".equals(ticket.getSeatStatus())) {
                    conn.rollback();
                    return "船票状态已变化，不能取消";
                }
            }

            // 逐个释放订单内的全部锁定座位；已经释放过的座位跳过，保证重试可恢复。
            for (Ticket ticket : tickets) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE seat SET seat_status=N'可售', lock_time=NULL "
                      + "WHERE id=? AND seat_status IN (N'锁定',N'可售')")) {
                    ps.setInt(1, ticket.getSeatId());
                    if (ps.executeUpdate() != 1) {
                        conn.rollback();
                        return "座位状态已变化，取消失败";
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ticket SET status=N'已取消' WHERE order_id=? AND status=N'待支付'")) {
                ps.setInt(1, orderId);
                if (ps.executeUpdate() != tickets.size()) {
                    conn.rollback();
                    return "船票状态已变化，取消失败";
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE [order] SET status=N'已取消', cancel_time=GETDATE() "
                  + "WHERE id=? AND status=N'待支付'")) {
                ps.setInt(1, orderId);
                if (ps.executeUpdate() != 1) {
                    conn.rollback();
                    return "订单状态已变化，取消失败";
                }
            }
            conn.commit();
            return null;
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                DBUtil.close(conn);
            }
        }
    }

    /**
     * 支付：订单→已支付、船票→已支付、座位→已售、sold_seats+1，全事务。
     *
     * @return null 表示支付成功；否则返回不能支付的原因
     */
    public String pay(int orderId) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);
            String orderNo = null;
            int scheduleId;

            // 校验订单仍是待支付（防止重复支付）
            String status = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status, order_no, schedule_id FROM [order] "
                  + "WITH (UPDLOCK, HOLDLOCK) WHERE id=?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return "订单不存在";
                    }
                    status = rs.getString("status");
                    orderNo = rs.getString("order_no");
                    scheduleId = rs.getInt("schedule_id");
                }
            }
            if (!"待支付".equals(status)) {
                conn.rollback();
                return "该订单当前状态不能支付";
            }

            // 班次必须仍在售票且未发船；同时锁住班次，避免与取消班次并发
            int totalSeats;
            int soldSeats;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status, depart_time, total_seats, sold_seats FROM schedule "
                  + "WITH (UPDLOCK, HOLDLOCK) WHERE id=? AND is_deleted=0")) {
                ps.setInt(1, scheduleId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return "班次不存在或已取消";
                    }
                    Timestamp dep = rs.getTimestamp("depart_time");
                    if (!"售票中".equals(rs.getString("status")) || dep == null
                            || !dep.after(new Timestamp(System.currentTimeMillis()))) {
                        conn.rollback();
                        return "班次已发船或停止售票";
                    }
                    totalSeats = rs.getInt("total_seats");
                    soldSeats = rs.getInt("sold_seats");
                }
            }
            if (soldSeats >= totalSeats) {
                conn.rollback();
                return "班次余票不足";
            }

            // 锁住订单内的全部船票与座位；必须全部处于待支付锁定状态，不能部分支付。
            List<Ticket> tickets = findOrderTicketsForUpdate(conn, orderId);
            if (tickets.isEmpty()) {
                conn.rollback();
                return "订单船票数据异常";
            }
            if (soldSeats + tickets.size() > totalSeats) {
                conn.rollback();
                return "班次余票不足";
            }
            for (Ticket ticket : tickets) {
                if (!"待支付".equals(ticket.getStatus())) {
                    conn.rollback();
                    return "船票状态异常，不能支付";
                }
                if (!"锁定".equals(ticket.getSeatStatus())) {
                    conn.rollback();
                    return "座位已释放，请重新下单";
                }
                if (ticket.isLockExpired()) {
                    conn.rollback();
                    return "支付超时，座位已释放，请重新选座";
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE seat SET seat_status=N'已售', lock_time=NULL "
                  + "WHERE id=? AND schedule_id=? AND seat_status=N'锁定'")) {
                for (Ticket ticket : tickets) {
                    ps.setInt(1, ticket.getSeatId());
                    ps.setInt(2, scheduleId);
                    ps.addBatch();
                }
                int[] updated = ps.executeBatch();
                for (int count : updated) {
                    if (count != 1) {
                        conn.rollback();
                        return "座位状态已变化，支付失败";
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ticket SET status=N'已支付' WHERE order_id=? AND status=N'待支付'")) {
                ps.setInt(1, orderId);
                if (ps.executeUpdate() != tickets.size()) {
                    conn.rollback();
                    return "船票状态已变化，支付失败";
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE [order] SET status=N'已支付', pay_time=GETDATE() "
                  + "WHERE id=? AND status=N'待支付'")) {
                ps.setInt(1, orderId);
                if (ps.executeUpdate() != 1) {
                    conn.rollback();
                    return "订单状态已变化，支付失败";
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE schedule SET sold_seats=sold_seats+? "
                  + "WHERE id=? AND sold_seats+?<=total_seats")) {
                ps.setInt(1, tickets.size());
                ps.setInt(2, scheduleId);
                ps.setInt(3, tickets.size());
                if (ps.executeUpdate() != 1) {
                    conn.rollback();
                    return "班次余票不足";
                }
            }
            // 资金流水：支付入账
            BigDecimal payAmount = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT total_amount FROM [order] WHERE id=?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) payAmount = rs.getBigDecimal("total_amount"); }
            }
            if (payAmount != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payment_log(order_id,order_no,type,amount,direction,remark) "
                      + "VALUES (?,?,N'支付',?,'in',N'购票支付')")) {
                    ps.setInt(1, orderId);
                    ps.setString(2, orderNo);
                    ps.setBigDecimal(3, payAmount);
                    ps.executeUpdate();
                }
            }
            conn.commit();
            return null;
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                DBUtil.close(conn);
            }
        }
    }

    /**
     * 退票前置校验：返回 null 表示允许退票，否则返回不能退票的原因。
     * 规则：发船前 2 小时以上免费退票；不足 2 小时扣 20%；发船后不退。
     */
    public String checkRefund(int orderId) {
        String sql = "SELECT o.status, s.depart_time, s.status AS sched_status "
                   + "FROM [order] o JOIN schedule s ON s.id=o.schedule_id WHERE o.id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "订单不存在";
                String st = rs.getString("status");
                if (!"已支付".equals(st) && !"已检票".equals(st)) return "该订单当前状态不能退票";
                Timestamp depart = rs.getTimestamp("depart_time");
                if (depart == null || !depart.after(new Timestamp(System.currentTimeMillis()))) {
                    return "班次已发船，不能退票";
                }
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "校验失败，请稍后重试";
        }
    }

    /**
     * 退票：按距发船时间计算手续费，事务内改订单/船票状态、释放座位、记录退款金额和原因。
     * 手续费规则：距发船 2 小时以上免手续费；不足 2 小时收 20%。
     * 发船后禁止退票（与 checkRefund 双重保险）。
     */
    public BigDecimal refund(int orderId, String reason) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // 查询订单金额、班次发船时间，并锁住订单内全部船票/座位
            BigDecimal amount = null;
            Timestamp depart = null;
            int scheduleId = 0;
            String status;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT o.total_amount, o.status, s.depart_time, o.schedule_id "
                  + "FROM [order] o WITH (UPDLOCK, HOLDLOCK) "
                  + "JOIN schedule s WITH (UPDLOCK, HOLDLOCK) ON s.id=o.schedule_id "
                  + "WHERE o.id=?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        amount = rs.getBigDecimal("total_amount");
                        status = rs.getString("status");
                        depart = rs.getTimestamp("depart_time");
                        scheduleId = rs.getInt("schedule_id");
                        if (!"已支付".equals(status) && !"已检票".equals(status)) {
                            conn.rollback();
                            return null;   // 只有已支付/已检票可退
                        }
                        if (depart == null || !depart.after(new Timestamp(System.currentTimeMillis()))) {
                            conn.rollback();
                            return null;   // 已发船不能退
                        }
                    } else {
                        conn.rollback();
                        return null;
                    }
                }
            }

            List<Ticket> tickets = findOrderTicketsForUpdate(conn, orderId);
            if (tickets.isEmpty()) {
                conn.rollback();
                return null;
            }
            for (Ticket ticket : tickets) {
                if (!"已支付".equals(ticket.getStatus()) && !"已检票".equals(ticket.getStatus())) {
                    throw new SQLException("存在未支付或已退票的船票，退票失败");
                }
                if (!"已售".equals(ticket.getSeatStatus())) {
                    throw new SQLException("座位状态异常，退票失败");
                }
            }

            // 手续费计算：不足 2 小时收 20%，2 小时及以上免手续费。
            long millisToDepart = depart.getTime() - System.currentTimeMillis();
            BigDecimal rate = millisToDepart < 2L * 60L * 60L * 1000L
                    ? new BigDecimal("0.20") : BigDecimal.ZERO;
            BigDecimal fee = amount.multiply(rate).setScale(2, BigDecimal.ROUND_HALF_UP);
            BigDecimal refundAmount = amount.subtract(fee);

            // 订单：已退票 + 退款三字段 + 预计到账时间（12306：1~15工作日，本系统按3个工作日演示）
            String finalReason = reason == null || reason.trim().isEmpty()
                    ? "乘客主动退票" : limitText(reason.trim(), 200);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE [order] SET status=N'已退票', refund_amount=?, refund_reason=?, "
                  + "refund_time=GETDATE(), refund_eta=DATEADD(DAY,3,GETDATE()), cancel_time=GETDATE() "
                  + "WHERE id=? AND status IN (N'已支付',N'已检票')")) {
                ps.setBigDecimal(1, refundAmount);
                ps.setString(2, finalReason);
                ps.setInt(3, orderId);
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("订单状态已变化，退票失败");
                }
            }
            // 资金流水：退票退款（出站）
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO payment_log(order_id,order_no,type,amount,direction,remark) "
                  + "SELECT id, order_no, N'退票退款', ?, 'out', ? FROM [order] WHERE id=?")) {
                ps.setBigDecimal(1, refundAmount);
                ps.setString(2, finalReason);
                ps.setInt(3, orderId);
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("写入退票资金流水失败");
                }
            }
            // 船票：已退票
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ticket SET status=N'已退票' "
                  + "WHERE order_id=? AND status IN (N'已支付',N'已检票')")) {
                ps.setInt(1, orderId);
                if (ps.executeUpdate() != tickets.size()) {
                    throw new SQLException("船票状态已变化，退票失败");
                }
            }
            // 座位：订单内全部已售→可售
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE seat SET seat_status=N'可售', lock_time=NULL "
                  + "WHERE id=? AND seat_status=N'已售'")) {
                for (Ticket ticket : tickets) {
                    ps.setInt(1, ticket.getSeatId());
                    ps.addBatch();
                }
                int[] updated = ps.executeBatch();
                for (int count : updated) {
                    if (count != 1) {
                        throw new SQLException("座位状态异常，退票失败");
                    }
                }
            }
            // 班次已售 -票数
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE schedule SET sold_seats=sold_seats-? "
                  + "WHERE id=? AND sold_seats>=?")) {
                ps.setInt(1, tickets.size());
                ps.setInt(2, scheduleId);
                ps.setInt(3, tickets.size());
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("班次已售数异常，退票失败");
                }
            }

            conn.commit();
            return refundAmount;
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                DBUtil.close(conn);
            }
        }
    }

    /**
     * 改签前置校验：返回 null 表示允许，否则返回原因。
     * 规则：仅“已支付”订单；新旧班次发船前至少 1 小时；目标班次仍在售票且更晚。
     */
    public String checkChange(int orderId, int newScheduleId) {
        String sql = "SELECT o.status, o.schedule_id AS old_schedule_id, "
                   + "s.depart_time AS old_depart, ns.status AS new_status, "
                   + "ns.depart_time AS new_depart, "
                   + "(SELECT COUNT(*) FROM ticket t WHERE t.order_id=o.id) AS ticket_count "
                   + "FROM [order] o JOIN schedule s ON s.id=o.schedule_id "
                   + "JOIN schedule ns ON ns.id=? WHERE o.id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newScheduleId);
            ps.setInt(2, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "订单或目标班次不存在";
                if (!"已支付".equals(rs.getString("status"))) return "只有已支付的订单可以改签";
                if (rs.getInt("ticket_count") != 1) {
                    return "多票订单暂不支持改签，请整单退票后重新购买";
                }
                Timestamp nd = rs.getTimestamp("new_depart");
                Timestamp od = rs.getTimestamp("old_depart");
                if (!"售票中".equals(rs.getString("new_status")) || nd == null
                        || !nd.after(new Timestamp(System.currentTimeMillis()))) {
                    return "目标班次已发船或停止售票，不能改签";
                }
                if (od == null || od.getTime() - System.currentTimeMillis()
                        < 60L * 60L * 1000L) {
                    return "原班次发船前不足 1 小时，不能改签";
                }
                if (nd.getTime() - System.currentTimeMillis()
                        < 60L * 60L * 1000L) {
                    return "目标班次发船前不足 1 小时，不能改签";
                }
                int oldScheduleId = rs.getInt("old_schedule_id");
                if (od != null && newScheduleId != oldScheduleId && !nd.after(od)) {
                    return "只能改签到同班次其他座位，或发船时间更晚的班次";
                }
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "校验失败，请稍后重试";
        }
    }

    /**
     * 改签：换班次换座位，记 change_log。
     * 事务内：新座位 UPDLOCK 锁定→已售、旧座位释放、班次已售调整、
     * 船票改绑新班次/座位、订单更新班次和金额、写改签日志。
     * @return null 表示失败；否则返回给页面展示的结果说明（含差价收退情况）
     */
    public String change(int orderId, int newScheduleId, int newSeatId, int operatorId, String reason)
            throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // 1-2. 校验原订单、目标座位、目标班次，以及同一证件的时段冲突（均带 UPDLOCK）
            ChangeOrder current = findChangeableOrder(conn, orderId);
            if (current == null) {
                conn.rollback();
                return null;
            }
            if (!lockSellableSeat(conn, newSeatId, newScheduleId)) {
                conn.rollback();
                return null;
            }
            TargetSchedule target = lockSellableSchedule(conn, newScheduleId);
            if (target == null) {
                conn.rollback();
                return null;
            }
            if (current.departTime == null
                    || (newScheduleId != current.scheduleId
                        && !target.departTime.after(current.departTime))) {
                conn.rollback();
                return null;
            }
            if (hasTicketOverlap(conn, orderId, newScheduleId)) {
                conn.rollback();
                return null;
            }
            BigDecimal newPrice = target.price;
            BigDecimal diff = newPrice.subtract(current.price);   // 正数补差价、负数退款

            // 3. 新座位→已售，旧座位→可售
            moveSeat(conn, current.seatId, newSeatId);
            // 4. 班次已售调整
            adjustSoldSeats(conn, current.scheduleId, newScheduleId);
            // 5. 船票改绑新班次/座位/价格，订单更新班次和总价
            rebindTicketAndOrder(conn, orderId, newScheduleId, newSeatId, newPrice);
            // 6. 写改签日志（任务书要求）
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO change_log(order_id,old_schedule_id,new_schedule_id,old_seat_id,new_seat_id,price_diff,operator_id,reason) "
                  + "VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setInt(1, orderId);
                ps.setInt(2, current.scheduleId);
                ps.setInt(3, newScheduleId);
                ps.setInt(4, current.seatId);
                ps.setInt(5, newSeatId);
                ps.setBigDecimal(6, diff);
                ps.setInt(7, operatorId);
                ps.setString(8, reason == null || reason.trim().isEmpty()
                        ? "乘客改签" : limitText(reason.trim(), 200));
                ps.executeUpdate();
            }
            // 7. 差价真实收退：写资金流水（正数=补款 in，负数=退款 out）
            String settle = settlePriceDiff(conn, orderId, diff);

            conn.commit();
            return "改签成功，已记录改签日志" + settle;
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                DBUtil.close(conn);
            }
        }
    }

    /** 改签用到的原订单数据：原班次、原座位、原票价、原发船时间。 */
    private static final class ChangeOrder {
        private final int scheduleId;
        private final int seatId;
        private final BigDecimal price;
        private final Timestamp departTime;

        private ChangeOrder(int scheduleId, int seatId, BigDecimal price, Timestamp departTime) {
            this.scheduleId = scheduleId;
            this.seatId = seatId;
            this.price = price;
            this.departTime = departTime;
        }
    }

    private static final class TargetSchedule {
        private final BigDecimal price;
        private final Timestamp departTime;

        private TargetSchedule(BigDecimal price, Timestamp departTime) {
            this.price = price;
            this.departTime = departTime;
        }
    }

    /** 读取原订单与原票；订单不存在或不是“已支付”时返回 null。 */
    private ChangeOrder findChangeableOrder(Connection conn, int orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT o.schedule_id, t.seat_id, t.price, s.depart_time "
              + "FROM [order] o WITH (UPDLOCK, HOLDLOCK) "
              + "JOIN ticket t WITH (UPDLOCK, HOLDLOCK) ON t.order_id=o.id "
              + "JOIN schedule s WITH (UPDLOCK, HOLDLOCK) ON s.id=o.schedule_id "
              + "WHERE o.id=? AND o.status=N'已支付'")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int scheduleId = rs.getInt("schedule_id");
                int seatId = rs.getInt("seat_id");
                BigDecimal price = rs.getBigDecimal("price");
                Timestamp depart = rs.getTimestamp("depart_time");
                if (rs.next()) return null;   // 多票订单不允许走单票改签
                return new ChangeOrder(scheduleId, seatId, price, depart);
            }
        }
    }

    /** UPDLOCK 锁新座位并确认可售（并发安全）。 */
    private boolean lockSellableSeat(Connection conn, int newSeatId, int newScheduleId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT seat_status FROM seat WITH (UPDLOCK, HOLDLOCK) WHERE id=? AND schedule_id=?")) {
            ps.setInt(1, newSeatId);
            ps.setInt(2, newScheduleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "可售".equals(rs.getString("seat_status"));
            }
        }
    }

    /** 锁新班次并返回票价与发船时间；不存在、非“售票中”或已发船时返回 null。 */
    private TargetSchedule lockSellableSchedule(Connection conn, int newScheduleId)
            throws SQLException {
        String sql = "SELECT status, price, depart_time FROM schedule "
                   + "WITH (UPDLOCK, HOLDLOCK) WHERE id=? AND is_deleted=0";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newScheduleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String status = rs.getString("status");
                Timestamp depart = rs.getTimestamp("depart_time");
                if (!"售票中".equals(status) || depart == null
                        || !depart.after(new Timestamp(System.currentTimeMillis()))) {
                    return null;
                }
                return new TargetSchedule(rs.getBigDecimal("price"), depart);
            }
        }
    }

    /** 同一证件在目标班次时段是否已有其它有效票（排除本订单自己的票）。 */
    private boolean hasTicketOverlap(Connection conn, int orderId, int newScheduleId)
            throws SQLException {
        String idCard = findTicketIdCard(conn, orderId);
        if (idCard == null) return false;
        String sql = "SELECT COUNT(*) FROM ticket t WITH (UPDLOCK, HOLDLOCK) "
                   + "JOIN schedule s ON s.id=t.schedule_id "
                   + "JOIN schedule ns ON ns.id=? "
                   + "WHERE t.id_card=? AND t.order_id<>? "
                   + "AND t.status IN (N'待支付',N'已支付',N'已检票') "
                   + "AND s.depart_time < ns.arrive_time AND s.arrive_time > ns.depart_time";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newScheduleId);
            ps.setString(2, idCard);
            ps.setInt(3, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private String findTicketIdCard(Connection conn, int orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id_card FROM ticket WHERE order_id=?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("id_card") : null;
            }
        }
    }

    /** 新座位置为已售，旧座位释放回可售。 */
    private void moveSeat(Connection conn, int oldSeatId, int newSeatId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE seat SET seat_status=N'已售', lock_time=NULL "
              + "WHERE id=? AND seat_status=N'可售'")) {
            ps.setInt(1, newSeatId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("新座位状态已变化，改签失败");
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE seat SET seat_status=N'可售', lock_time=NULL "
              + "WHERE id=? AND seat_status=N'已售'")) {
            ps.setInt(1, oldSeatId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("原座位状态异常，改签失败");
            }
        }
    }

    /** 旧班次已售 -1，新班次已售 +1。 */
    private void adjustSoldSeats(Connection conn, int oldScheduleId, int newScheduleId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE schedule SET sold_seats=sold_seats-1 "
              + "WHERE id=? AND sold_seats>0")) {
            ps.setInt(1, oldScheduleId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("原班次已售数异常，改签失败");
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE schedule SET sold_seats=sold_seats+1 "
              + "WHERE id=? AND sold_seats<total_seats")) {
            ps.setInt(1, newScheduleId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("目标班次余票不足，改签失败");
            }
        }
    }

    /** 船票改绑新班次/座位/价格，订单同步班次和总价。 */
    private void rebindTicketAndOrder(Connection conn, int orderId, int newScheduleId,
            int newSeatId, BigDecimal newPrice) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ticket SET schedule_id=?, seat_id=?, price=?, qr_code=? "
              + "WHERE order_id=? AND status=N'已支付'")) {
            ps.setInt(1, newScheduleId);
            ps.setInt(2, newSeatId);
            ps.setBigDecimal(3, newPrice);
            ps.setString(4, "QR-" + orderId + "-" + newSeatId);
            ps.setInt(5, orderId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("船票状态已变化，改签失败");
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE [order] SET schedule_id=?, total_amount=? "
              + "WHERE id=? AND status=N'已支付'")) {
            ps.setInt(1, newScheduleId);
            ps.setBigDecimal(2, newPrice);
            ps.setInt(3, orderId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("订单状态已变化，改签失败");
            }
        }
    }

    /** 差价真实收退：写资金流水，返回页面提示片段。 */
    private String settlePriceDiff(Connection conn, int orderId, BigDecimal diff) throws SQLException {
        String orderNo = findOrderNo(conn, orderId);
        if (diff.signum() > 0) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO payment_log(order_id,order_no,type,amount,direction,remark) "
                  + "VALUES (?,?,N'改签补款',?,'in',N'改签至更贵班次补差价')")) {
                ps.setInt(1, orderId);
                ps.setString(2, orderNo);
                ps.setBigDecimal(3, diff);
                ps.executeUpdate();
            }
            return "，已补差价 ￥" + diff.toPlainString();
        }
        if (diff.signum() < 0) {
            BigDecimal back = diff.abs();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO payment_log(order_id,order_no,type,amount,direction,remark) "
                  + "VALUES (?,?,N'改签退款',?,'out',N'改签至更便宜班次退差价')")) {
                ps.setInt(1, orderId);
                ps.setString(2, orderNo);
                ps.setBigDecimal(3, back);
                ps.executeUpdate();
            }
            return "，已退差价 ￥" + back.toPlainString() + "（3个工作日内到账）";
        }
        return "，票价相同无需补退差价";
    }

    private String findOrderNo(Connection conn, int orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT order_no FROM [order] WHERE id=?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("order_no") : null;
            }
        }
    }

    private String limitText(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** 乘客查询自己的订单（联表带出班次信息） */
    public List<Order> findByPassenger(int passengerId) {
        List<Order> list = new ArrayList<>();
        String sql = "SELECT o.*, s.depart_time, r.route_name, sh.ship_name, "
                   + "CASE WHEN o.status=N'待支付' THEN "
                   + "ISNULL(DATEDIFF(SECOND, GETDATE(), DATEADD(MINUTE,5,"
                   + "(SELECT MIN(se2.lock_time) FROM ticket t2 JOIN seat se2 ON se2.id=t2.seat_id "
                   + " WHERE t2.order_id=o.id))),0) ELSE 0 END AS lock_remain_seconds "
                   + "FROM [order] o "
                   + "JOIN schedule s ON s.id=o.schedule_id "
                   + "JOIN route r ON r.id=s.route_id "
                   + "JOIN ship sh ON sh.id=s.ship_id "
                   + "WHERE o.passenger_id=? ORDER BY o.id DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, passengerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            attachTickets(conn, list);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 售票员：查询本人经办的订单（seller_id = 当前登录用户） */
    public List<Order> findBySeller(int sellerId) {
        List<Order> list = new ArrayList<Order>();
        String sql = "SELECT o.*, s.depart_time, r.route_name, sh.ship_name, "
                   + "CASE WHEN o.status=N'待支付' THEN "
                   + "ISNULL(DATEDIFF(SECOND, GETDATE(), DATEADD(MINUTE,5,"
                   + "(SELECT MIN(se2.lock_time) FROM ticket t2 JOIN seat se2 ON se2.id=t2.seat_id "
                   + " WHERE t2.order_id=o.id))),0) ELSE 0 END AS lock_remain_seconds "
                   + "FROM [order] o "
                   + "JOIN schedule s ON s.id=o.schedule_id "
                   + "JOIN route r ON r.id=s.route_id "
                   + "JOIN ship sh ON sh.id=s.ship_id "
                   + "WHERE o.seller_id=? ORDER BY o.id DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            attachTickets(conn, list);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 管理员：订单总数（筛选条件与列表完全一致，供分页用） */
    public int countForAdmin(String status, String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM [order] o "
              + "LEFT JOIN [user] u ON u.id=o.passenger_id WHERE 1=1 ");
        appendAdminOrderFilter(sql, status, keyword);
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindAdminOrderFilter(ps, status, keyword);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /** 管理员：分页查询订单（按状态 / 关键字筛选），联表带出班次、座位、乘客 */
    public List<Order> findForAdmin(String status, String keyword, int page, int pageSize) {
        List<Order> list = new ArrayList<Order>();
        StringBuilder sql = new StringBuilder(
                "SELECT o.*, s.depart_time, r.route_name, sh.ship_name "
              + "FROM [order] o "
              + "JOIN schedule s ON s.id=o.schedule_id "
              + "JOIN route r ON r.id=s.route_id "
              + "JOIN ship sh ON sh.id=s.ship_id "
              + "LEFT JOIN [user] u ON u.id=o.passenger_id WHERE 1=1 ");
        appendAdminOrderFilter(sql, status, keyword);
        sql.append("ORDER BY o.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = bindAdminOrderFilter(ps, status, keyword);
            ps.setInt(idx++, (page - 1) * pageSize);
            ps.setInt(idx, pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            attachTickets(conn, list);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 管理员：按当前筛选导出全部订单（不分页），供 Excel 导出使用。 */
    public List<Order> findAllForAdmin(String status, String keyword) {
        List<Order> list = new ArrayList<Order>();
        StringBuilder sql = new StringBuilder(
                "SELECT o.*, s.depart_time, r.route_name, sh.ship_name "
              + "FROM [order] o "
              + "JOIN schedule s ON s.id=o.schedule_id "
              + "JOIN route r ON r.id=s.route_id "
              + "JOIN ship sh ON sh.id=s.ship_id "
              + "LEFT JOIN [user] u ON u.id=o.passenger_id WHERE 1=1 ");
        appendAdminOrderFilter(sql, status, keyword);
        sql.append("ORDER BY o.id DESC");
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindAdminOrderFilter(ps, status, keyword);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
            attachTickets(conn, list);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private void appendAdminOrderFilter(StringBuilder sql, String status, String keyword) {
        if (status != null && !status.trim().isEmpty() && !"all".equals(status)) {
            sql.append("AND o.status = ? ");
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append("AND (o.order_no LIKE ? OR u.username LIKE ? "
                     + "OR EXISTS (SELECT 1 FROM ticket t WHERE t.order_id=o.id "
                     + "AND (t.passenger_name LIKE ? OR t.passenger_phone LIKE ?))) ");
        }
    }

    private int bindAdminOrderFilter(PreparedStatement ps, String status, String keyword)
            throws SQLException {
        int idx = 1;
        if (status != null && !status.trim().isEmpty() && !"all".equals(status)) {
            ps.setString(idx++, status.trim());
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = "%" + keyword.trim() + "%";
            ps.setString(idx++, kw);
            ps.setString(idx++, kw);
            ps.setString(idx++, kw);
            ps.setString(idx++, kw);
        }
        return idx;
    }

    /** 按订单号查询（检票用，阶段5） */
    public Order findByOrderNo(String orderNo) {
        String sql = "SELECT o.*, s.depart_time, r.route_name, sh.ship_name "
                   + "FROM [order] o JOIN schedule s ON s.id=o.schedule_id "
                   + "JOIN route r ON r.id=s.route_id JOIN ship sh ON sh.id=s.ship_id "
                   + "WHERE o.order_no=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderNo);
            Order order = null;
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    order = map(rs);
                }
            }
            if (order != null) attachTickets(conn, java.util.Collections.singletonList(order));
            return order;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Order findById(int id) {
        String sql = "SELECT o.*, s.depart_time, r.route_name, sh.ship_name "
                   + "FROM [order] o JOIN schedule s ON s.id=o.schedule_id "
                   + "JOIN route r ON r.id=s.route_id JOIN ship sh ON sh.id=s.ship_id "
                   + "WHERE o.id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            Order order = null;
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    order = map(rs);
                }
            }
            if (order != null) attachTickets(conn, java.util.Collections.singletonList(order));
            return order;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 为订单列表批量装载船票明细，并把座位号、乘客姓名聚合成列表页可读文本。
     * 这样多票订单只占一行，不会被 JOIN ticket 放大成多行。
     */
    private void attachTickets(Connection conn, List<Order> orders) throws SQLException {
        if (orders == null || orders.isEmpty()) return;
        List<Integer> ids = new ArrayList<Integer>();
        Map<Integer, Order> orderMap = new HashMap<Integer, Order>();
        for (Order order : orders) {
            ids.add(order.getId());
            orderMap.put(order.getId(), order);
        }
        String sql = "SELECT t.*, se.seat_no FROM ticket t "
                   + "LEFT JOIN seat se ON se.id=t.seat_id "
                   + "WHERE t.order_id IN (" + placeholders(ids.size()) + ") "
                   + "ORDER BY t.order_id, t.id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order order = orderMap.get(rs.getInt("order_id"));
                    if (order == null) continue;
                    Ticket ticket = mapTicket(rs);
                    order.getTickets().add(ticket);
                }
            }
        }
        for (Order order : orders) {
            StringBuilder seatText = new StringBuilder();
            StringBuilder nameText = new StringBuilder();
            for (Ticket ticket : order.getTickets()) {
                if (seatText.length() > 0) seatText.append("、");
                if (nameText.length() > 0) nameText.append("、");
                seatText.append(ticket.getSeatNo() == null ? "-" : ticket.getSeatNo());
                nameText.append(ticket.getPassengerName() == null ? "-" : ticket.getPassengerName());
            }
            order.setSeatNo(seatText.length() == 0 ? null : seatText.toString());
            order.setPassengerName(nameText.length() == 0 ? null : nameText.toString());
        }
    }

    /** 查询订单内全部船票，供“我的船票/检票”等需要逐票展示的页面使用。 */
    public List<Ticket> findTicketsByOrderId(int orderId) {
        List<Ticket> tickets = new ArrayList<Ticket>();
        String sql = "SELECT t.*, se.seat_no FROM ticket t "
                   + "LEFT JOIN seat se ON se.id=t.seat_id "
                   + "WHERE t.order_id=? ORDER BY t.id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tickets.add(mapTicket(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tickets;
    }

    /** 乘客“我的船票”：只返回当前账号名下仍有效的票（已支付/已检票）。 */
    public List<Ticket> findValidTicketsByPassenger(int passengerId) {
        List<Ticket> tickets = new ArrayList<Ticket>();
        String sql = "SELECT t.*, se.seat_no, o.order_no, r.route_name, r.start_port, r.end_port, "
                   + "sh.ship_name, s.depart_time, s.status AS schedule_status "
                   + "FROM ticket t "
                   + "JOIN [order] o ON o.id=t.order_id "
                   + "JOIN schedule s ON s.id=t.schedule_id "
                   + "JOIN route r ON r.id=s.route_id "
                   + "JOIN ship sh ON sh.id=s.ship_id "
                   + "LEFT JOIN seat se ON se.id=t.seat_id "
                   + "WHERE o.passenger_id=? AND t.status IN (N'已支付',N'已检票') "
                   + "ORDER BY s.depart_time, t.id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, passengerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tickets.add(mapTicket(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tickets;
    }

    private Ticket mapTicket(ResultSet rs) throws SQLException {
        Ticket t = new Ticket();
        t.setId(rs.getInt("id"));
        t.setOrderId(rs.getInt("order_id"));
        t.setScheduleId(rs.getInt("schedule_id"));
        t.setSeatId(rs.getInt("seat_id"));
        t.setPassengerName(rs.getString("passenger_name"));
        t.setIdCard(rs.getString("id_card"));
        t.setPassengerPhone(rs.getString("passenger_phone"));
        t.setPrice(rs.getBigDecimal("price"));
        t.setStatus(rs.getString("status"));
        t.setQrCode(rs.getString("qr_code"));
        t.setCreateTime(rs.getTimestamp("create_time"));
        t.setSeatNo(rs.getString("seat_no"));
        try { t.setOrderNo(rs.getString("order_no")); } catch (SQLException ignored) { }
        try { t.setRouteName(rs.getString("route_name")); } catch (SQLException ignored) { }
        try { t.setStartPort(rs.getString("start_port")); } catch (SQLException ignored) { }
        try { t.setEndPort(rs.getString("end_port")); } catch (SQLException ignored) { }
        try { t.setShipName(rs.getString("ship_name")); } catch (SQLException ignored) { }
        try { t.setDepartTime(rs.getTimestamp("depart_time")); } catch (SQLException ignored) { }
        try { t.setScheduleStatus(rs.getString("schedule_status")); } catch (SQLException ignored) { }
        return t;
    }

    /** 查询某班次的可售座位（供选座页显示） */
    public List<String> findAvailableSeats(int scheduleId) {
        List<String> seats = new ArrayList<>();
        String sql = "SELECT seat_no FROM seat WHERE schedule_id=? AND seat_status=N'可售' ORDER BY seat_no";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, scheduleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) seats.add(rs.getString("seat_no"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return seats;
    }

    /** 查询某班次全部座位及状态，按排号、座号自然排序（供可视化选座图使用） */
    public List<SeatCell> findSeatMap(int scheduleId) {
        List<SeatCell> cells = new ArrayList<>();
        String sql = "SELECT seat_no, seat_status FROM seat WHERE schedule_id=? "
                   + "ORDER BY LEFT(seat_no,1), "
                   + "TRY_CAST(SUBSTRING(seat_no,2,LEN(seat_no)) AS INT), seat_no";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, scheduleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cells.add(new SeatCell(rs.getString("seat_no"), rs.getString("seat_status")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return cells;
    }

    /** 根据班次+座位号拿 seat_id（下单时用） */
    public int findSeatId(int scheduleId, String seatNo) {
        String sql = "SELECT id FROM seat WHERE schedule_id=? AND seat_no=? AND seat_status=N'可售'";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, scheduleId);
            ps.setString(2, seatNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /** 查询某订单的资金流水（支付/退款/补差价），供订单详情展示 */
    public List<String> findPaymentLogs(int orderId) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT type, amount, direction, "
                   + "CONVERT(varchar(16), create_time, 120) AS t, remark "
                   + "FROM payment_log WHERE order_id=? ORDER BY id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String dir = "in".equals(rs.getString("direction")) ? "支付" : "退回";
                    String amt = rs.getBigDecimal("amount").toPlainString();
                    list.add(rs.getString("t") + " " + rs.getString("type") + " " + dir
                           + " ￥" + amt + "　" + (rs.getString("remark") == null ? "" : rs.getString("remark")));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 查询某订单的改签记录（前后班次/座位/差价对比），供订单页醒目展示 */
    public List<String> findChangeInfo(int orderId) {
        List<String> list = new ArrayList<>();
        String sql = "SELECT cl.old_schedule_id, cl.new_schedule_id, cl.price_diff, "
                   + "os.seat_no AS old_seat, ns.seat_no AS new_seat, "
                   + "CONVERT(varchar(16), s1.depart_time, 120) AS old_time, "
                   + "CONVERT(varchar(16), s2.depart_time, 120) AS new_time, "
                   + "cl.reason, cl.change_time "
                   + "FROM change_log cl "
                   + "JOIN seat os ON os.id=cl.old_seat_id "
                   + "JOIN seat ns ON ns.id=cl.new_seat_id "
                   + "JOIN schedule s1 ON s1.id=cl.old_schedule_id "
                   + "JOIN schedule s2 ON s2.id=cl.new_schedule_id "
                   + "WHERE cl.order_id=? ORDER BY cl.id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String diff = rs.getBigDecimal("price_diff") == null ? "0"
                                : rs.getBigDecimal("price_diff").toPlainString();
                    String txt = rs.getString("old_time") + " " + rs.getString("old_seat") + "座"
                               + " → " + rs.getString("new_time") + " " + rs.getString("new_seat") + "座"
                               + "（差价 ￥" + diff + "）";
                    list.add(txt);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 批量查询资金流水，避免订单列表逐单查询（N+1）。 */
    public Map<Integer, List<String>> findPaymentLogsByOrderIds(List<Integer> orderIds) {
        Map<Integer, List<String>> result = new HashMap<Integer, List<String>>();
        if (orderIds == null || orderIds.isEmpty()) return result;
        String sql = "SELECT order_id, type, amount, direction, "
                   + "CONVERT(varchar(16), create_time, 120) AS t, remark "
                   + "FROM payment_log WHERE order_id IN (" + placeholders(orderIds.size()) + ") "
                   + "ORDER BY order_id, id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < orderIds.size(); i++) {
                ps.setInt(i + 1, orderIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int orderId = rs.getInt("order_id");
                    String dir = "in".equals(rs.getString("direction")) ? "支付" : "退回";
                    String amt = rs.getBigDecimal("amount").toPlainString();
                    String text = rs.getString("t") + " " + rs.getString("type") + " " + dir
                                + " ￥" + amt + "　"
                                + (rs.getString("remark") == null ? "" : rs.getString("remark"));
                    List<String> list = result.get(orderId);
                    if (list == null) {
                        list = new ArrayList<String>();
                        result.put(orderId, list);
                    }
                    list.add(text);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    /** 批量查询改签记录，避免订单列表逐单查询（N+1）。 */
    public Map<Integer, List<String>> findChangeInfoByOrderIds(List<Integer> orderIds) {
        Map<Integer, List<String>> result = new HashMap<Integer, List<String>>();
        if (orderIds == null || orderIds.isEmpty()) return result;
        String sql = "SELECT cl.order_id, cl.price_diff, "
                   + "os.seat_no AS old_seat, ns.seat_no AS new_seat, "
                   + "CONVERT(varchar(16), s1.depart_time, 120) AS old_time, "
                   + "CONVERT(varchar(16), s2.depart_time, 120) AS new_time "
                   + "FROM change_log cl "
                   + "JOIN seat os ON os.id=cl.old_seat_id "
                   + "JOIN seat ns ON ns.id=cl.new_seat_id "
                   + "JOIN schedule s1 ON s1.id=cl.old_schedule_id "
                   + "JOIN schedule s2 ON s2.id=cl.new_schedule_id "
                   + "WHERE cl.order_id IN (" + placeholders(orderIds.size()) + ") "
                   + "ORDER BY cl.order_id, cl.id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < orderIds.size(); i++) {
                ps.setInt(i + 1, orderIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int orderId = rs.getInt("order_id");
                    String diff = rs.getBigDecimal("price_diff") == null ? "0"
                                : rs.getBigDecimal("price_diff").toPlainString();
                    String text = rs.getString("old_time") + " " + rs.getString("old_seat") + "座"
                                + " → " + rs.getString("new_time") + " " + rs.getString("new_seat") + "座"
                                + "（差价 ￥" + diff + "）";
                    List<String> list = result.get(orderId);
                    if (list == null) {
                        list = new ArrayList<String>();
                        result.put(orderId, list);
                    }
                    list.add(text);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    private String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        return sb.toString();
    }

    private Order map(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setId(rs.getInt("id"));
        o.setOrderNo(rs.getString("order_no"));
        o.setScheduleId(rs.getInt("schedule_id"));
        Object pid = rs.getObject("passenger_id");
        o.setPassengerId(pid == null ? null : ((Number) pid).intValue());
        Object sid = rs.getObject("seller_id");
        o.setSellerId(sid == null ? null : ((Number) sid).intValue());
        o.setTotalAmount(rs.getBigDecimal("total_amount"));
        o.setStatus(rs.getString("status"));
        o.setCreateTime(rs.getTimestamp("create_time"));
        o.setPayTime(rs.getTimestamp("pay_time"));
        try { o.setCancelTime(rs.getTimestamp("cancel_time")); } catch (Exception ignored) { }
        try { o.setRefundTime(rs.getTimestamp("refund_time")); } catch (Exception ignored) { }
        try { o.setRefundEta(rs.getTimestamp("refund_eta")); } catch (Exception ignored) { }
        o.setRefundAmount(rs.getBigDecimal("refund_amount"));
        o.setRefundReason(rs.getString("refund_reason"));
        try { o.setDepartTime(rs.getTimestamp("depart_time")); } catch (Exception ignored) { }
        try { o.setRouteName(rs.getString("route_name")); } catch (Exception ignored) { }
        try { o.setShipName(rs.getString("ship_name")); } catch (Exception ignored) { }
        try { o.setSeatNo(rs.getString("seat_no")); } catch (Exception ignored) { }
        try { o.setPassengerName(rs.getString("passenger_name")); } catch (Exception ignored) { }
        try { o.setLockRemainSeconds(Math.max(0, rs.getInt("lock_remain_seconds"))); } catch (Exception ignored) { }
        return o;
    }
}
