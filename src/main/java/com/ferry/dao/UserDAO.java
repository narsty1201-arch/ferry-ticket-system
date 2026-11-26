package com.ferry.dao;

import com.ferry.entity.User;
import com.ferry.util.DBUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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
            ps.setString(5, u.getIdCard());
            return ps.executeUpdate();
        }
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

    /** 管理员：启用 / 禁用用户 */
    public boolean updateStatus(int id, String status) {
        String sql = "UPDATE [user] SET status=? WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** 管理员：重置密码（传入 MD5），同时解除失败锁定 */
    public boolean resetPassword(int id, String md5Password) {
        String sql = "UPDATE [user] SET password=?, fail_count=0, lock_until=NULL WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, md5Password);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
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
            ps.setString(3, idCard == null || idCard.trim().isEmpty() ? null : idCard.trim());
            ps.setInt(4, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** 修改密码（传入 MD5 后的值） */
    public boolean updatePassword(int id, String md5Password) {
        String sql = "UPDATE [user] SET password=? WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, md5Password);
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
}
