package com.ferry.servlet;

import com.ferry.dao.UserDAO;
import com.ferry.entity.User;
import com.ferry.util.MD5Util;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

/** 管理员 - 用户管理：查询全部用户、启用/禁用、重置密码 */
@WebServlet("/admin/users")
public class AdminUserServlet extends HttpServlet {
    private final UserDAO dao = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isAdmin(req, resp)) return;

        String role = req.getParameter("role");
        String keyword = req.getParameter("keyword");
        if (role == null) role = "all";
        if (keyword == null) keyword = "";

        List<User> users = dao.findAll(role, keyword);
        req.setAttribute("users", users);
        req.setAttribute("role", role);
        req.setAttribute("keyword", keyword);
        req.setAttribute("msg", req.getParameter("msg"));
        req.getRequestDispatcher("/admin/users.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isAdmin(req, resp)) return;

        HttpSession session = req.getSession(false);
        User current = (User) session.getAttribute("loginUser");
        String action = req.getParameter("action");
        int id = parseInt(req.getParameter("id"));
        String msg;

        if (id <= 0) {
            msg = "参数错误：未指定用户";
        } else if ("status".equals(action)) {
            if (id == current.getId()) {
                msg = "不能禁用当前登录的管理员账号";
            } else {
                String to = "disabled".equals(req.getParameter("to")) ? "disabled" : "normal";
                msg = dao.updateStatus(id, to) ? "用户状态已更新" : "状态更新失败";
            }
        } else if ("reset".equals(action)) {
            msg = dao.resetPassword(id, MD5Util.md5("123456")) ? "密码已重置为 123456" : "密码重置失败";
        } else {
            msg = "未知操作";
        }

        resp.sendRedirect(req.getContextPath() + "/admin/users?msg=" + enc(msg));
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

    private int parseInt(String s) {
        try { return Integer.parseInt(s == null ? "0" : s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }
}
