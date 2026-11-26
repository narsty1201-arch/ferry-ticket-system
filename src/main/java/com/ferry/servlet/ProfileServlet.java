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

/** 乘客个人中心：查看资料、修改资料、修改密码 */
@WebServlet("/passenger/profile")
public class ProfileServlet extends HttpServlet {
    private final UserDAO dao = new UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        User u = currentUser(req, resp);
        if (u == null) return;

        User latest = dao.findById(u.getId());
        if (latest != null) {
            refreshSession(req, latest);
            req.setAttribute("profile", latest);
        } else {
            req.setAttribute("profile", u);
        }
        req.setAttribute("msg", req.getParameter("msg"));
        req.getRequestDispatcher("/passenger/profile.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        req.setCharacterEncoding("UTF-8");
        User u = currentUser(req, resp);
        if (u == null) return;

        String action = req.getParameter("action");
        User latest = dao.findById(u.getId());
        String msg;

        if ("info".equals(action)) {
            String realName = req.getParameter("realName").trim();
            String phone = req.getParameter("phone").trim();
            String idCard = req.getParameter("idCard").trim();
            if (!com.ferry.util.IdentityUtil.validName(realName)
                    || !com.ferry.util.IdentityUtil.validPhone(phone)
                    || (!idCard.isEmpty() && !com.ferry.util.IdentityUtil.validIdCard(idCard))) {
                msg = "资料格式不正确：姓名2~20字，手机11位，身份证18位真实号码或留空";
            } else {
                boolean ok = dao.updateProfile(u.getId(), realName, phone, idCard);
                if (ok) refreshSession(req, dao.findById(u.getId()));
                msg = ok ? "资料已更新" : "资料更新失败";
            }
        } else if ("password".equals(action)) {
            String oldPwd = req.getParameter("oldPassword").trim();
            String newPwd = req.getParameter("newPassword").trim();
            if (newPwd.length() < 6) {
                msg = "新密码至少6位";
            } else if (latest == null || !MD5Util.md5(oldPwd).equals(latest.getPassword())) {
                msg = "原密码不正确";
            } else {
                boolean ok = dao.updatePassword(u.getId(), MD5Util.md5(newPwd));
                msg = ok ? "密码已修改，下次登录请用新密码" : "密码修改失败";
            }
        } else {
            msg = "未知操作";
        }

        resp.sendRedirect(req.getContextPath() + "/passenger/profile?msg=" + enc(msg));
    }

    private User currentUser(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession(false);
        User u = s == null ? null : (User) s.getAttribute("loginUser");
        if (u == null) resp.sendRedirect(req.getContextPath() + "/login.jsp");
        return u;
    }

    private void refreshSession(HttpServletRequest req, User u) {
        if (u == null) return;
        HttpSession s = req.getSession(false);
        if (s == null) return;
        s.setAttribute("loginUser", u);
        s.setAttribute("userName", u.getRealName());
        s.setAttribute("userCard", u.getIdCard());
        s.setAttribute("userPhone", u.getPhone());
    }

    private String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }
}
