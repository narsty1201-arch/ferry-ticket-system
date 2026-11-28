package com.ferry.dao;

import com.ferry.entity.Route;
import com.ferry.util.DBUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** 航线 DAO：增删改查 + 逻辑删除（不做物理 DELETE） */
public class RouteDAO {

    /** 查询所有未删除航线 */
    public List<Route> findAll() {
        List<Route> list = new ArrayList<>();
        String sql = "SELECT * FROM route WHERE is_deleted = 0 ORDER BY id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** 查询启用航线的港口，供班次筛选下拉框使用。 */
    public List<String> findActivePorts() {
        List<String> ports = new ArrayList<>();
        String sql = "SELECT port FROM ("
                   + "SELECT start_port AS port FROM route "
                   + "WHERE is_deleted=0 AND status='active' "
                   + "UNION "
                   + "SELECT end_port AS port FROM route "
                   + "WHERE is_deleted=0 AND status='active'"
                   + ") p ORDER BY port";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ports.add(rs.getString("port"));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ports;
    }

    public Route findById(int id) {
        String sql = "SELECT * FROM route WHERE id = ? AND is_deleted = 0";
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

    /** 新增航线；返回影响行数，重复航线由唯一约束拦截 */
    public int insert(Route r) throws SQLException {
        String sql = "INSERT INTO route(route_name,start_port,end_port,duration_min,description) VALUES (?,?,?,?,?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.getRouteName());
            ps.setString(2, r.getStartPort());
            ps.setString(3, r.getEndPort());
            ps.setInt(4, r.getDurationMin());
            ps.setString(5, r.getDescription());
            return ps.executeUpdate();
        }
    }

    public int update(Route r) throws SQLException {
        String sql = "UPDATE route SET route_name=?,start_port=?,end_port=?,duration_min=?,description=? WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.getRouteName());
            ps.setString(2, r.getStartPort());
            ps.setString(3, r.getEndPort());
            ps.setInt(4, r.getDurationMin());
            ps.setString(5, r.getDescription());
            ps.setInt(6, r.getId());
            return ps.executeUpdate();
        }
    }

    /** 逻辑删除：打 is_deleted 标记，不物理删除 */
    public int logicDelete(int id) throws SQLException {
        String sql = "UPDATE route SET is_deleted = 1 WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        }
    }

    /** 停开/启用航线 */
    public int changeStatus(int id, String status) throws SQLException {
        String sql = "UPDATE route SET status = ? WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            return ps.executeUpdate();
        }
    }

    private Route map(ResultSet rs) throws SQLException {
        Route r = new Route();
        r.setId(rs.getInt("id"));
        r.setRouteName(rs.getString("route_name"));
        r.setStartPort(rs.getString("start_port"));
        r.setEndPort(rs.getString("end_port"));
        r.setDurationMin(rs.getInt("duration_min"));
        r.setDescription(rs.getString("description"));
        r.setStatus(rs.getString("status"));
        r.setDeleted(rs.getBoolean("is_deleted"));
        return r;
    }
}
