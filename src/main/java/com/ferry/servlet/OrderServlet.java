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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 我的订单页：列出当前用户的订单，并带上可改签班次/座位数据 */
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
        List<Order> orders = orderDAO.findByPassenger(u.getId());
        req.setAttribute("orders", orders);
        req.setAttribute("schedules", scheduleDAO.findAll());

        // 改签可选的"班次+座位"组合：只列售票中班次的可售座位
        List<String> changeOptions = new ArrayList<String>();
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
        for (Schedule s : scheduleDAO.findAll()) {
            if (!"售票中".equals(s.getStatus())) continue;
            if (s.getDepartTime() == null || !s.getDepartTime().after(new java.util.Date())) continue;
            for (String seatNo : orderDAO.findAvailableSeats(s.getId())) {
                String label = s.getRouteName() + " " + fmt.format(s.getDepartTime())
                             + " " + seatNo + "座 ￥" + s.getPrice();
                changeOptions.add(s.getId() + "|" + seatNo + "|" + label);
            }
        }
        req.setAttribute("changeOptions", changeOptions);

        // 每个订单的改签记录（用于页面醒目展示"已改签"）
        Map<Integer, List<String>> changeInfos = new HashMap<Integer, List<String>>();
        for (Order o : orders) {
            List<String> infos = orderDAO.findChangeInfo(o.getId());
            if (!infos.isEmpty()) changeInfos.put(o.getId(), infos);
        }
        req.setAttribute("changeInfos", changeInfos);

        // 每个订单的资金流水（支付/退款/改签补退）
        Map<Integer, List<String>> paymentLogs = new HashMap<Integer, List<String>>();
        for (Order o : orders) {
            List<String> logs = orderDAO.findPaymentLogs(o.getId());
            if (!logs.isEmpty()) paymentLogs.put(o.getId(), logs);
        }
        req.setAttribute("paymentLogs", paymentLogs);
        req.setAttribute("msg", req.getParameter("msg"));
        req.getRequestDispatcher("/passenger/orders.jsp").forward(req, resp);
    }
}
