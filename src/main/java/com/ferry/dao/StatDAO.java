package com.ferry.dao;

import com.ferry.util.DBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计报表数据访问：聚合 SQL（COUNT/SUM/AVG/GROUP BY/CASE WHEN/JOIN）。
 *
 * 日期参数均为半开区间：[start, endExclusive)。不同指标采用各自业务时点：
 * 订单与销售额按 order.create_time，班次上座率按 schedule.depart_time，
 * 退票原因按 order.refund_time，售票员检票数按 check_in.check_time。
 */
public class StatDAO {

    private static final String BUSINESS_ORDER_STATUS =
            "o.status IN (N'已支付',N'已检票',N'已完成',N'已退票')";
    private static final String COMPLETED_ORDER_STATUS =
            "o.status IN (N'已检票',N'已完成')";
    private static final String ACTIVE_TICKET_STATUS =
            "t.status IN (N'已支付',N'已检票')";

    private static final class Filter {
        private final Timestamp start;
        private final Timestamp endExclusive;
        private final Integer routeId;

        private Filter(Date start, Date endExclusive, Integer routeId) {
            this.start = start == null ? null : new Timestamp(start.getTime());
            this.endExclusive = endExclusive == null ? null : new Timestamp(endExclusive.getTime());
            this.routeId = routeId;
        }

    }

    private List<Map<String, Object>> query(String sql, List<Object> params) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                int cols = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<String, Object>();
                    for (int i = 1; i <= cols; i++) {
                        String name = rs.getMetaData().getColumnLabel(i);
                        Object value = rs.getString(i);
                        row.put(name, value == null ? "" : value);
                    }
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 核心指标卡（全部时间、全部航线）。 */
    public Map<String, Object> overview() {
        return overview(null, null, null);
    }

    public Map<String, Object> overview(Date start, Date endExclusive, Integer routeId) {
        Filter f = new Filter(start, endExclusive, routeId);
        StringBuilder sql = new StringBuilder("SELECT ");
        List<Object> params = new ArrayList<Object>();

        sql.append("(SELECT COUNT(*) FROM [order] o JOIN schedule s ON s.id=o.schedule_id WHERE 1=1");
        appendOrderFilter(sql, params, "o", "s", f);
        sql.append(") AS total_orders, ");

        sql.append("(SELECT ISNULL(SUM(CASE ")
           .append("WHEN o.status IN (N'已支付',N'已检票',N'已完成') THEN o.total_amount ")
           .append("WHEN o.status=N'已退票' THEN o.total_amount-ISNULL(o.refund_amount,0) ")
           .append("ELSE 0 END),0) FROM [order] o JOIN schedule s ON s.id=o.schedule_id WHERE 1=1");
        appendOrderFilter(sql, params, "o", "s", f);
        sql.append(") AS total_income, ");

        sql.append("(SELECT COUNT(*) FROM ticket t JOIN [order] o ON o.id=t.order_id ")
           .append("JOIN schedule s ON s.id=o.schedule_id WHERE ")
           .append(ACTIVE_TICKET_STATUS);
        appendOrderFilter(sql, params, "o", "s", f);
        sql.append(") AS sold_seats, ");

        sql.append("(SELECT COUNT(*) FROM [order] o JOIN schedule s ON s.id=o.schedule_id WHERE ")
           .append(COMPLETED_ORDER_STATUS);
        appendOrderFilter(sql, params, "o", "s", f);
        sql.append(") AS checked_orders, ");

        sql.append("(SELECT COUNT(*) FROM [order] o JOIN schedule s ON s.id=o.schedule_id ")
           .append("WHERE o.status=N'已完成'");
        appendOrderFilter(sql, params, "o", "s", f);
        sql.append(") AS completed_orders, ");

        sql.append("(SELECT COUNT(*) FROM [order] o JOIN schedule s ON s.id=o.schedule_id ")
           .append("WHERE o.status=N'已退票'");
        appendOrderFilter(sql, params, "o", "s", f);
        sql.append(") AS refunded_orders, ");

        sql.append("(SELECT ISNULL(SUM(ISNULL(o.refund_amount,0)),0) ")
           .append("FROM [order] o JOIN schedule s ON s.id=o.schedule_id WHERE o.status=N'已退票'");
        appendOrderFilter(sql, params, "o", "s", f);
        sql.append(") AS refund_amount, ");

        sql.append("(SELECT COUNT(*) FROM [user]) AS user_count, ");

        sql.append("(SELECT COUNT(*) FROM schedule s WHERE s.is_deleted=0");
        appendScheduleFilter(sql, params, "s", f);
        sql.append(") AS schedule_count");

        List<Map<String, Object>> rows = query(sql.toString(), params);
        return rows.isEmpty() ? new HashMap<String, Object>() : rows.get(0);
    }

