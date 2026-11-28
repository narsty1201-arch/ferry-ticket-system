package com.ferry.servlet;

import com.ferry.dao.UserDAO;
import com.ferry.entity.User;
import com.ferry.util.IdentityUtil;
import com.ferry.util.Page;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/** 管理员用户管理：查询、新增、编辑、密码设置、启用/禁用和审计日志。 */
@WebServlet("/admin/users")
public class AdminUserServlet extends HttpServlet {
    private static final Set<String> ROLES = Set.of("admin", "seller", "passenger");

    private final UserDAO dao = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isAdmin(req, resp)) return;

        String action = trim(req.getParameter("action"));
        if ("add".equals(action)) {
            User formUser = new User();
            formUser.setRole("passenger");
            req.setAttribute("formMode", "add");
            req.setAttribute("formUser", formUser);
        } else if ("edit".equals(action)) {
            int id = parseInt(req.getParameter("id"));
            User formUser = dao.findById(id);
            if (formUser == null) {
                req.setAttribute("msg", "用户不存在或已被删除");
            } else {
                req.setAttribute("formMode", "edit");
                req.setAttribute("formUser", formUser);
            }
        }
        render(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isAdmin(req, resp)) return;

        HttpSession session = req.getSession(false);
        User current = (User) session.getAttribute("loginUser");
        String action = trim(req.getParameter("action"));

        if ("create".equals(action)) {
            handleCreate(req, resp, current);
        } else if ("update".equals(action)) {
            handleUpdate(req, resp, current);
        } else if ("status".equals(action)) {
            handleStatus(req, resp, current);
        } else {
            redirect(req, resp, "未知操作");
        }
    }

    private void handleCreate(HttpServletRequest req, HttpServletResponse resp, User current)
            throws ServletException, IOException {
        User formUser = readForm(req);
        String password = trim(req.getParameter("password"));
        String confirm = trim(req.getParameter("confirmPassword"));
        String error = validate(formUser, true, password, confirm);
        if (error == null && dao.findByUsername(formUser.getUsername()) != null) {
            error = "用户名已存在";
        }
        if (error != null) {
            forwardForm(req, resp, "add", formUser, error);
            return;
        }

        formUser.setPassword(hashPassword(password));
        try {
            int id = dao.insertManaged(formUser, current.getId());
            redirect(req, resp, "账号 " + formUser.getUsername() + " 已创建（ID " + id + "）");
        } catch (SQLException e) {
            if (isDuplicate(e)) {
                forwardForm(req, resp, "add", formUser, "用户名已存在");
            } else {
                e.printStackTrace();
                forwardForm(req, resp, "add", formUser, "新增账号失败，请稍后重试");
            }
        }
    }

    private void handleUpdate(HttpServletRequest req, HttpServletResponse resp, User current)
            throws ServletException, IOException {
        int id = parseInt(req.getParameter("id"));
        User target = id <= 0 ? null : dao.findById(id);
        if (target == null) {
            redirect(req, resp, "用户不存在或参数错误");
            return;
        }

        User formUser = readForm(req);
        formUser.setId(target.getId());
        formUser.setUsername(target.getUsername());
        formUser.setStatus(target.getStatus());
        // 当前登录管理员不能给自己降级，避免即时失去后台入口。
        if (id == current.getId()) formUser.setRole(target.getRole());

        String password = trim(req.getParameter("newPassword"));
        String confirm = trim(req.getParameter("confirmPassword"));
        String error = validate(formUser, false, password, confirm);
        if (error == null && "admin".equals(target.getRole())
                && "normal".equals(target.getStatus()) && !"admin".equals(formUser.getRole())
                && dao.countActiveAdminsExcept(id) == 0) {
            error = "不能降级当前唯一可用管理员";
        }
        if (error != null) {
            forwardForm(req, resp, "edit", formUser, error);
            return;
        }

        String encodedPassword = password.isEmpty() ? null : hashPassword(password);
        try {
            boolean ok = dao.updateManaged(formUser, encodedPassword, current.getId());
            if (ok) {
                if (id == current.getId()) refreshSession(req, id);
                String msg = "账号 " + target.getUsername() + " 的资料已更新";
                if (encodedPassword != null) msg += "，密码已重置并解除锁定";
                redirect(req, resp, msg);
            } else {
                forwardForm(req, resp, "edit", formUser, "保存失败：用户不存在");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            forwardForm(req, resp, "edit", formUser, "保存失败，请稍后重试");
        }
    }

    private void handleStatus(HttpServletRequest req, HttpServletResponse resp, User current)
            throws IOException {
        int id = parseInt(req.getParameter("id"));
        User target = id <= 0 ? null : dao.findById(id);
        if (target == null) {
            redirect(req, resp, "用户不存在或参数错误");
            return;
        }
        if (id == current.getId()) {
            redirect(req, resp, "不能禁用当前登录的管理员账号");
            return;
        }
        String to = trim(req.getParameter("to"));
        if (!"normal".equals(to) && !"disabled".equals(to)) {
            redirect(req, resp, "账号状态参数不正确");
            return;
        }
        if ("disabled".equals(to) && "admin".equals(target.getRole())
                && "normal".equals(target.getStatus())
                && dao.countActiveAdminsExcept(id) == 0) {
            redirect(req, resp, "不能禁用当前唯一可用管理员");
            return;
        }
        try {
            boolean ok = dao.updateStatus(id, to, current.getId());
            redirect(req, resp, ok ? ("disabled".equals(to) ? "账号已禁用" : "账号已启用")
                    : "状态更新失败：用户不存在");
        } catch (SQLException e) {
            e.printStackTrace();
            redirect(req, resp, "状态更新失败，请稍后重试");
        }
    }

    private void render(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String role = trim(req.getParameter("role"));
        if (!ROLES.contains(role)) role = "all";
        String keyword = trim(req.getParameter("keyword"));

        int pageSize = 10;
        int total = dao.countAll(role, keyword);
        int page = Page.clamp(Page.parse(req.getParameter("page"), 1), total, pageSize);
        List<User> users = dao.findAll(role, keyword, page, pageSize);
        req.setAttribute("pageData", new Page<User>(users, page, pageSize, total));
        req.setAttribute("pageUrl", "users?role=" + enc(role)
                + "&keyword=" + enc(keyword) + "&page=");
        req.setAttribute("role", role);
        req.setAttribute("keyword", keyword);
        req.setAttribute("adminLogs", dao.findAdminLogs(20));
        if (req.getAttribute("msg") == null) req.setAttribute("msg", req.getParameter("msg"));
        req.getRequestDispatcher("/admin/users.jsp").forward(req, resp);
    }

    private void forwardForm(HttpServletRequest req, HttpServletResponse resp,
            String mode, User formUser, String error) throws ServletException, IOException {
        req.setAttribute("formMode", mode);
        req.setAttribute("formUser", formUser);
        req.setAttribute("formError", error);
        render(req, resp);
    }

    private User readForm(HttpServletRequest req) {
        User user = new User();
        user.setId(parseInt(req.getParameter("id")));
        user.setUsername(trim(req.getParameter("username")));
        user.setRole(trim(req.getParameter("role")));
        user.setRealName(trim(req.getParameter("realName")));
        user.setPhone(trim(req.getParameter("phone")));
        user.setIdCard(trim(req.getParameter("idCard")).toUpperCase());
        user.setStatus("normal");
        return user;
    }

    private String validate(User user, boolean creating, String password, String confirm) {
        if (creating) {
            if (!user.getUsername().matches("[A-Za-z0-9_]{4,20}")) {
                return "用户名需为4-20位字母、数字或下划线";
            }
        }
        if (!ROLES.contains(user.getRole())) return "账号角色不正确";
        if (!IdentityUtil.validName(user.getRealName())) return "姓名需为2-20个字符";
        if (!IdentityUtil.validPhone(user.getPhone())) return "手机号必须是11位且以1开头";
        String idCard = trim(user.getIdCard());
        if ("passenger".equals(user.getRole()) && idCard.isEmpty()) {
            return "乘客账号必须填写身份证号";
        }
        if (!idCard.isEmpty() && !IdentityUtil.validIdCard(idCard)) {
            return "身份证号格式或校验位不正确";
        }
        if (creating && password.length() < 6) return "初始密码至少6位";
        if (!creating && !password.isEmpty() && password.length() < 6) return "新密码至少6位";
        if (!creating && password.isEmpty() && !confirm.isEmpty()) {
            return "请输入新密码，或清空确认密码";
        }
        if ((!password.isEmpty() || creating) && !password.equals(confirm)) {
            return "两次输入的密码不一致";
        }
        return null;
    }

    private boolean isDuplicate(SQLException e) {
        return e.getErrorCode() == 2601 || e.getErrorCode() == 2627;
    }

    private String hashPassword(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算密码摘要", e);
        }
    }

    private void refreshSession(HttpServletRequest req, int userId) {
        HttpSession session = req.getSession(false);
        if (session == null) return;
        User latest = dao.findById(userId);
        if (latest == null) return;
        session.setAttribute("loginUser", latest);
        session.setAttribute("userName", latest.getRealName());
        session.setAttribute("userCard", latest.getIdCard());
        session.setAttribute("userPhone", latest.getPhone());
    }

    private boolean isAdmin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        User u = session == null ? null : (User) session.getAttribute("loginUser");
        if (u == null || !"admin".equals(u.getRole())) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return false;
        }
        return true;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value == null ? "0" : value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private void redirect(HttpServletRequest req, HttpServletResponse resp, String msg)
            throws IOException {
        resp.sendRedirect(req.getContextPath() + "/admin/users?msg=" + enc(msg));
    }

    private String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
