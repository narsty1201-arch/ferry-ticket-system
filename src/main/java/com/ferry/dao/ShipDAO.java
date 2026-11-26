package com.ferry.dao;

import com.ferry.entity.Ship;
import com.ferry.util.DBUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** 船舶 DAO：增删改查 + 逻辑删除 + 新增时生成第一层船舶座位 */
public class ShipDAO {

    public List<Ship> findAll() {
        List<Ship> list = new ArrayList<>();
        String sql = "SELECT * FROM ship WHERE is_deleted = 0 ORDER BY id";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public Ship findById(int id) {
        String sql = "SELECT * FROM ship WHERE id = ? AND is_deleted = 0";
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

    /**
     * 新增船舶 + 自动生成船舶座位（第一层 ship_seat），放在同一个事务里。
     * 这样新增船后，创建班次时就能从 ship_seat 初始化班次座位。
     */
    public int insertWithSeats(Ship ship) throws SQLException {
        Connection conn = null;
        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);   // 开启事务

            // 1. 插入船舶，取回自增主键
            String sqlShip = "INSERT INTO ship(ship_name,total_seats,ship_type,description) VALUES (?,?,?,?)";
            int shipId;
            try (PreparedStatement ps = conn.prepareStatement(sqlShip, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, ship.getShipName());
                ps.setInt(2, ship.getTotalSeats());
                ps.setString(3, ship.getShipType());
                ps.setString(4, ship.getDescription());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    shipId = keys.getInt(1);
                }
            }

            // 2. 生成船舶座位：seat_no 从 A1,A2...A10,B1... 依次编号
            String sqlSeat = "INSERT INTO ship_seat(ship_id,seat_no,seat_type) VALUES (?,?,N'普通座')";
            try (PreparedStatement ps = conn.prepareStatement(sqlSeat)) {
                for (int j = 1; j <= ship.getTotalSeats(); j++) {
                    String seatNo = "" + (char) ('A' + (j - 1) / 10) + (((j - 1) % 10) + 1);
                    ps.setInt(1, shipId);
                    ps.setString(2, seatNo);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();   // 提交事务
            return shipId;
        } catch (SQLException e) {
            if (conn != null) conn.rollback();   // 出错回滚，避免船和座位不一致
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                DBUtil.close(conn);
            }
        }
    }

    /** 修改船舶基本信息（座位数旧数据不改，避免影响已有班次） */
    public int update(Ship ship) throws SQLException {
        String sql = "UPDATE ship SET ship_name=?,ship_type=?,description=? WHERE id=?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ship.getShipName());
            ps.setString(2, ship.getShipType());
            ps.setString(3, ship.getDescription());
            ps.setInt(4, ship.getId());
            return ps.executeUpdate();
        }
    }

    /** 逻辑删除船舶 */
    public int logicDelete(int id) throws SQLException {
        String sql = "UPDATE ship SET is_deleted = 1 WHERE id = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate();
        }
    }

    /** 某船已被多少个未删除班次使用（删除前校验，任务书要求不能随便删在用数据） */
    public int usedBySchedules(int shipId) {
        String sql = "SELECT COUNT(*) FROM schedule WHERE ship_id=? AND is_deleted=0";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, shipId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    private Ship map(ResultSet rs) throws SQLException {
        Ship s = new Ship();
        s.setId(rs.getInt("id"));
        s.setShipName(rs.getString("ship_name"));
        s.setTotalSeats(rs.getInt("total_seats"));
        s.setShipType(rs.getString("ship_type"));
        s.setDescription(rs.getString("description"));
        s.setStatus(rs.getString("status"));
        s.setDeleted(rs.getBoolean("is_deleted"));
        return s;
    }
}
