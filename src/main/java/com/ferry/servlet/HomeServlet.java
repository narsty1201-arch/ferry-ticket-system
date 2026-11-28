package com.ferry.servlet;

import com.ferry.dao.OrderDAO;
import com.ferry.dao.RouteDAO;
import com.ferry.dao.ScheduleDAO;
import com.ferry.entity.Schedule;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 角色首页与乘客班次页。
 * 首页和班次页统一走 Servlet，避免把业务查询散落在 JSP 中；
 * AuthFilter 会拒绝浏览器直接请求角色目录下的 JSP。
 */
@WebServlet({"/admin/home", "/seller/home", "/passenger/home", "/passenger/schedules"})
public class HomeServlet extends HttpServlet {
    private final ScheduleDAO scheduleDAO = new ScheduleDAO();
    private final RouteDAO routeDAO = new RouteDAO();
    private final OrderDAO orderDAO = new OrderDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        String path = req.getServletPath();

        if ("/passenger/schedules".equals(path)) {
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
            List<Schedule> bookable = invalidDate
                    ? new ArrayList<Schedule>()
                    : scheduleDAO.searchBookable(startPort, endPort, travelDate);

            req.setAttribute("schedules", bookable);
            req.setAttribute("ports", routeDAO.findActivePorts());
            req.setAttribute("startPort", startPort);
            req.setAttribute("endPort", endPort);
            req.setAttribute("travelDate", travelDateText);
            req.setAttribute("msg", escapeHtml(msg));
            req.getRequestDispatcher("/passenger/schedules.jsp").forward(req, resp);
            return;
        }

        String view;
        if ("/admin/home".equals(path)) {
            view = "/admin/home.jsp";
        } else if ("/seller/home".equals(path)) {
            view = "/seller/home.jsp";
        } else {
            view = "/passenger/home.jsp";
        }
        req.getRequestDispatcher(view).forward(req, resp);
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
