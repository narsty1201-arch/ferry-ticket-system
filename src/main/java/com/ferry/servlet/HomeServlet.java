package com.ferry.servlet;

import com.ferry.dao.OrderDAO;
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
import java.util.Date;
import java.util.List;

/**
 * 角色首页与乘客班次页。
 * 首页和班次页统一走 Servlet，避免把业务查询散落在 JSP 中；
 * AuthFilter 会拒绝浏览器直接请求角色目录下的 JSP。
 */
@WebServlet({"/admin/home", "/seller/home", "/passenger/home", "/passenger/schedules"})
public class HomeServlet extends HttpServlet {
    private final ScheduleDAO scheduleDAO = new ScheduleDAO();
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
            List<Schedule> bookable = new ArrayList<Schedule>();
            Date now = new Date();
            for (Schedule s : scheduleDAO.findAll()) {
                if ("售票中".equals(s.getStatus()) && s.getRemainSeats() > 0
                        && s.getDepartTime() != null && s.getDepartTime().after(now)) {
                    bookable.add(s);
                }
            }
            req.setAttribute("schedules", bookable);
            req.setAttribute("msg", req.getParameter("msg"));
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
}
