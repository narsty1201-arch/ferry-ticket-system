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
import java.util.Date;

/** 登录：校验账号密码 + 账号锁定 + 按角色跳转（照搬旧项目 LoginServlet 的套路） */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String username = req.getParameter("username").trim();
        String password = req.getParameter("password").trim();

        User user = userDAO.findByUsername(username);
        String error = null;

        if (user == null) {
            error = "账号不存在";
        } else if ("disabled".equals(user.getStatus())) {
            error = "账号已被禁用，请联系管理员";
        } else if (user.getLockUntil() != null && user.getLockUntil().after(new Date())) {
            error = "密码错误次数过多，账号已锁定10分钟";
        } else if (!MD5Util.md5(password).equals(user.getPassword())) {
            userDAO.recordFail(username);
            error = "密码错误，剩余次数：" + Math.max(0, 5 - user.getFailCount() - 1);
        }

        if (error != null) {
            req.setAttribute("error", error);
            req.setAttribute("username", username);
            req.getRequestDispatcher("/login.jsp").forward(req, resp);
            return;
        }

        // 登录成功：写 Session，清失败计数，按角色进入不同界面
        userDAO.clearFail(username);
        HttpSession session = req.getSession();
        session.setAttribute("loginUser", user);
        session.setAttribute("userId", user.getId());
        session.setAttribute("userName", user.getRealName());
        session.setAttribute("userCard", user.getIdCard());
        session.setAttribute("userPhone", user.getPhone());
        session.setAttribute("role", user.getRole());

        String ctx = req.getContextPath();
        if ("admin".equals(user.getRole())) {
            resp.sendRedirect(ctx + "/admin/home");
        } else if ("seller".equals(user.getRole())) {
            resp.sendRedirect(ctx + "/seller/home");
        } else {
            resp.sendRedirect(ctx + "/passenger/home");
        }
    }
}
