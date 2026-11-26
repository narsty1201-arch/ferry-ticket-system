package com.ferry.servlet;

import com.ferry.dao.OrderDAO;
import com.ferry.entity.Order;
import com.ferry.entity.User;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 管理员 - 订单管理：查看全部订单，按状态 / 关键字筛选 */
@WebServlet("/admin/orders")
public class AdminOrderServlet extends HttpServlet {
    private final OrderDAO dao = new OrderDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isAdmin(req, resp)) return;

        String status = req.getParameter("status");
        String keyword = req.getParameter("keyword");
        if (status == null) status = "all";
        if (keyword == null) keyword = "";

        List<Order> orders = dao.findAllForAdmin(status, keyword);
        req.setAttribute("orders", orders);
        Map<Integer, List<String>> paymentLogs = new HashMap<Integer, List<String>>();
        for (Order o : orders) {
            List<String> logs = dao.findPaymentLogs(o.getId());
            if (!logs.isEmpty()) paymentLogs.put(o.getId(), logs);
        }
        req.setAttribute("paymentLogs", paymentLogs);
        req.setAttribute("status", status);
        req.setAttribute("keyword", keyword);
        req.setAttribute("msg", req.getParameter("msg"));
        req.getRequestDispatcher("/admin/orders.jsp").forward(req, resp);
    }

    private boolean isAdmin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        User u = session == null ? null : (User) session.getAttribute("loginUser");
        if (u == null || !"admin".equals(u.getRole())) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return false;
        }
        return true;
    }
}
