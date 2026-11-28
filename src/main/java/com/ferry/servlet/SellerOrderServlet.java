package com.ferry.servlet;

import com.ferry.dao.OrderDAO;
import com.ferry.dao.ScheduleDAO;
import com.ferry.entity.Order;
import com.ferry.entity.Schedule;
import com.ferry.entity.User;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 售票员销售订单：列出本人经办订单，并提供支付 / 退票数据 */
@WebServlet("/seller/orders")
public class SellerOrderServlet extends HttpServlet {
    private final OrderDAO orderDAO = new OrderDAO();
    private final ScheduleDAO scheduleDAO = new ScheduleDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession(false);
        User u = session == null ? null : (User) session.getAttribute("loginUser");
        if (u == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }
        if (!"seller".equals(u.getRole()) && !"admin".equals(u.getRole())) {
            resp.sendRedirect(req.getContextPath() + "/passenger/home");
            return;
        }

        List<Order> orders = orderDAO.findBySeller(u.getId());
        req.setAttribute("orders", orders);

        List<String> changeOptions = new ArrayList<String>();
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
        for (Schedule s : scheduleDAO.findAll()) {
            if (!"售票中".equals(s.getStatus())) continue;
            if (s.getDepartTime() == null || !s.getDepartTime().after(new java.util.Date())) continue;
            for (String seatNo : orderDAO.findAvailableSeats(s.getId())) {
                String label = s.getRouteName() + " " + fmt.format(s.getDepartTime())
                             + " " + seatNo + "座 ￥" + s.getPrice();
                changeOptions.add(s.getId() + "|" + seatNo + "|"
                        + s.getDepartTime().getTime() + "|" + label);
            }
        }
        req.setAttribute("changeOptions", changeOptions);

        List<Integer> orderIds = new ArrayList<Integer>();
        for (Order o : orders) orderIds.add(o.getId());

        Map<Integer, List<String>> changeInfos = orderDAO.findChangeInfoByOrderIds(orderIds);
        req.setAttribute("changeInfos", changeInfos);
        req.setAttribute("paymentLogs", orderDAO.findPaymentLogsByOrderIds(orderIds));
        req.setAttribute("msg", req.getParameter("msg"));
        req.getRequestDispatcher("/seller/orders.jsp").forward(req, resp);
    }
}
