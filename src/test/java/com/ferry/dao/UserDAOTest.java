package com.ferry.dao;

import com.ferry.entity.User;
import com.ferry.util.DBUtil;
import com.ferry.util.IdentityUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UserDAOTest {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final String VALID_ID_CARD = "11010519491231002X";

    private final UserDAO dao = new UserDAO();

    private String tag;
    private int operatorId;
    private int targetId;

    @Before
    public void setUp() throws Exception {
        tag = "UA" + Long.toString(System.currentTimeMillis(), 36) + SEQUENCE.incrementAndGet();
        try (Connection conn = DBUtil.getConnection()) {
            operatorId = insertUser(conn, tag + "_admin", "admin", nextPhone());
        }
    }

    @After
    public void tearDown() throws Exception {
        try (Connection conn = DBUtil.getConnection()) {
            execute(conn, "DELETE FROM user_admin_log WHERE operator_id=? OR target_user_id IN "
                    + "(SELECT id FROM [user] WHERE username LIKE ?)", operatorId, tag + "%");
            execute(conn, "DELETE FROM [user] WHERE username LIKE ?", tag + "%");
        }
    }

    @Test
    public void createsEditsResetsAndDisablesUserWithAuditTrail() throws Exception {
        assertTrue("测试身份证必须通过校验位验证", IdentityUtil.validIdCard(VALID_ID_CARD));
        User user = new User();
        user.setUsername(tag + "_passenger");
        user.setPassword(hash("123456"));
        user.setRole("passenger");
        user.setRealName("初始姓名");
        user.setPhone(nextPhone());
        user.setIdCard(VALID_ID_CARD.toLowerCase());

        targetId = dao.insertManaged(user, operatorId);
        assertTrue(targetId > 0);
        User saved = dao.findById(targetId);
        assertNotNull(saved);
        assertEquals("乘客", roleLabel(saved.getRole()));
        assertEquals(VALID_ID_CARD, saved.getIdCard());
        assertEquals("normal", saved.getStatus());

        try (Connection conn = DBUtil.getConnection()) {
            execute(conn, "UPDATE [user] SET fail_count=5, lock_until=DATEADD(MINUTE,10,GETDATE()) "
                    + "WHERE id=?", targetId);
        }

        User edit = new User();
        edit.setId(targetId);
        edit.setRole("seller");
        edit.setRealName("修改姓名");
        edit.setPhone(nextPhone());
        edit.setIdCard("");
        String newHash = hash("newpass123");
        assertTrue(dao.updateManaged(edit, newHash, operatorId));

        saved = dao.findById(targetId);
        assertEquals("seller", saved.getRole());
        assertEquals("修改姓名", saved.getRealName());
        assertNull("非乘客角色允许不填身份证", saved.getIdCard());
        assertEquals(newHash, saved.getPassword());
        assertEquals(0, saved.getFailCount());
        assertNull(saved.getLockUntil());

        assertTrue(dao.updateStatus(targetId, "disabled", operatorId));
        assertEquals("disabled", dao.findById(targetId).getStatus());

        assertEquals(1, logCount(targetId, "新增"));
        assertEquals(1, logCount(targetId, "编辑"));
        assertEquals(1, logCount(targetId, "重置密码"));
        assertEquals(1, logCount(targetId, "禁用"));

        List<java.util.Map<String, Object>> logs = dao.findAdminLogs(100);
        boolean found = false;
        for (java.util.Map<String, Object> row : logs) {
            if (user.getUsername().equals(row.get("target_name"))) {
                found = true;
                assertNotNull(row.get("operator_name"));
                break;
            }
        }
        assertTrue("审计日志应能被用户管理页读取", found);
    }

    @Test
    public void duplicateUsernameRollsBackWithoutDuplicateAuditLog() throws Exception {
        User first = new User();
        first.setUsername(tag + "_duplicate");
        first.setPassword(hash("123456"));
        first.setRole("seller");
        first.setRealName("售出一号");
        first.setPhone(nextPhone());
        targetId = dao.insertManaged(first, operatorId);

        User second = new User();
        second.setUsername(tag + "_duplicate");
        second.setPassword(hash("123456"));
        second.setRole("seller");
        second.setRealName("售出二号");
        second.setPhone(nextPhone());
        try {
            dao.insertManaged(second, operatorId);
            fail("重复用户名应由数据库唯一约束拒绝");
        } catch (SQLException expected) {
            assertTrue(expected.getErrorCode() == 2601 || expected.getErrorCode() == 2627);
        }
        assertEquals(1, countUsers(first.getUsername()));
        assertEquals(1, logCount(targetId, "新增"));
    }

    @Test
    public void editingWithoutPasswordPreservesExistingCredential() throws Exception {
        User user = new User();
        user.setUsername(tag + "_keep");
        user.setPassword(hash("origin-password"));
        user.setRole("seller");
        user.setRealName("保留密码");
        user.setPhone(nextPhone());
        targetId = dao.insertManaged(user, operatorId);
        String before = dao.findById(targetId).getPassword();

        User edit = new User();
        edit.setId(targetId);
        edit.setRole("seller");
        edit.setRealName("只改姓名");
        edit.setPhone(nextPhone());
        assertTrue(dao.updateManaged(edit, null, operatorId));

        assertEquals(before, dao.findById(targetId).getPassword());
        assertEquals(1, logCount(targetId, "编辑"));
        assertEquals(0, logCount(targetId, "重置密码"));
    }

    private int logCount(int targetUserId, String action) throws SQLException {
        return queryInt("SELECT COUNT(*) FROM user_admin_log "
                + "WHERE target_user_id=? AND action=?", targetUserId, action);
    }

    private int countUsers(String username) throws SQLException {
        return queryInt("SELECT COUNT(*) FROM [user] WHERE username=?", username);
    }

    private int queryInt(String sql, Object... params) throws SQLException {
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int insertUser(Connection conn, String username, String role, String phone)
            throws SQLException {
        return insert(conn, "INSERT INTO [user]"
                + "(username,password,role,real_name,phone,status,fail_count) "
                + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,'normal',0)",
                username, hash("123456"), role, "测试" + role, phone);
    }

    private int insert(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getInt(1);
            }
        }
    }

    private void execute(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        }
    }

    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                ps.setObject(i + 1, null);
            } else if (params[i] instanceof Integer) {
                ps.setInt(i + 1, (Integer) params[i]);
            } else {
                ps.setString(i + 1, String.valueOf(params[i]));
            }
        }
    }

    private String roleLabel(String role) {
        if ("admin".equals(role)) return "管理员";
        if ("seller".equals(role)) return "售票员";
        return "乘客";
    }

    private String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String nextPhone() {
        return String.format("135%08d", SEQUENCE.incrementAndGet() % 100000000L);
    }
}
