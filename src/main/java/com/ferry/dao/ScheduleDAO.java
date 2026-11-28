package com.ferry.dao;

import com.ferry.entity.Schedule;
import com.ferry.util.DBUtil;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 班次 DAO —— 阶段3核心：
 * 1) 联表查询带出航线/船舶名   2) 时间冲突校验
 * 3) 新增班次时从 ship_seat 初始化班次座位（第二层 seat）
 * 4) 取消班次用事务：座位回滚 + 已支付订单自动退票
 */
public class ScheduleDAO {

    /**
     * 自动刷新过期班次状态（对标 12306 开车/到站状态流转）：
     * 已过发船时间且仍“售票中” → “已发船”；已过到达时间且“已发船” → “已完成”。
     * 由 Filter 定时调用，保证不依赖用户访问特定页面。
     * @return 本次更新的班次数量
     */
    public int refreshExpired() {
        int n = 0;
        String sql1 = "UPDATE schedule SET status=N'已发船' "
                    + "WHERE is_deleted=0 AND status=N'售票中' AND depart_time < GETDATE()";
        String sql2 = "UPDATE schedule SET status=N'已完成' "
                    + "WHERE is_deleted=0 AND status=N'已发船' AND arrive_time < GETDATE()";
        // 任务闭环：船已到港，已检票的订单自动转“已完成”（未检票的保持已支付，方便后续改签/补检）
        String sql3 = "UPDATE [order] SET status=N'已完成' "
                    + "WHERE status=N'已检票' AND schedule_id IN "
                    + "(SELECT id FROM schedule WHERE status=N'已完成' AND is_deleted=0)";
        try (Connection conn = DBUtil.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sql1)) {
                n += ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(sql2)) {
                n += ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(sql3)) {
                ps.executeUpdate();   // 订单闭环数不计入班次更新数
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return n;
    }

