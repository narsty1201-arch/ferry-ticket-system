package com.ferry.dao;

import com.ferry.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 统计报表数据访问：聚合 SQL（COUNT/SUM/AVG/GROUP BY/CASE WHEN/JOIN） */
public class StatDAO {

    private List<Map<String, Object>> query(String sql) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<String, Object>();
                for (int i = 1; i <= cols; i++) {
                    String name = rs.getMetaData().getColumnLabel(i);
                    Object v = rs.getString(i);
                    row.put(name, v == null ? "" : v);
                }
                list.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 核心指标卡：总订单、总收入、已售座位、已检票、退票、上座率 */
    public Map<String, Object> overview() {
        Map<String, Object> m = new HashMap<String, Object>();
        String sql = "SELECT "
                   + "(SELECT COUNT(*) FROM [order]) AS total_orders, "
                   + "(SELECT ISNULL(SUM(CASE "
                   + "WHEN status IN (N'已支付',N'已检票',N'已完成') THEN total_amount "
                   + "WHEN status=N'已退票' THEN total_amount-ISNULL(refund_amount,0) "
                   + "ELSE 0 END),0) FROM [order]) AS total_income, "
                   + "(SELECT ISNULL(SUM(sold_seats),0) FROM schedule WHERE is_deleted=0) AS sold_seats, "
                   + "(SELECT COUNT(*) FROM [order] WHERE status=N'已检票') AS checked_orders, "
                   + "(SELECT COUNT(*) FROM [order] WHERE status=N'已退票') AS refunded_orders, "
                   + "(SELECT COUNT(*) FROM [user]) AS user_count, "
                   + "(SELECT COUNT(*) FROM schedule WHERE is_deleted=0) AS schedule_count";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String[] cols = {"total_orders","total_income","sold_seats","checked_orders",
                                 "refunded_orders","user_count","schedule_count"};
                for (String c : cols) m.put(c, rs.getString(c));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return m;
    }

    /** 按航线统计：订单数、净收入、已售座位（分开聚合，避免订单数放大已售座位） */
    public List<Map<String, Object>> byRoute() {
        return query("SELECT r.route_name, r.start_port, r.end_port, "
                   + "ISNULL(o.order_count,0) AS order_count, "
                   + "ISNULL(o.income,0) AS income, "
                   + "ISNULL(ss.sold,0) AS sold "
                   + "FROM route r "
                   + "LEFT JOIN ("
                   + "  SELECT s.route_id, COUNT(o.id) AS order_count, "
                   + "    SUM(CASE WHEN o.status IN (N'已支付',N'已检票',N'已完成') THEN o.total_amount "
                   + "             WHEN o.status=N'已退票' THEN o.total_amount-ISNULL(o.refund_amount,0) "
                   + "             ELSE 0 END) AS income "
                   + "  FROM schedule s LEFT JOIN [order] o ON o.schedule_id=s.id "
                   + "  WHERE s.is_deleted=0 GROUP BY s.route_id"
                   + ") o ON o.route_id=r.id "
                   + "LEFT JOIN ("
                   + "  SELECT route_id, SUM(sold_seats) AS sold FROM schedule "
                   + "  WHERE is_deleted=0 GROUP BY route_id"
                   + ") ss ON ss.route_id=r.id "
                   + "WHERE r.is_deleted=0 ORDER BY income DESC");
    }

    /** 按班次统计上座率（CASE WHEN 计算已售/可售） */
    public List<Map<String, Object>> bySchedule() {
        return query("SELECT s.id AS schedule_id, r.route_name, "
                   + "CONVERT(varchar(16), s.depart_time, 120) AS depart_time, "
                   + "s.total_seats, s.sold_seats, "
                   + "CASE WHEN s.total_seats>0 THEN CAST(s.sold_seats*100.0/s.total_seats AS DECIMAL(5,1)) ELSE 0 END AS rate "
                   + "FROM schedule s JOIN route r ON r.id=s.route_id "
                   + "WHERE s.is_deleted=0 ORDER BY s.id");
    }

    /** 售票员业绩排名（净收入；退款只扣除实际退款金额） */
    public List<Map<String, Object>> sellerPerformance() {
        return query("SELECT real_name, username, order_count, total_amount, "
                   + "CASE WHEN order_count>0 "
                   + "THEN CAST(total_amount/order_count AS DECIMAL(10,2)) ELSE 0 END AS avg_amount "
                   + "FROM ("
                   + "  SELECT u.real_name, u.username, "
                   + "    COUNT(CASE WHEN o.status IN (N'已支付',N'已检票',N'已完成',N'已退票') "
                   + "               THEN 1 END) AS order_count, "
                   + "    ISNULL(SUM(CASE WHEN o.status IN (N'已支付',N'已检票',N'已完成') THEN o.total_amount "
                   + "                    WHEN o.status=N'已退票' THEN o.total_amount-ISNULL(o.refund_amount,0) "
                   + "                    ELSE 0 END),0) AS total_amount "
                   + "  FROM [user] u LEFT JOIN [order] o ON o.seller_id=u.id "
                   + "  WHERE u.role=N'seller' GROUP BY u.id, u.real_name, u.username"
                   + ") x ORDER BY total_amount DESC");
    }

    /** 订单状态分布（CASE WHEN 分类统计） */
    public List<Map<String, Object>> orderStatus() {
        return query("SELECT "
                   + "SUM(CASE WHEN status=N'待支付' THEN 1 ELSE 0 END) AS pending, "
                   + "SUM(CASE WHEN status=N'已支付' THEN 1 ELSE 0 END) AS paid, "
                   + "SUM(CASE WHEN status=N'已检票' THEN 1 ELSE 0 END) AS checked, "
                   + "SUM(CASE WHEN status=N'已退票' THEN 1 ELSE 0 END) AS refunded, "
                   + "SUM(CASE WHEN status=N'已取消' THEN 1 ELSE 0 END) AS cancelled "
                   + "FROM [order]");
    }
}