    /** 按航线统计：订单数只含已成交业务，已售数按有效船票统计，避免被取消订单放大。 */
    public List<Map<String, Object>> byRoute() {
        return byRoute(null, null);
    }

    public List<Map<String, Object>> byRoute(Date start, Date endExclusive) {
        return byRoute(start, endExclusive, null);
    }

    public List<Map<String, Object>> byRoute(Date start, Date endExclusive, Integer routeId) {
        Filter f = new Filter(start, endExclusive, routeId);
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<Object>();
        sql.append("SELECT r.route_name, r.start_port, r.end_port, ")
           .append("ISNULL(o.order_count,0) AS order_count, ")
           .append("ISNULL(o.checked_orders,0) AS checked_orders, ")
           .append("ISNULL(o.income,0) AS income, ")
           .append("ISNULL(o.refund_amount,0) AS refund_amount, ")
           .append("ISNULL(t.sold,0) AS sold, ISNULL(c.checked,0) AS checked ")
           .append("FROM route r ")
           .append("LEFT JOIN (")
           .append("  SELECT s.route_id, COUNT(CASE WHEN ").append(BUSINESS_ORDER_STATUS)
           .append(" THEN 1 END) AS order_count, ")
           .append("         COUNT(CASE WHEN ").append(COMPLETED_ORDER_STATUS)
           .append(" THEN 1 END) AS checked_orders, ")
           .append("         ISNULL(SUM(CASE ")
           .append("           WHEN o.status IN (N'已支付',N'已检票',N'已完成') THEN o.total_amount ")
           .append("           WHEN o.status=N'已退票' THEN o.total_amount-ISNULL(o.refund_amount,0) ")
           .append("           ELSE 0 END),0) AS income, ")
           .append("         ISNULL(SUM(CASE WHEN o.status=N'已退票' ")
           .append("           THEN ISNULL(o.refund_amount,0) ELSE 0 END),0) AS refund_amount ")
           .append("  FROM schedule s LEFT JOIN [order] o ON o.schedule_id=s.id ")
           .append("  WHERE 1=1");
        appendOrderFilter(sql, params, "o", "s", f);
        sql.append("  GROUP BY s.route_id")
           .append(") o ON o.route_id=r.id ")
           .append("LEFT JOIN (")
           .append("  SELECT s.route_id, COUNT(*) AS sold ")
           .append("  FROM ticket t JOIN [order] o ON o.id=t.order_id ")
           .append("  JOIN schedule s ON s.id=o.schedule_id ")
           .append("  WHERE ").append(ACTIVE_TICKET_STATUS);
        appendOrderFilter(sql, params, "o", "s", f);
        sql.append("  GROUP BY s.route_id")
           .append(") t ON t.route_id=r.id ")
           .append("LEFT JOIN (")
           .append("  SELECT s.route_id, COUNT(*) AS checked ")
           .append("  FROM check_in ci JOIN ticket t ON t.id=ci.ticket_id ")
           .append("  JOIN [order] o ON o.id=t.order_id ")
           .append("  JOIN schedule s ON s.id=o.schedule_id ")
           .append("  WHERE ci.status=N'成功'");
        appendOrderFilter(sql, params, "o", "s", f);
        sql.append("  GROUP BY s.route_id")
           .append(") c ON c.route_id=r.id ")
           .append("WHERE (r.is_deleted=0 OR ISNULL(o.order_count,0)>0 ")
           .append("       OR ISNULL(t.sold,0)>0 OR ISNULL(c.checked,0)>0)");
        if (f.routeId != null) {
            sql.append(" AND r.id=?");
            params.add(f.routeId);
        }
        sql.append(" ORDER BY ISNULL(o.income,0) DESC, r.route_name");
        return query(sql.toString(), params);
    }

