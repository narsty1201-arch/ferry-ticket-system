package com.ferry.servlet;

import com.ferry.dao.UserDAO;
import com.ferry.entity.User;
import com.ferry.util.MD5Util;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 乘客自助注册：用户名唯一、手机号11位、身份证18位，密码MD5加密 */
@WebServlet("/register")
public class RegisterServlet extends HttpServlet {
    private final UserDAO userDAO = new UserDAO();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String username = req.getParameter("username").trim();
        String password = req.getParameter("password").trim();
        String realName = req.getParameter("realName").trim();
        String phone = req.getParameter("phone").trim();
        String idCard = req.getParameter("idCard").trim().toUpperCase();

        String error = validate(username, password, realName, phone, idCard);
        if (error == null && userDAO.findByUsername(username) != null) {
            error = "用户名已存在";
        }
        if (error != null) {
            req.setAttribute("error", error);
            req.getRequestDispatcher("/register.jsp").forward(req, resp);
            return;
        }

        User u = new User();
        u.setUsername(username);
        u.setPassword(MD5Util.md5(password));
        u.setRealName(realName);
        u.setPhone(phone);
        u.setIdCard(idCard);

        try {
            userDAO.insertPassenger(u);
            resp.sendRedirect(req.getContextPath() + "/login.jsp?registered=1");
        } catch (Exception e) {
            e.printStackTrace();
            req.setAttribute("error", "注册失败，请稍后重试");
            req.getRequestDispatcher("/register.jsp").forward(req, resp);
        }
    }

    private String validate(String username, String password, String name, String phone, String idCard) {
        if (username.length() < 4 || username.length() > 20 || !username.matches("[A-Za-z0-9_]+")) {
            return "用户名需为4-20位字母、数字或下划线";
        }
        if (password.length() < 6) return "密码至少6位";
        if (name.isEmpty()) return "姓名不能为空";
        if (!com.ferry.util.IdentityUtil.validPhone(phone)) return "手机号必须是11位且以1开头";
        if (!com.ferry.util.IdentityUtil.validIdCard(idCard)) return "身份证号格式或校验位不正确";
        return null;
    }
}
