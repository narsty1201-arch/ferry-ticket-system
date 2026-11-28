package com.ferry.dao;

import com.ferry.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 检票数据访问：校验船票、写检票记录、检票统计 */
public class CheckInDAO {

    /** 检票窗口：发船前 60 分钟开放，发船后 10 分钟关闭。 */
    public static final int CHECK_IN_OPEN_MINUTES = 60;
    public static final int CHECK_IN_CLOSE_MINUTES = 10;

    /**
     * 检票：按订单号校验并检票，全事务。
     * ticketId 为 null 时检该订单全部“已支付”票；指定 ticketId 时只检一张，
     * 支持多票订单逐票检票。全部票检完后订单状态才变为“已检票”。
     * 返回 null 表示成功；否则返回失败原因。
     */
    public String checkIn(String orderNo, int sellerId) {
        return checkIn(orderNo, null, sellerId);
    }

    public String checkIn(String orderNo, Integer ticketId, int sellerId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // 1. 锁订单，并锁住订单内全部船票，防止并发重复检票。
            int orderId;
            String orderStatus;
            String scheduleStatus;
            Timestamp departTime;
            String sql = "SELECT o.id, o.status, s.status AS schedule_status, s.depart_time "
                       + "FROM [order] o WITH (UPDLOCK, HOLDLOCK) "
                       + "JOIN schedule s ON s.id=o.schedule_id "
                       + "WHERE o.order_no=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, orderNo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return "订单号不存在：" + orderNo; }
                    orderId = rs.getInt("id");
                    orderStatus = rs.getString("status");
                    scheduleStatus = rs.getString("schedule_status");
                    departTime = rs.getTimestamp("depart_time");
                }
            }

            // 2. 校验状态
            if ("已退票".equals(orderStatus) || "已取消".equals(orderStatus)) {
                conn.rollback();
                return recordFailureAndReturn(orderId, ticketId, sellerId, "该订单已退票/取消，不能检票");
            }
            if ("待支付".equals(orderStatus)) {
                conn.rollback();
                return recordFailureAndReturn(orderId, ticketId, sellerId, "该订单尚未支付，不能检票");
            }
            if (!"已支付".equals(orderStatus) && !"已检票".equals(orderStatus)) {
                conn.rollback();
                return recordFailureAndReturn(orderId, ticketId, sellerId,
                        "订单状态异常（" + orderStatus + "），不能检票");
            }
            if ("已完成".equals(scheduleStatus) || "已取消".equals(scheduleStatus)
                    || "待发布".equals(scheduleStatus)) {
                conn.rollback();
                return recordFailureAndReturn(orderId, ticketId, sellerId,
                        "该班次状态为“" + scheduleStatus + "”，不能检票");
            }
            if (departTime == null) {
                conn.rollback();
                return recordFailureAndReturn(orderId, ticketId, sellerId, "班次发船时间缺失，不能检票");
            }
            long now = System.currentTimeMillis();
            long openAt = departTime.getTime() - CHECK_IN_OPEN_MINUTES * 60L * 1000L;
            long closeAt = departTime.getTime() + CHECK_IN_CLOSE_MINUTES * 60L * 1000L;
            if (now < openAt) {
                conn.rollback();
                return recordFailureAndReturn(orderId, ticketId, sellerId,
                        "检票尚未开始（发船前 " + CHECK_IN_OPEN_MINUTES + " 分钟开放）");
            }
            if (now > closeAt) {
                conn.rollback();
                return recordFailureAndReturn(orderId, ticketId, sellerId,
                        "检票已结束（发船后 " + CHECK_IN_CLOSE_MINUTES + " 分钟关闭）");
            }

            int checkedCount = 0;
            int waitingCount = 0;
            String ticketSql = "SELECT t.id, t.status FROM ticket t WITH (UPDLOCK, HOLDLOCK) "
                             + "WHERE t.order_id=?"
                             + (ticketId == null ? "" : " AND t.id=?")
                             + " ORDER BY t.id";
            try (PreparedStatement ps = conn.prepareStatement(ticketSql)) {
                ps.setInt(1, orderId);
                if (ticketId != null) ps.setInt(2, ticketId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String status = rs.getString("status");
                        if ("已检票".equals(status)) {
                            checkedCount++;
                            continue;
                        }
                        if (!"已支付".equals(status)) {
                            conn.rollback();
                            return recordFailureAndReturn(orderId, ticketId, sellerId,
                                    "存在不可检票的船票（" + status + "），整单检票已中止");
                        }
                        try (PreparedStatement ins = conn.prepareStatement(
                                "INSERT INTO check_in(ticket_id, seller_id, check_time, status) "
                              + "VALUES (?,?,GETDATE(),N'成功')")) {
                            ins.setInt(1, id);
                            ins.setInt(2, sellerId);
                            ins.executeUpdate();
                        }
                        try (PreparedStatement upd = conn.prepareStatement(
                                "UPDATE ticket SET status=N'已检票' WHERE id=? AND status=N'已支付'")) {
                            upd.setInt(1, id);
                            if (upd.executeUpdate() != 1) {
                                throw new SQLException("船票状态已变化，检票失败");
                            }
                        }
                        waitingCount++;
                    }
                }
            }
            if (ticketId != null && waitingCount == 0 && checkedCount == 0) {
                conn.rollback();
                return "该订单中不存在指定船票";
            }
            if (waitingCount == 0) {
                conn.rollback();
                return recordFailureAndReturn(orderId, ticketId, sellerId,
                        ticketId == null ? "该订单已全部检票，请勿重复检票" : "该船票已检票，请勿重复检票");
            }

            // 只有订单内所有票都已检票时才把订单标为已检票。
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) AS total, "
                  + "SUM(CASE WHEN status=N'已检票' THEN 1 ELSE 0 END) AS checked "
                  + "FROM ticket WHERE order_id=?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt("checked") == rs.getInt("total")) {
                        try (PreparedStatement upd = conn.prepareStatement(
                                "UPDATE [order] SET status=N'已检票' WHERE id=?")) {
                            upd.setInt(1, orderId);
                            upd.executeUpdate();
                        }
                    }
                }
            }
            conn.commit();
            return null;
        } catch (SQLException e) {
            if (conn != null) { try { conn.rollback(); } catch (Exception ignored) {} }
            e.printStackTrace();
            return "检票失败，请稍后重试";
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (Exception ignored) {}
                DBUtil.close(conn);
            }
        }
    }

    /**
     * 先回滚业务事务，再用独立事务写入失败记录。
     * 失败记录不能跟随检票事务回滚，否则审计信息会丢失。
     */
    private String recordFailureAndReturn(int orderId, Integer ticketId, int sellerId, String reason) {
        String safeReason = reason == null ? "检票失败" : reason;
        if (safeReason.length() > 100) safeReason = safeReason.substring(0, 100);
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id FROM ticket WHERE order_id=?"
                   + (ticketId == null ? "" : " AND id=?"))) {
            ps.setInt(1, orderId);
            if (ticketId != null) ps.setInt(2, ticketId);
            List<Integer> ids = new ArrayList<Integer>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt("id"));
            }
            for (int id : ids) {
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO check_in(ticket_id,seller_id,check_time,status,fail_reason) "
                      + "VALUES (?,?,GETDATE(),N'失败',?)")) {
                    ins.setInt(1, id);
                    ins.setInt(2, sellerId);
                    ins.setString(3, safeReason);
                    ins.executeUpdate();
                }
            }
        } catch (SQLException e) {
            // 失败留痕失败不能覆盖原始业务错误，但保留服务端日志便于排查。
            e.printStackTrace();
        }
        return reason;
    }

    /** 查询检票成功后的全部票详情（多票订单逐票展示）。 */
    public List<Map<String, Object>> findTicketDetails(String orderNo) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        String sql = "SELECT o.order_no, t.passenger_name, t.id_card, t.passenger_phone, "
                   + "t.id AS ticket_id, t.status AS ticket_status, se.seat_no, "
                   + "r.route_name, r.start_port, r.end_port, "
                   + "CONVERT(varchar(16), s.depart_time, 120) AS depart_time, "
                   + "CONVERT(varchar(19), ci.check_time, 120) AS check_time, "
                   + "u.real_name AS seller_name "
                   + "FROM [order] o JOIN ticket t ON t.order_id=o.id "
                   + "JOIN schedule s ON s.id=o.schedule_id "
                   + "JOIN route r ON r.id=s.route_id "
                   + "LEFT JOIN seat se ON se.id=t.seat_id "
                   + "LEFT JOIN check_in ci ON ci.ticket_id=t.id AND ci.status=N'成功' "
                   + "LEFT JOIN [user] u ON u.id=ci.seller_id "
                   + "WHERE o.order_no=? ORDER BY t.id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderNo);
            try (ResultSet rs = ps.executeQuery()) {
                String[] cols = {"order_no","passenger_name","id_card","passenger_phone",
                                 "ticket_id","ticket_status","seat_no","route_name",
                                 "start_port","end_port","depart_time","check_time","seller_name"};
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<String, Object>();
                    for (String c : cols) {
                        Object value = rs.getString(c);
                        m.put(c, value == null ? "" : value);
                    }
                    list.add(m);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 单票兼容入口：返回第一张票。 */
    public Map<String, Object> findTicketDetail(String orderNo) {
        List<Map<String, Object>> list = findTicketDetails(orderNo);
        return list.isEmpty() ? new HashMap<String, Object>() : list.get(0);
    }

    /** 检票记录总数（分页用） */
    public int countCheckIns() {
        String sql = "SELECT COUNT(*) FROM check_in";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /** 检票记录分页列表 */
    public List<Map<String, Object>> listCheckIns(int page, int pageSize) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        String sql = "SELECT ci.id, ci.check_time, o.order_no, t.passenger_name, t.id_card, "
                   + "se.seat_no, r.route_name, CONVERT(varchar(16), s.depart_time, 120) AS depart_time, "
                   + "u.real_name AS seller_name, ci.status "
                   + "FROM check_in ci JOIN ticket t ON t.id=ci.ticket_id "
                   + "JOIN [order] o ON o.id=t.order_id "
                   + "JOIN schedule s ON s.id=o.schedule_id "
                   + "JOIN route r ON r.id=s.route_id "
                   + "LEFT JOIN seat se ON se.id=t.seat_id "
                   + "LEFT JOIN [user] u ON u.id=ci.seller_id "
                   + "ORDER BY ci.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, (page - 1) * pageSize);
            ps.setInt(2, pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                String[] cols = {"id","check_time","order_no","passenger_name","id_card",
                                 "seat_no","route_name","depart_time","seller_name","status"};
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<String, Object>();
                    for (String c : cols) {
                        Object v = rs.getString(c);
                        row.put(c, v == null ? "" : v);
                    }
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 检票统计：各班次应检/已检/上座率 */
    public List<Map<String, Object>> checkInStats() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        String sql = "SELECT s.id AS schedule_id, r.route_name, "
                   + "CONVERT(varchar(16), s.depart_time, 120) AS depart_time, "
                   + "s.sold_seats AS sold, "
                   + "ISNULL(COUNT(CASE WHEN ci.status=N'成功' THEN 1 END),0) AS checked, "
                   + "CASE WHEN s.sold_seats>0 THEN "
                   + "CAST(ISNULL(COUNT(CASE WHEN ci.status=N'成功' THEN 1 END),0)*100.0/s.sold_seats AS DECIMAL(5,1)) "
                   + "ELSE 0 END AS rate "
                   + "FROM schedule s JOIN route r ON r.id=s.route_id "
                   + "LEFT JOIN ticket t ON t.schedule_id=s.id "
                   + "LEFT JOIN check_in ci ON ci.ticket_id=t.id AND ci.status=N'成功' "
                   + "WHERE s.is_deleted=0 "
                   + "GROUP BY s.id, r.route_name, s.depart_time, s.sold_seats "
                   + "ORDER BY s.id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            String[] cols = {"schedule_id","route_name","depart_time","sold","checked","rate"};
            while (rs.next()) {
                Map<String, Object> row = new HashMap<String, Object>();
                for (String c : cols) {
                    Object v = rs.getString(c);
                    row.put(c, v == null ? "" : v);
                }
                list.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
