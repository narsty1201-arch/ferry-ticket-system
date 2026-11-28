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

    /**
     * 锁座下单（并发核心）。
     * 关键：SELECT ... WITH (UPDLOCK, HOLDLOCK) 在事务内锁住座位行，
     * 两个并发请求同时抢一座时，第二个会阻塞直到第一个提交，然后看到 seat_status 已是“锁定”而失败。
     */
    public int lockAndCreate(Order o, Ticket t) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

            // 1. 用 UPDLOCK 锁住目标座位行，并确认它是“可售”
            String lockSql = "SELECT id, seat_status FROM seat WITH (UPDLOCK, HOLDLOCK) WHERE id=? AND schedule_id=?";
            Integer seatId = null;
            try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
                ps.setInt(1, t.getSeatId());
                ps.setInt(2, o.getScheduleId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && "可售".equals(rs.getString("seat_status"))) {
                        seatId = rs.getInt("id");
                    }
                }
            }
            if (seatId == null) {
                conn.rollback();
                return -1;   // -1 表示座位已被占/不存在
            }

            // 2. 用同一个 UPDLOCK 校验“一人一班次一票”（避免并发下重复购票）
            //    对标 12306：已退票/已取消不再占用名额，退票后允许重新购买同一班次
            String dupSql = "SELECT COUNT(*) FROM ticket WITH (UPDLOCK, HOLDLOCK) "
                          + "WHERE id_card=? AND schedule_id=? "
                          + "AND status IN (N'待支付',N'已支付',N'已检票')";
            try (PreparedStatement ps = conn.prepareStatement(dupSql)) {
                ps.setString(1, t.getIdCard());
                ps.setInt(2, o.getScheduleId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        conn.rollback();
                        return -2;   // -2 表示该证件已买过此班次
                    }
                }
            }

            // 2b. 班次必须处于“售票中”且尚未发船（对标12306：开车后停止售票）
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

            // 2c. 同一证件不能持有“时段重叠”的多张有效票（对标12306：同一时间不能有两张票）
            //     有效票 = 待支付/已支付/已检票；重叠条件：旧班次发船 < 新班次到达 且 旧班次到达 > 新班次发船
            String overlapSql = "SELECT COUNT(*) FROM ticket t WITH (UPDLOCK, HOLDLOCK) "
                              + "JOIN schedule s ON s.id=t.schedule_id "
                              + "JOIN schedule ns ON ns.id=? "
                              + "WHERE t.id_card=? AND t.status IN (N'待支付',N'已支付',N'已检票') "
                              + "AND s.depart_time < ns.arrive_time AND s.arrive_time > ns.depart_time";
            try (PreparedStatement ps = conn.prepareStatement(overlapSql)) {
                ps.setInt(1, o.getScheduleId());
                ps.setString(2, t.getIdCard());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        conn.rollback();
                        return -3;   // -3 表示该证件已有同一时段的船票
                    }
                }
            }

            // 3. 座位改为“锁定”，记录锁定时间（5分钟倒计时起点）
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE seat SET seat_status=N'锁定', lock_time=GETDATE() WHERE id=?")) {
                ps.setInt(1, seatId);
                ps.executeUpdate();
            }

            // 4. 生成订单号：FD + 时间戳 + 3位随机数
            String orderNo = "FD" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                           + String.format("%03d", (int)(Math.random() * 1000));
            o.setOrderNo(orderNo);

            // 5. 插入订单（待支付）
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
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ticket(order_id,schedule_id,seat_id,passenger_name,id_card,passenger_phone,price,status,qr_code) "
                  + "VALUES (?,?,?,?,?,?,?,N'待支付',?)")) {
                ps.setInt(1, orderId);
                ps.setInt(2, o.getScheduleId());
                ps.setInt(3, seatId);
                ps.setString(4, t.getPassengerName());
                ps.setString(5, t.getIdCard());
                ps.setString(6, t.getPassengerPhone());
                ps.setBigDecimal(7, t.getPrice());
                ps.setString(8, "QR-" + orderNo + "-" + seatId);
                ps.executeUpdate();
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
            String ticketStatus;
            String seatStatus;
            int seatId;
            String sql = "SELECT o.status AS order_status, t.status AS ticket_status, "
                       + "t.seat_id, s.seat_status "
                       + "FROM [order] o WITH (UPDLOCK, HOLDLOCK) "
                       + "JOIN ticket t WITH (UPDLOCK, HOLDLOCK) ON t.order_id=o.id "
                       + "JOIN seat s WITH (UPDLOCK, HOLDLOCK) ON s.id=t.seat_id "
                       + "WHERE o.id=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return "订单不存在或数据异常";
                    }
                    orderStatus = rs.getString("order_status");
                    ticketStatus = rs.getString("ticket_status");
                    seatStatus = rs.getString("seat_status");
                    seatId = rs.getInt("seat_id");
                }
            }
            if (!"待支付".equals(orderStatus)) {
                conn.rollback();
                return "该订单已支付或已取消，不能取消";
            }
            if (!"待支付".equals(ticketStatus)) {
                conn.rollback();
                return "船票状态已变化，不能取消";
            }
            if ("已售".equals(seatStatus)) {
                conn.rollback();
                return "座位状态异常，不能取消";
            }
            if ("锁定".equals(seatStatus)) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE seat SET seat_status=N'可售', lock_time=NULL "
                      + "WHERE id=? AND seat_status=N'锁定'")) {
                    ps.setInt(1, seatId);
                    if (ps.executeUpdate() != 1) {
                        conn.rollback();
                        return "座位状态已变化，取消失败";
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ticket SET status=N'已取消' WHERE order_id=? AND status=N'待支付'")) {
                ps.setInt(1, orderId);
                if (ps.executeUpdate() != 1) {
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

            // 锁船票与座位，确认座位仍是本订单的“锁定”状态且未超过5分钟
            int seatId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT t.seat_id, t.status AS ticket_status, s.seat_status, "
                  + "CASE WHEN s.lock_time IS NULL OR s.lock_time < DATEADD(MINUTE,-5,GETDATE()) "
                  + "THEN 1 ELSE 0 END AS lock_expired "
                  + "FROM ticket t WITH (UPDLOCK, HOLDLOCK) "
                  + "JOIN seat s WITH (UPDLOCK, HOLDLOCK) ON s.id=t.seat_id "
                  + "WHERE t.order_id=?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return "订单船票数据异常";
                    }
                    if (!"待支付".equals(rs.getString("ticket_status"))) {
                        conn.rollback();
                        return "船票状态异常，不能支付";
                    }
                    if (!"锁定".equals(rs.getString("seat_status"))) {
                        conn.rollback();
                        return "座位已释放，请重新下单";
                    }
                    if (rs.getInt("lock_expired") == 1) {
                        conn.rollback();
                        return "支付超时，座位已释放，请重新选座";
                    }
                    seatId = rs.getInt("seat_id");
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE seat SET seat_status=N'已售', lock_time=NULL "
                  + "WHERE id=? AND schedule_id=? AND seat_status=N'锁定'")) {
                ps.setInt(1, seatId);
                ps.setInt(2, scheduleId);
                if (ps.executeUpdate() != 1) {
                    conn.rollback();
                    return "座位状态已变化，支付失败";
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ticket SET status=N'已支付' WHERE order_id=? AND status=N'待支付'")) {
                ps.setInt(1, orderId);
                if (ps.executeUpdate() != 1) {
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
                    "UPDATE schedule SET sold_seats=sold_seats+1 "
                  + "WHERE id=? AND sold_seats<total_seats")) {
                ps.setInt(1, scheduleId);
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
     * 对标 12306：班次发船后不再办理退票（改签仍允许，按用户要求保留）。
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
                    return "班次已发船，不能退票（12306 规则：开车后不办理退票）";
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
     * 手续费规则：>24h 免手续费；2~24h 收10%；<2h 收20%。
     * 发船后禁止退票（与 checkRefund 双重保险）。
     */
    public BigDecimal refund(int orderId, String reason) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // 查询订单金额、班次发船时间、座位
            BigDecimal amount = null;
            Timestamp depart = null;
            int seatId = 0, scheduleId = 0;
            String status;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT o.total_amount, o.status, s.depart_time, t.seat_id, t.schedule_id "
                  + "FROM [order] o WITH (UPDLOCK, HOLDLOCK) "
                  + "JOIN ticket t WITH (UPDLOCK, HOLDLOCK) ON t.order_id=o.id "
                  + "JOIN schedule s WITH (UPDLOCK, HOLDLOCK) ON s.id=o.schedule_id "
                  + "WHERE o.id=?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        amount = rs.getBigDecimal("total_amount");
                        status = rs.getString("status");
                        depart = rs.getTimestamp("depart_time");
                        seatId = rs.getInt("seat_id");
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

            // 手续费计算
            long hours = (depart.getTime() - System.currentTimeMillis()) / (1000L * 3600);
            BigDecimal rate = hours > 24 ? BigDecimal.ZERO
                            : (hours >= 2 ? new BigDecimal("0.10") : new BigDecimal("0.20"));
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
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("船票状态已变化，退票失败");
                }
            }
            // 座位：已售→可售
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE seat SET seat_status=N'可售', lock_time=NULL "
                  + "WHERE id=? AND seat_status=N'已售'")) {
                ps.setInt(1, seatId);
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("座位状态异常，退票失败");
                }
            }
            // 班次已售 -1
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE schedule SET sold_seats=sold_seats-1 "
                  + "WHERE id=? AND sold_seats>0")) {
                ps.setInt(1, scheduleId);
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
     */
    public String checkChange(int orderId, int newScheduleId) {
        String sql = "SELECT o.status, o.schedule_id AS old_schedule_id, "
                   + "s.depart_time AS old_depart, ns.status AS new_status, "
                   + "ns.depart_time AS new_depart "
                   + "FROM [order] o JOIN schedule s ON s.id=o.schedule_id "
                   + "JOIN schedule ns ON ns.id=? WHERE o.id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newScheduleId);
            ps.setInt(2, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "订单或目标班次不存在";
                if (!"已支付".equals(rs.getString("status"))) return "只有已支付的订单可以改签";
                Timestamp nd = rs.getTimestamp("new_depart");
                Timestamp od = rs.getTimestamp("old_depart");
                if (!"售票中".equals(rs.getString("new_status")) || nd == null
                        || !nd.after(new Timestamp(System.currentTimeMillis()))) {
                    return "目标班次已发船或停止售票，不能改签";
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
                return new ChangeOrder(rs.getInt("schedule_id"), rs.getInt("seat_id"),
                        rs.getBigDecimal("price"), rs.getTimestamp("depart_time"));
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
        String sql = "SELECT o.*, s.depart_time, r.route_name, sh.ship_name, se.seat_no, t.passenger_name, "
                   + "CASE WHEN o.status=N'待支付' AND se.lock_time IS NOT NULL "
                   + "THEN DATEDIFF(SECOND, GETDATE(), DATEADD(MINUTE,5,se.lock_time)) "
                   + "ELSE 0 END AS lock_remain_seconds "
                   + "FROM [order] o "
                   + "JOIN schedule s ON s.id=o.schedule_id "
                   + "JOIN route r ON r.id=s.route_id "
                   + "JOIN ship sh ON sh.id=s.ship_id "
                   + "JOIN ticket t ON t.order_id=o.id "
                   + "LEFT JOIN seat se ON se.id=t.seat_id "
                   + "WHERE o.passenger_id=? ORDER BY o.id DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, passengerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 售票员：查询本人经办的订单（seller_id = 当前登录用户） */
    public List<Order> findBySeller(int sellerId) {
        List<Order> list = new ArrayList<Order>();
        String sql = "SELECT o.*, s.depart_time, r.route_name, sh.ship_name, se.seat_no, t.passenger_name, "
                   + "CASE WHEN o.status=N'待支付' AND se.lock_time IS NOT NULL "
                   + "THEN DATEDIFF(SECOND, GETDATE(), DATEADD(MINUTE,5,se.lock_time)) "
                   + "ELSE 0 END AS lock_remain_seconds "
                   + "FROM [order] o "
                   + "JOIN schedule s ON s.id=o.schedule_id "
                   + "JOIN route r ON r.id=s.route_id "
                   + "JOIN ship sh ON sh.id=s.ship_id "
                   + "JOIN ticket t ON t.order_id=o.id "
                   + "LEFT JOIN seat se ON se.id=t.seat_id "
                   + "WHERE o.seller_id=? ORDER BY o.id DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 管理员：订单总数（筛选条件与列表完全一致，供分页用） */
    public int countForAdmin(String status, String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(DISTINCT o.id) FROM [order] o "
              + "LEFT JOIN ticket t ON t.order_id=o.id "
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
                "SELECT o.*, s.depart_time, r.route_name, sh.ship_name, se.seat_no, t.passenger_name "
              + "FROM [order] o "
              + "JOIN schedule s ON s.id=o.schedule_id "
              + "JOIN route r ON r.id=s.route_id "
              + "JOIN ship sh ON sh.id=s.ship_id "
              + "LEFT JOIN ticket t ON t.order_id=o.id "
              + "LEFT JOIN seat se ON se.id=t.seat_id "
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
            sql.append("AND (o.order_no LIKE ? OR t.passenger_name LIKE ? "
                     + "OR u.username LIKE ? OR t.passenger_phone LIKE ?) ");
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
        String sql = "SELECT o.*, s.depart_time, r.route_name, sh.ship_name, se.seat_no, t.passenger_name "
                   + "FROM [order] o JOIN schedule s ON s.id=o.schedule_id "
                   + "JOIN route r ON r.id=s.route_id JOIN ship sh ON sh.id=s.ship_id "
                   + "JOIN ticket t ON t.order_id=o.id LEFT JOIN seat se ON se.id=t.seat_id "
                   + "WHERE o.order_no=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Order findById(int id) {
        String sql = "SELECT o.*, s.depart_time, r.route_name, sh.ship_name, se.seat_no, t.passenger_name "
                   + "FROM [order] o JOIN schedule s ON s.id=o.schedule_id "
                   + "JOIN route r ON r.id=s.route_id JOIN ship sh ON sh.id=s.ship_id "
                   + "JOIN ticket t ON t.order_id=o.id LEFT JOIN seat se ON se.id=t.seat_id "
                   + "WHERE o.id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
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