    /** 按班次统计上座率及运营预警。预警只针对未来 24/48 小时内的低上座班次。 */
    public List<Map<String, Object>> bySchedule() {
        return bySchedule(null, null, null);
    }

    public List<Map<String, Object>> bySchedule(Date start, Date endExclusive, Integer routeId) {
        Filter f = new Filter(start, endExclusive, routeId);
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<Object>();
        sql.append("SELECT s.id AS schedule_id, r.route_name, s.status, ")
           .append("CONVERT(varchar(16), s.depart_time, 120) AS depart_time, ")
           .append("s.total_seats, s.sold_seats, ISNULL(c.checked,0) AS checked, ")
           .append("CASE WHEN s.total_seats>0 ")
           .append("THEN CAST(s.sold_seats*100.0/s.total_seats AS DECIMAL(5,1)) ")
           .append("ELSE 0 END AS rate, ")
           .append("CASE ")
           .append(" WHEN s.depart_time < GETDATE() THEN N'已结束' ")
           .append(" WHEN s.total_seats<=0 THEN N'无数据' ")
           .append(" WHEN s.sold_seats*100.0/s.total_seats < 30 ")
           .append("      AND s.depart_time < DATEADD(HOUR,24,GETDATE()) THEN N'紧急' ")
           .append(" WHEN s.sold_seats*100.0/s.total_seats < 60 ")
           .append("      AND s.depart_time < DATEADD(HOUR,48,GETDATE()) THEN N'关注' ")
           .append(" ELSE N'正常' END AS alert_level ")
           .append("FROM schedule s JOIN route r ON r.id=s.route_id ")
           .append("LEFT JOIN (")
           .append("  SELECT t.schedule_id, COUNT(*) AS checked ")
           .append("  FROM check_in ci JOIN ticket t ON t.id=ci.ticket_id ")
           .append("  WHERE ci.status=N'成功' GROUP BY t.schedule_id")
           .append(") c ON c.schedule_id=s.id ")
           .append("WHERE s.is_deleted=0");
        appendScheduleFilter(sql, params, "s", f);
        sql.append(" ORDER BY s.depart_time DESC, s.id DESC");
        return query(sql.toString(), params);
    }

    /** 售票员业绩排名；销售额按订单创建时间，检票数按实际检票时间。 */
    public List<Map<String, Object>> sellerPerformance() {
        return sellerPerformance(null, null, null);
    }

    public List<Map<String, Object>> sellerPerformance(Date start, Date endExclusive) {
        return sellerPerformance(start, endExclusive, null);
    }

    public List<Map<String, Object>> sellerPerformance(Date start, Date endExclusive, Integer routeId) {
        return querySellerPerformance(new Filter(start, endExclusive, routeId), null);
    }

    /** 单个售票员的本期业绩；无数据时返回全 0，便于页面直接展示。 */
    public Map<String, Object> sellerSummary(int sellerId, Date start, Date endExclusive) {
        List<Map<String, Object>> rows = querySellerPerformance(
                new Filter(start, endExclusive, null), sellerId);
        if (!rows.isEmpty()) return rows.get(0);
        Map<String, Object> empty = new HashMap<String, Object>();
        String[] keys = {"real_name", "username", "order_count", "ticket_count",
                         "total_amount", "refund_count", "refund_amount",
                         "checked_count", "avg_amount"};
        for (String key : keys) empty.put(key, "0");
        return empty;
    }

