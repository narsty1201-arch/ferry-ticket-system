package com.ferry.servlet;

import com.ferry.dao.OrderDAO;
import com.ferry.entity.Order;
import com.ferry.entity.User;
import com.ferry.util.Page;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
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

        int pageSize = 10;
        int total = dao.countForAdmin(status, keyword);
        int page = Page.clamp(Page.parse(req.getParameter("page"), 1), total, pageSize);
        List<Order> orders = dao.findForAdmin(status, keyword, page, pageSize);

        List<Integer> orderIds = new ArrayList<Integer>();
        for (Order o : orders) orderIds.add(o.getId());
        Map<Integer, List<String>> paymentLogs = dao.findPaymentLogsByOrderIds(orderIds);

        req.setAttribute("pageData", new Page<Order>(orders, page, pageSize, total));
        req.setAttribute("pageUrl", "orders?status=" + enc(status)
                + "&keyword=" + enc(keyword) + "&page=");
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

    private String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
