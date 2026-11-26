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

    /**
     * 检票：按订单号校验并检票，全事务。
     * 返回 null 表示成功；否则返回失败原因。
     */
    public String checkIn(String orderNo, int sellerId) {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // 1. 查订单 + 票 + 班次（UPDLOCK 锁订单防并发重复检票）
            int orderId = 0, ticketId = 0;
            String orderStatus = null, ticketStatus = null;
            Timestamp departTime = null;
            String passengerName = null, seatNo = null, routeName = null;
            String sql = "SELECT o.id AS order_id, o.status AS order_status, "
                       + "t.id AS ticket_id, t.status AS ticket_status, t.passenger_name, "
                       + "s.depart_time, se.seat_no, r.route_name "
                       + "FROM [order] o WITH (UPDLOCK) "
                       + "JOIN ticket t ON t.order_id=o.id "
                       + "JOIN schedule s ON s.id=o.schedule_id "
                       + "JOIN route r ON r.id=s.route_id "
                       + "LEFT JOIN seat se ON se.id=t.seat_id "
                       + "WHERE o.order_no=?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, orderNo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return "订单号不存在：" + orderNo; }
                    orderId = rs.getInt("order_id");
                    orderStatus = rs.getString("order_status");
                    ticketId = rs.getInt("ticket_id");
                    ticketStatus = rs.getString("ticket_status");
                    passengerName = rs.getString("passenger_name");
                    departTime = rs.getTimestamp("depart_time");
                    seatNo = rs.getString("seat_no");
                    routeName = rs.getString("route_name");
                }
            }

            // 2. 校验状态
            if ("已检票".equals(orderStatus)) { conn.rollback(); return "该订单已检票，请勿重复检票"; }
            if ("已退票".equals(orderStatus) || "已取消".equals(orderStatus)) {
                conn.rollback(); return "该订单已退票/取消，不能检票";
            }
            if ("待支付".equals(orderStatus)) { conn.rollback(); return "该订单尚未支付，不能检票"; }
            if (!"已支付".equals(orderStatus)) { conn.rollback(); return "订单状态异常（" + orderStatus + "），不能检票"; }

            // 3. 是否已检票过（防重复）
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM check_in WHERE ticket_id=? AND status=N'成功'")) {
                ps.setInt(1, ticketId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) { conn.rollback(); return "该船票已检票，请勿重复检票"; }
                }
            }

            // 4. 写检票记录
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO check_in(ticket_id, seller_id, check_time, status) VALUES (?,?,GETDATE(),N'成功')")) {
                ps.setInt(1, ticketId);
                ps.setInt(2, sellerId);
                ps.executeUpdate();
            }
            // 5. 更新票和订单状态
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ticket SET status=N'已检票' WHERE id=?")) {
                ps.setInt(1, ticketId); ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE [order] SET status=N'已检票' WHERE id=?")) {
                ps.setInt(1, orderId); ps.executeUpdate();
            }
            conn.commit();

            // 成功信息：航线+乘客+座位
            return null;
        } catch (SQLException e) {
            if (conn != null) { try { conn.rollback(); } catch (Exception ignored) {} }
            e.printStackTrace();
            return "检票失败：" + e.getMessage();
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (Exception ignored) {}
                DBUtil.close(conn);
            }
        }
    }

    /** 查询检票成功后的票详情（供成功页展示） */
    public Map<String, Object> findTicketDetail(String orderNo) {
        Map<String, Object> m = new HashMap<String, Object>();
        String sql = "SELECT o.order_no, t.passenger_name, t.id_card, t.passenger_phone, "
                   + "se.seat_no, r.route_name, r.start_port, r.end_port, "
                   + "CONVERT(varchar(16), s.depart_time, 120) AS depart_time, "
                   + "CONVERT(varchar(19), ci.check_time, 120) AS check_time, "
                   + "u.real_name AS seller_name "
                   + "FROM [order] o JOIN ticket t ON t.order_id=o.id "
                   + "JOIN schedule s ON s.id=o.schedule_id "
                   + "JOIN route r ON r.id=s.route_id "
                   + "LEFT JOIN seat se ON se.id=t.seat_id "
                   + "LEFT JOIN check_in ci ON ci.ticket_id=t.id AND ci.status=N'成功' "
                   + "LEFT JOIN [user] u ON u.id=ci.seller_id "
                   + "WHERE o.order_no=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, orderNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String[] cols = {"order_no","passenger_name","id_card","passenger_phone",
                                     "seat_no","route_name","start_port","end_port",
                                     "depart_time","check_time","seller_name"};
                    for (String c : cols) m.put(c, rs.getString(c));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return m;
    }

    /** 检票记录列表 */
    public List<Map<String, Object>> listCheckIns() {
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
                   + "ORDER BY ci.id DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
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