    private List<Map<String, Object>> querySellerPerformance(Filter f, Integer sellerId) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<Object>();
        sql.append("SELECT u.real_name, u.username, ")
           .append("ISNULL(o.order_count,0) AS order_count, ")
           .append("ISNULL(t.ticket_count,0) AS ticket_count, ")
           .append("ISNULL(o.total_amount,0) AS total_amount, ")
           .append("ISNULL(o.refund_count,0) AS refund_count, ")
           .append("ISNULL(o.refund_amount,0) AS refund_amount, ")
           .append("ISNULL(c.checked_count,0) AS checked_count, ")
           .append("CASE WHEN ISNULL(o.order_count,0)>0 ")
           .append("THEN CAST(ISNULL(o.total_amount,0)/o.order_count AS DECIMAL(10,2)) ")
           .append("ELSE 0 END AS avg_amount ")
           .append("FROM [user] u ")
           .append("LEFT JOIN (")
           .append("  SELECT o.seller_id, ")
           .append("         COUNT(CASE WHEN ").append(BUSINESS_ORDER_STATUS)
           .append(" THEN 1 END) AS order_count, ")
           .append("         COUNT(CASE WHEN o.status=N'已退票' THEN 1 END) AS refund_count, ")
           .append("         ISNULL(SUM(CASE ")
           .append("           WHEN o.status IN (N'已支付',N'已检票',N'已完成') THEN o.total_amount ")
           .append("           WHEN o.status=N'已退票' THEN o.total_amount-ISNULL(o.refund_amount,0) ")
           .append("           ELSE 0 END),0) AS total_amount, ")
           .append("         ISNULL(SUM(CASE WHEN o.status=N'已退票' ")
           .append("           THEN ISNULL(o.refund_amount,0) ELSE 0 END),0) AS refund_amount ")
           .append("  FROM [order] o JOIN schedule s ON s.id=o.schedule_id ")
           .append("  WHERE o.seller_id IS NOT NULL");
        appendOrderFilter(sql, params, "o", "s", f);
        sql.append("  GROUP BY o.seller_id")
           .append(") o ON o.seller_id=u.id ")
           .append("LEFT JOIN (")
           .append("  SELECT o.seller_id, COUNT(*) AS ticket_count ")
           .append("  FROM ticket t JOIN [order] o ON o.id=t.order_id ")
           .append("  JOIN schedule s ON s.id=o.schedule_id ")
           .append("  WHERE o.seller_id IS NOT NULL AND ").append(ACTIVE_TICKET_STATUS);
        appendOrderFilter(sql, params, "o", "s", f);
        sql.append("  GROUP BY o.seller_id")
           .append(") t ON t.seller_id=u.id ")
           .append("LEFT JOIN (")
           .append("  SELECT ci.seller_id, COUNT(*) AS checked_count ")
           .append("  FROM check_in ci JOIN ticket t ON t.id=ci.ticket_id ")
           .append("  JOIN [order] o ON o.id=t.order_id ")
           .append("  JOIN schedule s ON s.id=o.schedule_id ")
           .append("  WHERE ci.status=N'成功'");
        appendCheckInFilter(sql, params, "ci", "s", f);
        sql.append("  GROUP BY ci.seller_id")
           .append(") c ON c.seller_id=u.id ")
           .append("WHERE u.role='seller'");
        if (sellerId != null) {
            sql.append(" AND u.id=?");
            params.add(sellerId);
        }
        sql.append(" ORDER BY ISNULL(o.total_amount,0) DESC, u.real_name");
        return query(sql.toString(), params);
    }

    /** 退票原因分布：按退款时间筛选，未填写原因统一归入“未填写”。 */
    public List<Map<String, Object>> refundReasons() {
        return refundReasons(null, null, null);
    }

    public List<Map<String, Object>> refundReasons(Date start, Date endExclusive, Integer routeId) {
        Filter f = new Filter(start, endExclusive, routeId);
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<Object>();
        sql.append("SELECT ISNULL(NULLIF(LTRIM(RTRIM(o.refund_reason)),N''),N'未填写') AS reason, ")
           .append("COUNT(*) AS refund_count, ")
           .append("ISNULL(SUM(o.total_amount),0) AS refunded_total, ")
           .append("ISNULL(SUM(ISNULL(o.refund_amount,0)),0) AS refund_amount ")
           .append("FROM [order] o JOIN schedule s ON s.id=o.schedule_id ")
           .append("WHERE o.status=N'已退票'");
        appendRefundFilter(sql, params, "o", "s", f);
        sql.append(" GROUP BY ISNULL(NULLIF(LTRIM(RTRIM(o.refund_reason)),N''),N'未填写') ")
           .append("ORDER BY COUNT(*) DESC, reason");
        return query(sql.toString(), params);
    }

    /** 订单状态分布（CASE WHEN 分类统计），包含“已完成”与合计。 */
    public List<Map<String, Object>> orderStatus() {
        return orderStatus(null, null, null);
    }

    public List<Map<String, Object>> orderStatus(Date start, Date endExclusive, Integer routeId) {
        Filter f = new Filter(start, endExclusive, routeId);
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<Object>();
        sql.append("SELECT COUNT(*) AS total, ")
           .append("ISNULL(SUM(CASE WHEN o.status=N'待支付' THEN 1 ELSE 0 END),0) AS pending, ")
           .append("ISNULL(SUM(CASE WHEN o.status=N'已支付' THEN 1 ELSE 0 END),0) AS paid, ")
           .append("ISNULL(SUM(CASE WHEN o.status=N'已检票' THEN 1 ELSE 0 END),0) AS checked, ")
           .append("ISNULL(SUM(CASE WHEN o.status=N'已完成' THEN 1 ELSE 0 END),0) AS completed, ")
           .append("ISNULL(SUM(CASE WHEN o.status=N'已退票' THEN 1 ELSE 0 END),0) AS refunded, ")
           .append("ISNULL(SUM(CASE WHEN o.status=N'已取消' THEN 1 ELSE 0 END),0) AS cancelled ")
           .append("FROM [order] o JOIN schedule s ON s.id=o.schedule_id WHERE 1=1");
        appendOrderFilter(sql, params, "o", "s", f);
        return query(sql.toString(), params);
    }

    private void appendOrderFilter(StringBuilder sql, List<Object> params,
            String orderAlias, String scheduleAlias, Filter f) {
        if (f.routeId != null) {
            sql.append(" AND ").append(scheduleAlias).append(".route_id=?");
            params.add(f.routeId);
        }
        if (f.start != null) {
            sql.append(" AND ").append(orderAlias).append(".create_time>=?");
            params.add(f.start);
        }
        if (f.endExclusive != null) {
            sql.append(" AND ").append(orderAlias).append(".create_time<?");
            params.add(f.endExclusive);
        }
    }

    private void appendScheduleFilter(StringBuilder sql, List<Object> params,
            String scheduleAlias, Filter f) {
        if (f.routeId != null) {
            sql.append(" AND ").append(scheduleAlias).append(".route_id=?");
            params.add(f.routeId);
        }
        if (f.start != null) {
            sql.append(" AND ").append(scheduleAlias).append(".depart_time>=?");
            params.add(f.start);
        }
        if (f.endExclusive != null) {
            sql.append(" AND ").append(scheduleAlias).append(".depart_time<?");
            params.add(f.endExclusive);
        }
    }

    private void appendRefundFilter(StringBuilder sql, List<Object> params,
            String orderAlias, String scheduleAlias, Filter f) {
        if (f.routeId != null) {
            sql.append(" AND ").append(scheduleAlias).append(".route_id=?");
            params.add(f.routeId);
        }
        if (f.start != null) {
            sql.append(" AND ").append(orderAlias).append(".refund_time>=?");
            params.add(f.start);
        }
        if (f.endExclusive != null) {
            sql.append(" AND ").append(orderAlias).append(".refund_time<?");
            params.add(f.endExclusive);
        }
    }

    private void appendCheckInFilter(StringBuilder sql, List<Object> params,
            String checkInAlias, String scheduleAlias, Filter f) {
        if (f.routeId != null) {
            sql.append(" AND ").append(scheduleAlias).append(".route_id=?");
            params.add(f.routeId);
        }
        if (f.start != null) {
            sql.append(" AND ").append(checkInAlias).append(".check_time>=?");
            params.add(f.start);
        }
        if (f.endExclusive != null) {
            sql.append(" AND ").append(checkInAlias).append(".check_time<?");
            params.add(f.endExclusive);
        }
    }
}
