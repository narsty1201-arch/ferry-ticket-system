package com.ferry.dao;

import com.ferry.entity.User;
import com.ferry.util.DBUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 用户数据访问层：所有 SQL 集中在这里，Servlet 只负责调度 */
public class UserDAO {

    /** 按用户名查询（登录用） */
    public User findByUsername(String username) {
        String sql = "SELECT * FROM [user] WHERE username = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /** 注册乘客 */
    public int insertPassenger(User u) throws SQLException {
        String sql = "INSERT INTO [user](username,password,role,real_name,phone,id_card,status) "
                   + "VALUES (?,?,'passenger',?,?,?,'normal')";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, u.getUsername());
            ps.setString(2, u.getPassword());   // 调用方已 MD5 加密
            ps.setString(3, u.getRealName());
            ps.setString(4, u.getPhone());
            ps.setString(5, normalizeIdCard(u.getIdCard()));
            return ps.executeUpdate();
        }
    }

    /** 管理员新增任意角色账号，并在同一事务内写入审计日志。 */
    public int insertManaged(User u, int operatorId) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);
            int targetId;
            String sql = "INSERT INTO [user]"
                       + "(username,password,role,real_name,phone,id_card,status) "
                       + "VALUES (?,?,?,?,?,?,'normal')";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, u.getUsername());
                ps.setString(2, u.getPassword());
                ps.setString(3, u.getRole());
                ps.setString(4, u.getRealName());
                ps.setString(5, u.getPhone());
                ps.setString(6, normalizeIdCard(u.getIdCard()));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("新增用户未返回主键");
                    targetId = keys.getInt(1);
                }
            }
            writeAdminLog(conn, operatorId, targetId, "新增",
                    "角色=" + u.getRole() + "；姓名=" + u.getRealName());
            conn.commit();
            return targetId;
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
     * 管理员编辑账号资料，可选同时重置密码。
     * 用户名不可修改；密码只保存调用方传入的 MD5，不记录到审计日志。
     */
    public boolean updateManaged(User u, String encodedPassword, int operatorId)
            throws SQLException {
        boolean resetPassword = encodedPassword != null && !encodedPassword.trim().isEmpty();
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);
            String sql = "UPDATE [user] SET real_name=?, phone=?, id_card=?, role=?, "
                       + "password=CASE WHEN ? IS NULL THEN password ELSE ? END, "
                       + "fail_count=CASE WHEN ? IS NULL THEN fail_count ELSE 0 END, "
                       + "lock_until=CASE WHEN ? IS NULL THEN lock_until ELSE NULL END "
                       + "WHERE id=?";
            int affected;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                String password = resetPassword ? encodedPassword : null;
                ps.setString(1, u.getRealName());
                ps.setString(2, u.getPhone());
                ps.setString(3, normalizeIdCard(u.getIdCard()));
                ps.setString(4, u.getRole());
                ps.setString(5, password);
                ps.setString(6, password);
                ps.setString(7, password);
                ps.setString(8, password);
                ps.setInt(9, u.getId());
                affected = ps.executeUpdate();
            }
            if (affected == 0) {
                conn.rollback();
                return false;
            }
            writeAdminLog(conn, operatorId, u.getId(), "编辑",
                    "角色=" + u.getRole() + "；姓名=" + u.getRealName());
            if (resetPassword) {
                writeAdminLog(conn, operatorId, u.getId(), "重置密码",
                        "设置新密码并解除失败锁定");
            }
            conn.commit();
            return true;
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

    /** 管理员启用/禁用账号，并在同一事务内写入审计日志。 */
    public boolean updateStatus(int id, String status, int operatorId) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);
            int affected;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE [user] SET status=? WHERE id=?")) {
                ps.setString(1, status);
                ps.setInt(2, id);
                affected = ps.executeUpdate();
            }
            if (affected == 0) {
                conn.rollback();
                return false;
            }
            writeAdminLog(conn, operatorId, id, "disabled".equals(status) ? "禁用" : "启用",
                    "账号状态=" + ("disabled".equals(status) ? "禁用" : "正常"));
            conn.commit();
            return true;
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

    /** 除指定账号外，当前仍可登录的管理员数量。用于防止禁用/降级最后一个管理员。 */
    public int countActiveAdminsExcept(int userId) {
        String sql = "SELECT COUNT(*) FROM [user] "
                   + "WHERE role='admin' AND status='normal' AND id<>?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /** 最近的管理员账号操作日志，供用户管理页审计展示。 */
    public List<Map<String, Object>> findAdminLogs(int limit) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        String sql = "SELECT TOP (?) l.action, l.detail, "
                   + "CONVERT(varchar(16), l.create_time, 120) AS create_time, "
                   + "op.username AS operator_name, tu.username AS target_name "
                   + "FROM user_admin_log l "
                   + "JOIN [user] op ON op.id=l.operator_id "
                   + "JOIN [user] tu ON tu.id=l.target_user_id "
                   + "ORDER BY l.id DESC";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("action", rs.getString("action"));
                    row.put("detail", rs.getString("detail"));
                    row.put("create_time", rs.getString("create_time"));
                    row.put("operator_name", rs.getString("operator_name"));
                    row.put("target_name", rs.getString("target_name"));
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private void writeAdminLog(Connection conn, int operatorId, int targetUserId,
            String action, String detail) throws SQLException {
        String safeDetail = detail == null ? "" : detail;
        if (safeDetail.length() > 500) safeDetail = safeDetail.substring(0, 500);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO user_admin_log(operator_id,target_user_id,action,detail) "
              + "VALUES (?,?,?,?)")) {
            ps.setInt(1, operatorId);
            ps.setInt(2, targetUserId);
            ps.setString(3, action);
            ps.setString(4, safeDetail);
            ps.executeUpdate();
        }
    }

    /**
     * 代客购票：按身份证匹配已注册的乘客账号。
     * 找到返回乘客 user.id；未注册返回 null（订单仍可出票，真实身份保存在 ticket 表）。
     */
    public Integer findPassengerIdByIdCard(String idCard) {
        String normalized = normalizeIdCard(idCard);
        if (normalized == null) return null;
        String sql = "SELECT TOP 1 id FROM [user] "
                   + "WHERE role='passenger' AND id_card IS NOT NULL "
                   + "AND UPPER(LTRIM(RTRIM(id_card)))=? ORDER BY id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalized);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /** 密码错误一次：fail_count+1，达到5次则锁定10分钟 */
    public void recordFail(String username) {
        String sql = "UPDATE [user] SET fail_count = fail_count + 1, "
                   + "lock_until = CASE WHEN fail_count + 1 >= 5 "
                   + "THEN DATEADD(MINUTE,10,GETDATE()) ELSE lock_until END "
                   + "WHERE username = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** 登录成功清零失败次数 */
    public void clearFail(String username) {
        String sql = "UPDATE [user] SET fail_count = 0, lock_until = NULL WHERE username = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** 管理员：按角色 + 关键字查询全部用户 */
    public List<User> findAll(String role, String keyword) {
        List<User> list = new ArrayList<User>();
        StringBuilder sql = new StringBuilder("SELECT * FROM [user] WHERE 1=1 ");
        boolean hasRole = role != null && !role.trim().isEmpty() && !"all".equals(role);
        boolean hasKw = keyword != null && !keyword.trim().isEmpty();
        if (hasRole) sql.append("AND role = ? ");
        if (hasKw)  sql.append("AND (username LIKE ? OR real_name LIKE ? OR phone LIKE ?) ");
        sql.append("ORDER BY id");
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (hasRole) ps.setString(idx++, role.trim());
            if (hasKw) {
                String kw = "%" + keyword.trim() + "%";
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

    /** 管理员：用户总数（筛选条件与列表一致） */
    public int countAll(String role, String keyword) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM [user] WHERE 1=1 ");
        appendFilter(sql, role, keyword);
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindFilter(ps, role, keyword);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /** 管理员：分页查询用户 */
    public List<User> findAll(String role, String keyword, int page, int pageSize) {
        List<User> list = new ArrayList<User>();
        StringBuilder sql = new StringBuilder("SELECT * FROM [user] WHERE 1=1 ");
        appendFilter(sql, role, keyword);
        sql.append("ORDER BY id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = bindFilter(ps, role, keyword);
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

    private void appendFilter(StringBuilder sql, String role, String keyword) {
        if (role != null && !role.trim().isEmpty() && !"all".equals(role)) {
            sql.append("AND role = ? ");
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append("AND (username LIKE ? OR real_name LIKE ? OR phone LIKE ?) ");
        }
    }

    private int bindFilter(PreparedStatement ps, String role, String keyword) throws SQLException {
        int idx = 1;
        if (role != null && !role.trim().isEmpty() && !"all".equals(role)) {
            ps.setString(idx++, role.trim());
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = "%" + keyword.trim() + "%";
            ps.setString(idx++, kw);
            ps.setString(idx++, kw);
            ps.setString(idx++, kw);
        }
        return idx;
    }

    /** 按ID查询用户（个人资料页用，取数据库最新值而非 Session 旧对象） */
    public User findById(int id) {
        String sql = "SELECT * FROM [user] WHERE id=?";
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

    /** 修改个人资料（姓名 / 手机 / 身份证） */
    public boolean updateProfile(int id, String realName, String phone, String idCard) {
        String sql = "UPDATE [user] SET real_name=?, phone=?, id_card=? WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, realName);
            ps.setString(2, phone);
            ps.setString(3, normalizeIdCard(idCard));
            ps.setInt(4, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** 修改密码（传入 MD5 后的值） */
    public boolean updatePassword(int id, String encodedPassword) {
        String sql = "UPDATE [user] SET password=? WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, encodedPassword);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setRole(rs.getString("role"));
        u.setRealName(rs.getString("real_name"));
        u.setPhone(rs.getString("phone"));
        u.setIdCard(rs.getString("id_card"));
        u.setStatus(rs.getString("status"));
        u.setFailCount(rs.getInt("fail_count"));
        u.setLockUntil(rs.getTimestamp("lock_until"));
        u.setCreateTime(rs.getTimestamp("create_time"));
        return u;
    }

    private String normalizeIdCard(String idCard) {
        if (idCard == null || idCard.trim().isEmpty()) return null;
        return idCard.trim().toUpperCase();
    }
}
