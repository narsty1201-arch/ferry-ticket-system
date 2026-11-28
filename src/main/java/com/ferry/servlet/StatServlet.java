package com.ferry.servlet;

import com.ferry.dao.StatDAO;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;

/** 统计报表控制器：聚合 SQL 结果汇总到一页 */
@WebServlet("/admin/stats")
public class StatServlet extends HttpServlet {
    private final StatDAO dao = new StatDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession s = req.getSession(false);
        com.ferry.entity.User u = s == null ? null : (com.ferry.entity.User) s.getAttribute("loginUser");
        if (u == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }
        // 权限：统计报表仅管理员可见，普通乘客/售票员不得查看全局营收
        if (!"admin".equals(u.getRole())) {
            resp.sendRedirect(req.getContextPath() + "/passenger/home");
            return;
        }
        Map<String, Object> ov = dao.overview();
        req.setAttribute("ov", ov);
        req.setAttribute("byRoute", dao.byRoute());
        req.setAttribute("bySchedule", dao.bySchedule());
        req.setAttribute("sellers", dao.sellerPerformance());
        req.setAttribute("statusDist", dao.orderStatus());
        req.getRequestDispatcher("/admin/stats.jsp").forward(req, resp);
    }
}
