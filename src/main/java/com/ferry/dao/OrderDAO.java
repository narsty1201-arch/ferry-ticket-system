package com.ferry.dao;

import com.ferry.entity.Order;
import com.ferry.entity.Ticket;
import com.ferry.util.DBUtil;

import java.math.BigDecimal;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
                ps.setInt(3, o.getPassengerId());
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
                           + "WHERE t.status=N'待支付' AND s.seat_status=N'锁定' "
                           + "AND s.lock_time < DATEADD(MINUTE,-5,GETDATE())";
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

    /** 支付：订单→已支付、船票→已支付、座位→已售、sold_seats+1，全事务 */
    public void pay(int orderId) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);
            String orderNo = null;

            // 校验订单仍是待支付（防止重复支付）
            String status = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status FROM [order] WITH (UPDLOCK) WHERE id=?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) status = rs.getString("status");
                }
            }
            if (!"待支付".equals(status)) {
                conn.rollback();
                return;
            }
            // 发船后不允许再支付（避免买到已开船的票）
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT s.depart_time FROM [order] o JOIN schedule s ON s.id=o.schedule_id WHERE o.id=?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Timestamp dep = rs.getTimestamp("depart_time");
                        if (dep == null || !dep.after(new Timestamp(System.currentTimeMillis()))) {
                            conn.rollback(); return;
                        }
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT order_no FROM [order] WHERE id=?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) orderNo = rs.getString("order_no"); }
            }

            // 座位：锁定→已售；拿到 seat_id 和 schedule_id
            int seatId = 0, scheduleId = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT seat_id, schedule_id FROM ticket WHERE order_id=?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) { seatId = rs.getInt("seat_id"); scheduleId = rs.getInt("schedule_id"); }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE seat SET seat_status=N'已售', lock_time=NULL WHERE id=?")) {
                ps.setInt(1, seatId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ticket SET status=N'已支付' WHERE order_id=?")) {
                ps.setInt(1, orderId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE [order] SET status=N'已支付', pay_time=GETDATE() WHERE id=?")) {
                ps.setInt(1, orderId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE schedule SET sold_seats=sold_seats+1 WHERE id=?")) {
                ps.setInt(1, scheduleId);
                ps.executeUpdate();
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
                  + "FROM [order] o JOIN ticket t ON t.order_id=o.id "
                  + "JOIN schedule s ON s.id=o.schedule_id WHERE o.id=?")) {
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
            String finalReason = reason == null || reason.trim().isEmpty() ? "乘客主动退票" : reason;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE [order] SET status=N'已退票', refund_amount=?, refund_reason=?, "
                  + "refund_time=GETDATE(), refund_eta=DATEADD(DAY,3,GETDATE()), cancel_time=GETDATE() WHERE id=?")) {
                ps.setBigDecimal(1, refundAmount);
                ps.setString(2, finalReason);
                ps.setInt(3, orderId);
                ps.executeUpdate();
            }
            // 资金流水：退票退款（出站）
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO payment_log(order_id,order_no,type,amount,direction,remark) "
                  + "SELECT id, order_no, N'退票退款', ?, 'out', ? FROM [order] WHERE id=?")) {
                ps.setBigDecimal(1, refundAmount);
                ps.setString(2, finalReason);
                ps.setInt(3, orderId);
                ps.executeUpdate();
            }
            // 船票：已退票
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ticket SET status=N'已退票' WHERE order_id=?")) {
                ps.setInt(1, orderId);
                ps.executeUpdate();
            }
            // 座位：已售→可售
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE seat SET seat_status=N'可售', lock_time=NULL WHERE id=?")) {
                ps.setInt(1, seatId);
                ps.executeUpdate();
            }
            // 班次已售 -1
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE schedule SET sold_seats=CASE WHEN sold_seats>0 THEN sold_seats-1 ELSE 0 END WHERE id=?")) {
                ps.setInt(1, scheduleId);
                ps.executeUpdate();
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
        String sql = "SELECT o.status, s.depart_time AS old_depart, ns.status AS new_status, "
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
                if (od != null && !nd.after(od)) return "只能改签到发船时间更晚的班次";
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
     */
    /**
     * @return null 表示失败；否则返回给页面展示的结果说明（含差价收退情况）
     */
    public String change(int orderId, int newScheduleId, int newSeatId, int operatorId, String reason)
            throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // 1. 查原订单与原票
            int oldScheduleId = 0, oldSeatId = 0;
            BigDecimal oldPrice = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT o.schedule_id, t.seat_id, t.price FROM [order] o "
                  + "JOIN ticket t ON t.order_id=o.id WHERE o.id=? AND o.status=N'已支付'")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return null; }
                    oldScheduleId = rs.getInt("schedule_id");
                    oldSeatId = rs.getInt("seat_id");
                    oldPrice = rs.getBigDecimal("price");
                }
            }

            // 2. UPDLOCK 锁新座位，确认可售（并发安全）
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT seat_status FROM seat WITH (UPDLOCK, HOLDLOCK) WHERE id=? AND schedule_id=?")) {
                ps.setInt(1, newSeatId);
                ps.setInt(2, newScheduleId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !"可售".equals(rs.getString("seat_status"))) {
                        conn.rollback();
                        return null;
                    }
                }
            }
            // 2b. 新班次必须是“售票中”且未发船（对标12306：改签目标必须可乘车）
            String newSchedSql = "SELECT status, price, depart_time, arrive_time FROM schedule "
                               + "WITH (UPDLOCK, HOLDLOCK) WHERE id=? AND is_deleted=0";
            BigDecimal newPrice;
            try (PreparedStatement ps = conn.prepareStatement(newSchedSql)) {
                ps.setInt(1, newScheduleId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return null; }
                    String st = rs.getString("status");
                    Timestamp dep = rs.getTimestamp("depart_time");
                    if (!"售票中".equals(st) || dep == null || !dep.after(new Timestamp(System.currentTimeMillis()))) {
                        conn.rollback();
                        return null;
                    }
                    newPrice = rs.getBigDecimal("price");
                }
            }

            // 2c. 改签后不能与其他有效票时段重叠（排除本订单自己的票）
            String overlapSql = "SELECT COUNT(*) FROM ticket t WITH (UPDLOCK, HOLDLOCK) "
                              + "JOIN schedule s ON s.id=t.schedule_id "
                              + "JOIN schedule ns ON ns.id=? "
                              + "WHERE t.id_card=? AND t.order_id<>? "
                              + "AND t.status IN (N'待支付',N'已支付',N'已检票') "
                              + "AND s.depart_time < ns.arrive_time AND s.arrive_time > ns.depart_time";
            String idCard;
            try (PreparedStatement ps = conn.prepareStatement("SELECT id_card FROM ticket WHERE order_id=?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    idCard = rs.next() ? rs.getString("id_card") : null;
                }
            }
            if (idCard != null) {
                try (PreparedStatement ps = conn.prepareStatement(overlapSql)) {
                    ps.setInt(1, newScheduleId);
                    ps.setString(2, idCard);
                    ps.setInt(3, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            conn.rollback();
                            return null;
                        }
                    }
                }
            }
            BigDecimal diff = newPrice.subtract(oldPrice);   // 正数补差价、负数退款

            // 3. 新座位→已售，旧座位→可售
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE seat SET seat_status=N'已售', lock_time=NULL WHERE id=?")) {
                ps.setInt(1, newSeatId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE seat SET seat_status=N'可售', lock_time=NULL WHERE id=?")) {
                ps.setInt(1, oldSeatId);
                ps.executeUpdate();
            }
            // 4. 班次已售调整
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE schedule SET sold_seats=sold_seats-1 WHERE id=?")) {
                ps.setInt(1, oldScheduleId); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE schedule SET sold_seats=sold_seats+1 WHERE id=?")) {
                ps.setInt(1, newScheduleId); ps.executeUpdate();
            }
            // 5. 船票改绑新班次/座位/价格，订单更新班次和总价
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ticket SET schedule_id=?, seat_id=?, price=?, qr_code=? WHERE order_id=?")) {
                ps.setInt(1, newScheduleId);
                ps.setInt(2, newSeatId);
                ps.setBigDecimal(3, newPrice);
                ps.setString(4, "QR-" + orderId + "-" + newSeatId);
                ps.setInt(5, orderId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE [order] SET schedule_id=?, total_amount=? WHERE id=?")) {
                ps.setInt(1, newScheduleId);
                ps.setBigDecimal(2, newPrice);
                ps.setInt(3, orderId);
                ps.executeUpdate();
            }
            // 6. 写改签日志（任务书要求）
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO change_log(order_id,old_schedule_id,new_schedule_id,old_seat_id,new_seat_id,price_diff,operator_id,reason) "
                  + "VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setInt(1, orderId);
                ps.setInt(2, oldScheduleId);
                ps.setInt(3, newScheduleId);
                ps.setInt(4, oldSeatId);
                ps.setInt(5, newSeatId);
                ps.setBigDecimal(6, diff);
                ps.setInt(7, operatorId);
                ps.setString(8, reason == null ? "乘客改签" : reason);
                ps.executeUpdate();
            }

            // 7. 差价真实收退：写资金流水（正数=补款 in，负数=退款 out）
            String orderNo = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT order_no FROM [order] WHERE id=?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) orderNo = rs.getString("order_no"); }
            }
            String settle;
            if (diff.signum() > 0) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payment_log(order_id,order_no,type,amount,direction,remark) "
                      + "VALUES (?,?,N'改签补款',?,'in',N'改签至更贵班次补差价')")) {
                    ps.setInt(1, orderId); ps.setString(2, orderNo);
                    ps.setBigDecimal(3, diff); ps.executeUpdate();
                }
                settle = "，已补差价 ￥" + diff.toPlainString();
            } else if (diff.signum() < 0) {
                BigDecimal back = diff.abs();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO payment_log(order_id,order_no,type,amount,direction,remark) "
                      + "VALUES (?,?,N'改签退款',?,'out',N'改签至更便宜班次退差价')")) {
                    ps.setInt(1, orderId); ps.setString(2, orderNo);
                    ps.setBigDecimal(3, back); ps.executeUpdate();
                }
                settle = "，已退差价 ￥" + back.toPlainString() + "（3个工作日内到账）";
            } else {
                settle = "，票价相同无需补退差价";
            }

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

    /** 乘客查询自己的订单（联表带出班次信息） */
    public List<Order> findByPassenger(int passengerId) {
        List<Order> list = new ArrayList<>();
        String sql = "SELECT o.*, s.depart_time, r.route_name, sh.ship_name, se.seat_no, t.passenger_name "
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
        String sql = "SELECT o.*, s.depart_time, r.route_name, sh.ship_name, se.seat_no, t.passenger_name "
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

    /** 管理员：查询全部订单（按状态 / 关键字筛选），联表带出班次、座位、乘客 */
    public List<Order> findAllForAdmin(String status, String keyword) {
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
        boolean hasStatus = status != null && !status.trim().isEmpty() && !"all".equals(status);
        boolean hasKw = keyword != null && !keyword.trim().isEmpty();
        if (hasStatus) sql.append("AND o.status = ? ");
        if (hasKw) sql.append("AND (o.order_no LIKE ? OR t.passenger_name LIKE ? "
                            + "OR u.username LIKE ? OR t.passenger_phone LIKE ?) ");
        sql.append("ORDER BY o.id DESC");
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (hasStatus) ps.setString(idx++, status.trim());
            if (hasKw) {
                String kw = "%" + keyword.trim() + "%";
                ps.setString(idx++, kw);
                ps.setString(idx++, kw);
                ps.setString(idx++, kw);
                ps.setString(idx++, kw);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
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

    private Order map(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setId(rs.getInt("id"));
        o.setOrderNo(rs.getString("order_no"));
        o.setScheduleId(rs.getInt("schedule_id"));
        o.setPassengerId(rs.getInt("passenger_id"));
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
        return o;
    }
}
