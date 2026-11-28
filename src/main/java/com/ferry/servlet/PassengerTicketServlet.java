package com.ferry.servlet;

import com.ferry.dao.OrderDAO;
import com.ferry.entity.Ticket;
import com.ferry.entity.User;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;

/** 乘客“我的船票”：展示当前账号名下仍有效的船票和票码。 */
@WebServlet("/passenger/tickets")
public class PassengerTicketServlet extends HttpServlet {
    private final OrderDAO orderDAO = new OrderDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("loginUser");
        if (user == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }
        if (!"passenger".equals(user.getRole())) {
            resp.sendRedirect(req.getContextPath() + "/passenger/home");
            return;
        }
        List<Ticket> tickets = orderDAO.findValidTicketsByPassenger(user.getId());
        req.setAttribute("tickets", tickets);
        req.setAttribute("msg", req.getParameter("msg"));
        req.getRequestDispatcher("/passenger/tickets.jsp").forward(req, resp);
    }
}
