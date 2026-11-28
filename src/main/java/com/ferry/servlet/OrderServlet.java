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
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 我的订单页：列出当前用户的订单，并批量带出资金流水与历史改签记录 */
@WebServlet("/passenger/orders")
public class OrderServlet extends HttpServlet {
    private final OrderDAO orderDAO = new OrderDAO();
    private final ScheduleDAO scheduleDAO = new ScheduleDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("loginUser") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }
        User u = (User) session.getAttribute("loginUser");
        try {
            orderDAO.releaseExpiredLocks();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        List<Order> orders = orderDAO.findByPassenger(u.getId());
        req.setAttribute("orders", orders);

        // 可改签目标：仍未发船的班次 + 可售座位；带上发船时间给页面按订单筛选。
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

        // 批量查询改签记录与资金流水，避免 N+1
        Map<Integer, List<String>> changeInfos = orderDAO.findChangeInfoByOrderIds(orderIds);
        req.setAttribute("changeInfos", changeInfos);
        req.setAttribute("paymentLogs", orderDAO.findPaymentLogsByOrderIds(orderIds));
        req.setAttribute("msg", req.getParameter("msg"));
        req.getRequestDispatcher("/passenger/orders.jsp").forward(req, resp);
    }
}