    /** 查询所有未删除班次，联表带出名称和剩余座位 */
    public List<Schedule> findAll() {
        refreshExpired();   // 查询前先刷新过期班次状态
        List<Schedule> list = new ArrayList<>();
        String sql = "SELECT s.*, r.route_name, r.start_port, r.end_port, sh.ship_name "
                   + "FROM schedule s "
                   + "JOIN route r ON r.id = s.route_id "
                   + "JOIN ship sh ON sh.id = s.ship_id "
                   + "WHERE s.is_deleted = 0 ORDER BY s.depart_time";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * 查询可购票班次，支持出发港、到达港和自然日筛选。
     * 空条件表示不限制；日期使用半开区间，避免对 DATETIME 做 CAST。
     */
    public List<Schedule> searchBookable(String startPort, String endPort, Date travelDate) {
        refreshExpired();
        List<Schedule> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT s.*, r.route_name, r.start_port, r.end_port, sh.ship_name "
              + "FROM schedule s "
              + "JOIN route r ON r.id = s.route_id "
              + "JOIN ship sh ON sh.id = s.ship_id "
              + "WHERE s.is_deleted=0 AND r.is_deleted=0 AND sh.is_deleted=0 "
              + "AND r.status='active' AND sh.status='active' "
              + "AND s.status=N'售票中' AND s.sold_seats < s.total_seats "
              + "AND s.depart_time > GETDATE()");
        if (hasText(startPort)) sql.append(" AND r.start_port=?");
        if (hasText(endPort)) sql.append(" AND r.end_port=?");
        if (travelDate != null) {
            sql.append(" AND s.depart_time >= ? AND s.depart_time < ?");
        }
        sql.append(" ORDER BY s.depart_time");

        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (hasText(startPort)) ps.setString(idx++, startPort);
            if (hasText(endPort)) ps.setString(idx++, endPort);
            if (travelDate != null) {
                long start = travelDate.getTime();
                ps.setTimestamp(idx++, new Timestamp(start));
                ps.setTimestamp(idx, new Timestamp(start + 24L * 60L * 60L * 1000L));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** 时间冲突校验：同一艘船在同一发船时间是否已有未删除班次 */
    public boolean timeConflict(int shipId, Timestamp departTime, int excludeId) {
        String sql = "SELECT COUNT(*) FROM schedule "
                   + "WHERE ship_id=? AND depart_time=? AND is_deleted=0 AND id <> ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shipId);
            ps.setTimestamp(2, departTime);
            ps.setInt(3, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 新增班次 + 初始化班次座位，放在同一个事务里。
     * 到达时间由 发船时间 + 航线航行时长 自动计算。
     */
    public int insertWithSeats(Schedule s) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            if (s.getDepartTime() == null || s.getPrice() == null
                    || s.getPrice().signum() <= 0 || s.getTotalSeats() <= 0) {
                throw new SQLException("班次时间、票价或座位数不合法");
            }

            // 1. 取航线航行时长和船舶容量，校验座位数
            int durationMin;
            int shipCapacity;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT r.duration_min, sh.total_seats AS ship_capacity "
                  + "FROM route r WITH (HOLDLOCK) JOIN ship sh WITH (HOLDLOCK) ON sh.id=? "
                  + "WHERE r.id=? AND r.is_deleted=0 AND sh.is_deleted=0")) {
                ps.setInt(1, s.getShipId());
                ps.setInt(2, s.getRouteId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("航线或船舶不存在");
                    durationMin = rs.getInt(1);
                    shipCapacity = rs.getInt("ship_capacity");
                }
            }
            if (s.getTotalSeats() > shipCapacity) {
                throw new SQLException("班次座位数不能超过船舶容量 " + shipCapacity);
            }
            if (timeConflictInTransaction(conn, s.getShipId(), s.getDepartTime(), 0)) {
                throw new SQLException("时间冲突：该船在这个发船时间已有班次");
            }
            Timestamp arrive = new Timestamp(
                    s.getDepartTime().getTime() + durationMin * 60L * 1000L);

            // 2. 插入班次，取回自增 id
            String sqlSched = "INSERT INTO schedule(route_id,ship_id,depart_time,arrive_time,price,total_seats,status) "
                            + "VALUES (?,?,?,?,?,?,N'售票中')";
            int schedId;
            try (PreparedStatement ps = conn.prepareStatement(sqlSched, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, s.getRouteId());
                ps.setInt(2, s.getShipId());
                ps.setTimestamp(3, s.getDepartTime());
                ps.setTimestamp(4, arrive);
                ps.setBigDecimal(5, s.getPrice());
                ps.setInt(6, s.getTotalSeats());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    schedId = keys.getInt(1);
                }
            }

            // 3. 从第一层 ship_seat 复制生成第二层班次座位，状态“可售”
            String sqlSeat = "INSERT INTO seat(schedule_id,seat_no,seat_status) VALUES (?,?,N'可售')";
            try (PreparedStatement ps = conn.prepareStatement(sqlSeat)) {
                ps.setInt(1, schedId);
                // 这里用船舶座位数生成编号，与 ship_seat 保持一致
                for (int j = 1; j <= s.getTotalSeats(); j++) {
                    String seatNo = "" + (char) ('A' + (j - 1) / 10) + (((j - 1) % 10) + 1);
                    ps.setString(2, seatNo);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            return schedId;
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
     * 管理员手动改班次状态（演示用：强制发船 / 恢复售票 / 置为完成）。
     * 限制：只能改未删除的班次；目标状态必须在允许集合内；
     * “恢复售票”要求发船时间在未来，否则没有实际意义。
     * @return null 成功，否则返回错误原因
     */
    public String updateStatus(int id, String toStatus) {
        if (toStatus == null || !("待发布".equals(toStatus) || "售票中".equals(toStatus)
                || "已发船".equals(toStatus) || "已完成".equals(toStatus))) {
            return "目标状态不合法";
        }
        try (Connection conn = DBUtil.getConnection()) {
            Timestamp depart = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT depart_time FROM schedule WHERE id=? AND is_deleted=0")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return "班次不存在或已删除";
                    depart = rs.getTimestamp("depart_time");
                }
            }
            if ("售票中".equals(toStatus) && depart != null
                    && !depart.after(new Timestamp(System.currentTimeMillis()))) {
                return "发船时间已过，不能恢复售票";
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE schedule SET status=? WHERE id=? AND is_deleted=0")) {
                ps.setString(1, toStatus);
                ps.setInt(2, id);
                ps.executeUpdate();
            }
            // 手动置为“已完成”时，已检票订单同步闭环
            if ("已完成".equals(toStatus)) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE [order] SET status=N'已完成' WHERE schedule_id=? AND status=N'已检票'")) {
                    ps.setInt(1, id);
                    ps.executeUpdate();
                }
            }
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return "操作失败：" + e.getMessage();
        }
    }

    /**
     * 修改班次：重新计算到达时间，并在事务内校验时间冲突和座位容量。
     * 已创建班次不允许减少座位数；如需缩减，取消后重新创建。
     */
    public int update(Schedule s) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            int currentTotal;
            int soldSeats;
            Timestamp currentDepart;
            String currentStatus;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT total_seats, sold_seats, depart_time, status FROM schedule "
                  + "WITH (UPDLOCK, HOLDLOCK) WHERE id=? AND is_deleted=0")) {
                ps.setInt(1, s.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("班次不存在或已删除");
                    currentTotal = rs.getInt("total_seats");
                    soldSeats = rs.getInt("sold_seats");
                    currentDepart = rs.getTimestamp("depart_time");
                    currentStatus = rs.getString("status");
                }
            }

            int newTotal = s.getTotalSeats() > 0 ? s.getTotalSeats() : currentTotal;
            if (newTotal < soldSeats) {
                throw new SQLException("座位数不能少于已售座位数 " + soldSeats);
            }
            if (newTotal < currentTotal) {
                throw new SQLException("已创建班次不能减少座位数，请取消后重新创建");
            }

            int durationMin;
            int shipCapacity;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT r.duration_min, sh.total_seats AS ship_capacity "
                  + "FROM route r WITH (HOLDLOCK) JOIN ship sh WITH (HOLDLOCK) ON sh.id=? "
                  + "WHERE r.id=? AND r.is_deleted=0 AND sh.is_deleted=0")) {
                ps.setInt(1, s.getShipId());
                ps.setInt(2, s.getRouteId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("航线或船舶不存在");
                    durationMin = rs.getInt("duration_min");
                    shipCapacity = rs.getInt("ship_capacity");
                }
            }
            if (newTotal > shipCapacity) {
                throw new SQLException("班次座位数不能超过船舶容量 " + shipCapacity);
            }

            Timestamp depart = s.getDepartTime() == null ? currentDepart : s.getDepartTime();
            if (depart == null || s.getPrice() == null || s.getPrice().signum() <= 0) {
                throw new SQLException("班次时间或票价不合法");
            }
            String status = s.getStatus() == null ? currentStatus : s.getStatus();
            if (!("待发布".equals(status) || "售票中".equals(status)
                    || "已发船".equals(status) || "已完成".equals(status))) {
                throw new SQLException("班次状态不合法");
            }
            if (timeConflictInTransaction(conn, s.getShipId(), depart, s.getId())) {
                throw new SQLException("时间冲突：该船在这个发船时间已有班次");
            }

            Timestamp arrive = new Timestamp(
                    depart.getTime() + durationMin * 60L * 1000L);
            int updated;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE schedule SET route_id=?, ship_id=?, depart_time=?, arrive_time=?, "
                  + "price=?, total_seats=?, status=? WHERE id=? AND is_deleted=0")) {
                ps.setInt(1, s.getRouteId());
                ps.setInt(2, s.getShipId());
                ps.setTimestamp(3, depart);
                ps.setTimestamp(4, arrive);
                ps.setBigDecimal(5, s.getPrice());
                ps.setInt(6, newTotal);
                ps.setString(7, status);
                ps.setInt(8, s.getId());
                updated = ps.executeUpdate();
            }
            if (updated != 1) throw new SQLException("班次更新失败");
            if (newTotal > currentTotal) {
                addMissingSeats(conn, s.getId(), newTotal);
            }

            conn.commit();
            return updated;
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

    private boolean timeConflictInTransaction(Connection conn, int shipId,
            Timestamp departTime, int excludeId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM schedule WITH (UPDLOCK, HOLDLOCK) "
              + "WHERE ship_id=? AND depart_time=? AND is_deleted=0 AND id<>?")) {
            ps.setInt(1, shipId);
            ps.setTimestamp(2, departTime);
            ps.setInt(3, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** 班次扩容时补齐缺少的座位；已存在编号不会被覆盖。 */
    private void addMissingSeats(Connection conn, int scheduleId, int newTotal)
            throws SQLException {
        Set<String> existing = new HashSet<String>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT seat_no FROM seat WHERE schedule_id=?")) {
            ps.setInt(1, scheduleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) existing.add(rs.getString(1));
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO seat(schedule_id,seat_no,seat_status) VALUES (?,?,N'可售')")) {
            for (int j = 1; j <= newTotal; j++) {
                String seatNo = seatNo(j);
                if (existing.contains(seatNo)) continue;
                ps.setInt(1, scheduleId);
                ps.setString(2, seatNo);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private String seatNo(int index) {
        return "" + (char) ('A' + (index - 1) / 10) + (((index - 1) % 10) + 1);
    }

    /**
     * 取消班次（逻辑删除）+ 自动退票事务。
     * 业务规则：班次发船后禁止取消。
     * 处理范围：待支付订单取消、已支付/已检票订单全额退款并写资金流水。
     *
     * @return null 表示成功；否则返回不能取消的原因
     */
    public String cancelSchedule(int scheduleId) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // 1) 锁班次并校验是否允许取消
            String scheduleStatus;
            Timestamp departTime;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status, depart_time FROM schedule "
                  + "WITH (UPDLOCK, HOLDLOCK) WHERE id=? AND is_deleted=0")) {
                ps.setInt(1, scheduleId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        conn.rollback();
                        return "班次不存在或已取消";
                    }
                    scheduleStatus = rs.getString("status");
                    departTime = rs.getTimestamp("depart_time");
                }
            }
            if ("已发船".equals(scheduleStatus) || "已完成".equals(scheduleStatus)
                    || departTime == null
                    || !departTime.after(new Timestamp(System.currentTimeMillis()))) {
                conn.rollback();
                return "班次已发船或已完成，不能取消";
            }

            // 2) 锁订单，避免与支付/退票/改签并发；已完成订单不允许被系统取消
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM [order] WITH (UPDLOCK, HOLDLOCK) "
                  + "WHERE schedule_id=? AND status=N'已完成'")) {
                ps.setInt(1, scheduleId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        conn.rollback();
                        return "班次存在已完成订单，不能取消";
                    }
                }
            }

            // 3) 已支付/已检票订单：全额退款，并写资金流水
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO payment_log(order_id,order_no,type,amount,direction,remark) "
                  + "SELECT id, order_no, N'退票退款', total_amount, 'out', "
                  + "N'班次取消系统自动退票' FROM [order] "
                  + "WHERE schedule_id=? AND status IN (N'已支付',N'已检票')")) {
                ps.setInt(1, scheduleId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE [order] SET status=N'已退票', refund_amount=total_amount, "
                  + "refund_reason=N'班次取消系统自动退票', refund_time=GETDATE(), "
                  + "refund_eta=DATEADD(DAY,3,GETDATE()), cancel_time=GETDATE() "
                  + "WHERE schedule_id=? AND status IN (N'已支付',N'已检票')")) {
                ps.setInt(1, scheduleId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE [order] SET status=N'已取消', cancel_time=GETDATE() "
                  + "WHERE schedule_id=? AND status=N'待支付'")) {
                ps.setInt(1, scheduleId);
                ps.executeUpdate();
            }

            // 4) 船票状态跟随订单
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ticket SET status=N'已取消' "
                  + "WHERE schedule_id=? AND status=N'待支付'")) {
                ps.setInt(1, scheduleId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ticket SET status=N'已退票' "
                  + "WHERE schedule_id=? AND status IN (N'已支付',N'已检票')")) {
                ps.setInt(1, scheduleId);
                ps.executeUpdate();
            }

            // 5) 座位恢复可售
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE seat SET seat_status=N'可售', lock_time=NULL "
                  + "WHERE schedule_id=? AND seat_status<>N'可售'")) {
                ps.setInt(1, scheduleId);
                ps.executeUpdate();
            }

            // 6) 班次逻辑删除
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE schedule SET is_deleted=1, status=N'已取消', sold_seats=0 WHERE id=?")) {
                ps.setInt(1, scheduleId);
                ps.executeUpdate();
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

    private Schedule map(ResultSet rs) throws SQLException {
        Schedule s = new Schedule();
        s.setId(rs.getInt("id"));
        s.setRouteId(rs.getInt("route_id"));
        s.setShipId(rs.getInt("ship_id"));
        s.setDepartTime(rs.getTimestamp("depart_time"));
        s.setArriveTime(rs.getTimestamp("arrive_time"));
        s.setPrice(rs.getBigDecimal("price"));
        s.setTotalSeats(rs.getInt("total_seats"));
        s.setSoldSeats(rs.getInt("sold_seats"));
        s.setStatus(rs.getString("status"));
        s.setDeleted(rs.getBoolean("is_deleted"));
        s.setCreateTime(rs.getTimestamp("create_time"));
        // 联表字段（可能别名不存在时为 null，用 try 容忍）
        try { s.setRouteName(rs.getString("route_name")); } catch (Exception ignored) { }
        try { s.setStartPort(rs.getString("start_port")); } catch (Exception ignored) { }
        try { s.setEndPort(rs.getString("end_port")); } catch (Exception ignored) { }
        try { s.setShipName(rs.getString("ship_name")); } catch (Exception ignored) { }
        return s;
    }
}
