package com.ferry.servlet;

import com.ferry.dao.OrderDAO;
import com.ferry.dao.RouteDAO;
import com.ferry.dao.ScheduleDAO;
import com.ferry.entity.Schedule;
import com.ferry.entity.User;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/** 售票员售票台：列出可售票班次，进入选座页代客下单 */
@WebServlet("/seller/sell")
public class SellerSellServlet extends HttpServlet {
    private final ScheduleDAO scheduleDAO = new ScheduleDAO();
    private final RouteDAO routeDAO = new RouteDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isSeller(req, resp)) return;

        try {
            orderDAO.releaseExpiredLocks();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        String startPort = trimToNull(req.getParameter("startPort"));
        String endPort = trimToNull(req.getParameter("endPort"));
        String travelDateText = trimToNull(req.getParameter("travelDate"));
        String msg = trimToNull(req.getParameter("msg"));

        java.sql.Date travelDate = null;
        boolean invalidDate = false;
        if (travelDateText != null) {
            try {
                travelDate = java.sql.Date.valueOf(travelDateText);
            } catch (IllegalArgumentException e) {
                invalidDate = true;
                travelDateText = null;
                msg = "日期格式不正确，请重新选择日期";
            }
        }
        List<Schedule> list = invalidDate
                ? new java.util.ArrayList<Schedule>()
                : scheduleDAO.searchBookable(startPort, endPort, travelDate);

        req.setAttribute("list", list);
        req.setAttribute("ports", routeDAO.findActivePorts());
        req.setAttribute("startPort", startPort);
        req.setAttribute("endPort", endPort);
        req.setAttribute("travelDate", travelDateText);
        req.setAttribute("msg", escapeHtml(msg));
        req.getRequestDispatcher("/seller/sell.jsp").forward(req, resp);
    }

    private boolean isSeller(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession(false);
        User u = s == null ? null : (User) s.getAttribute("loginUser");
        if (u == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return false;
        }
        if (!"seller".equals(u.getRole()) && !"admin".equals(u.getRole())) {
            resp.sendRedirect(req.getContextPath() + "/passenger/home");
            return false;
        }
        return true;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String escapeHtml(String value) {
        if (value == null) return null;
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
