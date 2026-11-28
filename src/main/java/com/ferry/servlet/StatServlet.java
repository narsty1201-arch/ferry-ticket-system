package com.ferry.servlet;

import com.ferry.dao.RouteDAO;
import com.ferry.dao.StatDAO;
import com.ferry.entity.Route;
import com.ferry.util.DateRange;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** 统计报表控制器：聚合 SQL 结果汇总到一页 */
@WebServlet("/admin/stats")
public class StatServlet extends HttpServlet {
    private final StatDAO dao = new StatDAO();
    private final RouteDAO routeDAO = new RouteDAO();

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
        String startText = trim(req.getParameter("startDate"));
        String endText = trim(req.getParameter("endDate"));
        DateRange range = DateRange.parse(startText, endText);
        String filterError = range.error();

        Integer routeId = null;
        String routeText = trim(req.getParameter("routeId"));
        if (!routeText.isEmpty()) {
            try {
                routeId = Integer.valueOf(routeText);
            } catch (NumberFormatException e) {
                filterError = joinError(filterError, "航线筛选参数不正确");
            }
        }
        if (range.hasError()) {
            range = DateRange.all();
            routeId = null;
        }

        java.sql.Timestamp start = range.startInclusive();
        java.sql.Timestamp end = range.endExclusive();
        Map<String, Object> ov = dao.overview(start, end, routeId);
        req.setAttribute("ov", ov);
        req.setAttribute("byRoute", dao.byRoute(start, end, routeId));
        req.setAttribute("bySchedule", dao.bySchedule(start, end, routeId));
        req.setAttribute("sellers", dao.sellerPerformance(start, end, routeId));
        req.setAttribute("statusDist", dao.orderStatus(start, end, routeId));
        req.setAttribute("refundReasons", dao.refundReasons(start, end, routeId));
        List<Route> routes = routeDAO.findAll();
        req.setAttribute("routes", routes);
        req.setAttribute("startDate", startText);
        req.setAttribute("endDate", endText);
        req.setAttribute("routeId", routeId);
        req.setAttribute("filterError", filterError);
        req.getRequestDispatcher("/admin/stats.jsp").forward(req, resp);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String joinError(String current, String extra) {
        return current == null || current.isEmpty() ? extra : current + "；" + extra;
    }
}
