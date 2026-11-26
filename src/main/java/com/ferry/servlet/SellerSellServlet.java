package com.ferry.servlet;

import com.ferry.dao.OrderDAO;
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
        List<Schedule> all = scheduleDAO.findAll();
        // 只展示真正可代售的班次，页面数量与可操作项一致
        List<Schedule> list = new java.util.ArrayList<Schedule>();
        for (Schedule s : all) {
            if ("售票中".equals(s.getStatus()) && s.getRemainSeats() > 0) list.add(s);
        }
        req.setAttribute("list", list);
        req.setAttribute("msg", req.getParameter("msg"));
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
            resp.sendRedirect(req.getContextPath() + "/passenger/home.jsp");
            return false;
        }
        return true;
    }
}
