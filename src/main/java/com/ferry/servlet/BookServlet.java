package com.ferry.servlet;

import com.ferry.dao.OrderDAO;
import com.ferry.dao.ScheduleDAO;
import com.ferry.entity.Order;
import com.ferry.entity.Schedule;
import com.ferry.entity.SeatCell;
import com.ferry.entity.Ticket;
import com.ferry.entity.User;
import com.ferry.util.DBUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 购票控制器：
 * GET  action=seats  显示某班次的选座页
 * POST action=order  锁座下单（5分钟待支付）
 * POST action=pay    支付
 * POST action=refund 退票
 * POST action=change 改签
 */
@WebServlet("/book")
public class BookServlet extends HttpServlet {
    private final OrderDAO orderDAO = new OrderDAO();
    private final ScheduleDAO scheduleDAO = new ScheduleDAO();
    private final com.ferry.dao.UserDAO userDAO = new com.ferry.dao.UserDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isLogin(req, resp)) return;
        String action = req.getParameter("action");
        if ("seats".equals(action)) {
            HttpSession s = req.getSession(false);
            User loginUser = (User) s.getAttribute("loginUser");
            int scheduleId = parseInt(req.getParameter("scheduleId"));
            Schedule sched = findSchedule(scheduleId);
            // 先释放超时锁座，避免选座图把已过期的“锁定”显示成不可选
            try {
                orderDAO.releaseExpiredLocks();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            List<SeatCell> seatMap = orderDAO.findSeatMap(scheduleId);
            int availableCount = 0;
            for (SeatCell cell : seatMap) {
                if (cell.isAvailable()) availableCount++;
            }
            req.setAttribute("sched", sched);
            req.setAttribute("seatMap", seatMap);
            req.setAttribute("availableCount", availableCount);
            // 选座页可能因“座位被占 / 资料不合规”被重定向回来，需要把提示和原座位还原
            String preSeat = req.getParameter("seatNo");
            if (preSeat != null && !preSeat.matches("[A-Za-z0-9]{1,10}")) preSeat = null;
            req.setAttribute("seatNo", preSeat);
            req.setAttribute("msg", escapeHtml(req.getParameter("msg")));
            String view = "passenger".equals(loginUser.getRole())
                    ? "/passenger/seats.jsp" : "/seller/seats.jsp";
            req.getRequestDispatcher(view).forward(req, resp);
            return;
        }
        resp.sendRedirect(req.getContextPath() + "/passenger/schedules");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isLogin(req, resp)) return;
        HttpSession session = req.getSession();
        User loginUser = (User) session.getAttribute("loginUser");
        String action = req.getParameter("action");

        try {
            if ("order".equals(action)) {
                // 下单前先释放超时锁座（5分钟规则）
                orderDAO.releaseExpiredLocks();

                int scheduleId = parseInt(req.getParameter("scheduleId"));
                Schedule sched = findSchedule(scheduleId);
                if (sched == null) {
                    resp.sendRedirect(req.getContextPath() + listPath(loginUser)
                            + "?msg=" + enc("班次不存在或已停止售票"));
                    return;
                }
                List<String> seatNos = collectSeatNos(req);
                if (seatNos.isEmpty() || seatNos.size() > OrderDAO.MAX_TICKETS_PER_ORDER) {
                    resp.sendRedirect(req.getContextPath() + "/book?action=seats&scheduleId=" + scheduleId
                            + "&msg=" + enc("请选择 1~" + OrderDAO.MAX_TICKETS_PER_ORDER + " 个不同座位"));
                    return;
                }

                Order o = new Order();
                o.setScheduleId(scheduleId);
                if ("seller".equals(loginUser.getRole())) o.setSellerId(loginUser.getId());
                o.setTotalAmount(sched.getPrice());

                // 身份约束：乘客只能用本人档案信息购票（表单值一律忽略，防冒用他人身份）；
                // 售票员代客购票可填乘客信息，但必须通过格式 + 校验位验证。
                String[] names = req.getParameterValues("passengerName");
                String[] ids = req.getParameterValues("idCard");
                String[] phones = req.getParameterValues("phone");
                List<Ticket> tickets = new ArrayList<Ticket>();
                if ("passenger".equals(loginUser.getRole())) {
                    User me = userDAO.findById(loginUser.getId());
                    if (me == null || !com.ferry.util.IdentityUtil.validIdCard(me.getIdCard())
                            || !com.ferry.util.IdentityUtil.validPhone(me.getPhone())) {
                        resp.sendRedirect(req.getContextPath() + "/passenger/profile?msg="
                                + enc("请先在个人中心补全真实姓名、身份证和手机号后再购票"));
                        return;
                    }
                    for (int i = 0; i < seatNos.size(); i++) {
                        String pName = i == 0 ? me.getRealName() : valueAt(names, i);
                        String pId = i == 0 ? me.getIdCard() : valueAt(ids, i);
                        String pPhone = i == 0 ? me.getPhone() : valueAt(phones, i);
                        String idError = com.ferry.util.IdentityUtil.validate(pName, pId, pPhone);
                        if (idError != null) {
                            resp.sendRedirect(req.getContextPath() + "/book?action=seats&scheduleId=" + scheduleId
                                    + "&msg=" + enc("第 " + (i + 1) + " 位乘客：" + idError));
                            return;
                        }
                        tickets.add(makeTicket(orderDAO, scheduleId, seatNos.get(i), pName, pId, pPhone, sched));
                    }
                    o.setPassengerId(loginUser.getId());
                } else {
                    for (int i = 0; i < seatNos.size(); i++) {
                        String pName = valueAt(names, i);
                        String pId = valueAt(ids, i);
                        String pPhone = valueAt(phones, i);
                        String idError = com.ferry.util.IdentityUtil.validate(pName, pId, pPhone);
                        if (idError != null) {
                            resp.sendRedirect(req.getContextPath() + "/book?action=seats&scheduleId=" + scheduleId
                                    + "&msg=" + enc("第 " + (i + 1) + " 位乘客：" + idError));
                            return;
                        }
                        tickets.add(makeTicket(orderDAO, scheduleId, seatNos.get(i), pName, pId, pPhone, sched));
                    }
                    o.setPassengerId(userDAO.findPassengerIdByIdCard(valueAt(ids, 0)));
                }
                for (Ticket t : tickets) {
                    if (t.getSeatId() <= 0) {
                        resp.sendRedirect(req.getContextPath() + "/book?action=seats&scheduleId=" + scheduleId
                                + "&msg=" + enc("座位不存在或已被占用，请刷新后重选"));
                        return;
                    }
                }
                java.math.BigDecimal total = sched.getPrice()
                        .multiply(java.math.BigDecimal.valueOf(tickets.size()));
                o.setTotalAmount(total);

                int result = orderDAO.lockAndCreate(o, tickets);
                if (result == -1) {
                    resp.sendRedirect(req.getContextPath() + "/book?action=seats&scheduleId=" + scheduleId
                            + "&msg=" + enc("座位已被占用，请重新选择"));
                } else if (result == -3) {
                    resp.sendRedirect(req.getContextPath() + listPath(loginUser)
                            + "?msg=" + enc("该证件已有同一时间段的船票，不能重复购票"));
                } else if (result == -4) {
                    resp.sendRedirect(req.getContextPath() + listPath(loginUser)
                            + "?msg=" + enc("该班次已发船或停止售票，不能购票"));
                } else if (result == -2) {
                    resp.sendRedirect(req.getContextPath() + listPath(loginUser)
                            + "?msg=" + enc("该证件已购买此班次船票"));
                } else if (result == -5) {
                    resp.sendRedirect(req.getContextPath() + "/book?action=seats&scheduleId=" + scheduleId
                            + "&msg=" + enc("订单票数或乘客信息不合法，请重新选择"));
                } else {
                    resp.sendRedirect(req.getContextPath() + ordersPath(loginUser) + "?msg="
                            + enc("锁座成功！订单号 " + o.getOrderNo() + "，共 " + tickets.size()
                                    + " 张，请在5分钟内支付"));
                }
                return;
            }

            if ("cancel".equals(action)) {
                int orderId = parseInt(req.getParameter("orderId"));
                String deny = checkOwner(orderId, loginUser);
                if (deny != null) {
                    resp.sendRedirect(req.getContextPath() + ordersPath(loginUser) + "?msg=" + enc(deny));
                    return;
                }
                String cancelError = orderDAO.cancelPending(orderId);
                String cancelMsg = cancelError == null ? "订单已取消，座位已释放" : "取消失败：" + cancelError;
                resp.sendRedirect(req.getContextPath() + ordersPath(loginUser) + "?msg=" + enc(cancelMsg));
                return;
            }

            if ("pay".equals(action)) {
                int orderId = parseInt(req.getParameter("orderId"));
                String deny = checkOwner(orderId, loginUser);
                if (deny != null) {
                    resp.sendRedirect(req.getContextPath() + ordersPath(loginUser) + "?msg=" + enc(deny));
                    return;
                }
                String payError = orderDAO.pay(orderId);
                String payMsg = payError == null ? "支付成功，出票完成" : "支付失败：" + payError;
                resp.sendRedirect(req.getContextPath() + ordersPath(loginUser) + "?msg=" + enc(payMsg));
                return;
            }

            if ("refund".equals(action)) {
                int orderId = parseInt(req.getParameter("orderId"));
                String deny = checkOwner(orderId, loginUser);
                if (deny != null) {
                    resp.sendRedirect(req.getContextPath() + ordersPath(loginUser) + "?msg=" + enc(deny));
                    return;
                }
                String blockReason = orderDAO.checkRefund(orderId);
                if (blockReason != null) {
                    resp.sendRedirect(req.getContextPath() + ordersPath(loginUser) + "?msg=" + enc(blockReason));
                    return;
                }
                BigDecimal refund = orderDAO.refund(orderId, req.getParameter("reason"));
                String msg = refund == null ? "该订单当前状态不能退票"
                           : "退票申请已受理，实退 ￥" + refund + "（按距发船时间扣手续费），预计 3 个工作日内到账";
                resp.sendRedirect(req.getContextPath() + ordersPath(loginUser) + "?msg=" + enc(msg));
                return;
            }

            if ("change".equals(action)) {
                int orderId = parseInt(req.getParameter("orderId"));
                String deny = checkOwner(orderId, loginUser);
                if (deny != null) {
                    resp.sendRedirect(req.getContextPath() + ordersPath(loginUser) + "?msg=" + enc(deny));
                    return;
                }
                // 新格式：changeTarget = "班次ID|座位号"；兼容旧的 newScheduleId/newSeatNo
                String target = req.getParameter("changeTarget");
                int newScheduleId;
                String seatNo;
                if (target != null && target.contains("|")) {
                    String[] parts = target.split("\\|", 2);
                    newScheduleId = parseInt(parts[0]);
                    seatNo = parts[1];
                } else {
                    newScheduleId = parseInt(req.getParameter("newScheduleId"));
                    seatNo = req.getParameter("newSeatNo");
                }
                String changeBlock = orderDAO.checkChange(orderId, newScheduleId);
                if (changeBlock != null) {
                    resp.sendRedirect(req.getContextPath() + ordersPath(loginUser) + "?msg=" + enc(changeBlock));
                    return;
                }
                int newSeatId = orderDAO.findSeatId(newScheduleId, seatNo);
                String result = orderDAO.change(orderId, newScheduleId, newSeatId, loginUser.getId(),
                                                req.getParameter("reason"));
                resp.sendRedirect(req.getContextPath() + ordersPath(loginUser) + "?msg="
                        + enc(result == null ? "改签失败：座位不可用或与已有船票时段冲突" : result));
                return;
            }

            resp.sendRedirect(req.getContextPath() + "/passenger/home");
        } catch (SQLException e) {
            e.printStackTrace();
            resp.sendRedirect(req.getContextPath() + ordersPath(loginUser)
                    + "?msg=" + enc("操作失败，请稍后重试"));
        }
    }

    /**
     * 数据归属校验：乘客只能操作本人订单；售票员只能操作本人经办订单；
     * 管理员可操作全部。返回 null 表示有权，否则返回拒绝原因。
     */
    private String checkOwner(int orderId, User u) {
        Order o = orderDAO.findById(orderId);
        if (o == null) return "订单不存在";
        String role = u.getRole();
        if ("admin".equals(role)) return null;
        if ("seller".equals(role)) {
            if (o.getSellerId() != null && o.getSellerId() == u.getId()) return null;
            return "无权操作该订单：不是您经办的订单";
        }
        if (o.getPassengerId() != null && o.getPassengerId().intValue() == u.getId()) return null;
        return "无权操作该订单：不是您的订单";
    }

    /** 按角色返回订单页路径：售票员走销售订单，其他走我的订单 */
    private String ordersPath(User u) {
        if ("seller".equals(u.getRole())) return "/seller/orders";
        if ("admin".equals(u.getRole())) return "/admin/orders";
        return "/passenger/orders";
    }

    /** 按角色返回班次列表路径：售票员走售票台，其他走乘客班次页 */
    private String listPath(User u) {
        return "passenger".equals(u.getRole()) ? "/passenger/schedules" : "/seller/sell";
    }

    /** 查询班次详情（联表） */
    private Schedule findSchedule(int id) {
        String sql = "SELECT s.*, r.route_name, r.start_port, r.end_port, sh.ship_name "
                   + "FROM schedule s JOIN route r ON r.id=s.route_id JOIN ship sh ON sh.id=s.ship_id "
                   + "WHERE s.id=? AND s.is_deleted=0";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Schedule s = new Schedule();
                    s.setId(rs.getInt("id"));
                    s.setRouteId(rs.getInt("route_id"));
                    s.setShipId(rs.getInt("ship_id"));
                    s.setDepartTime(rs.getTimestamp("depart_time"));
                    s.setArriveTime(rs.getTimestamp("arrive_time"));
                    s.setPrice(rs.getBigDecimal("price"));
                    s.setTotalSeats(rs.getInt("total_seats"));
                    s.setSoldSeats(rs.getInt("sold_seats"));
                    s.setStatus(rs.getString("status"));
                    s.setRouteName(rs.getString("route_name"));
                    s.setStartPort(rs.getString("start_port"));
                    s.setEndPort(rs.getString("end_port"));
                    s.setShipName(rs.getString("ship_name"));
                    return s;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean isLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession(false);
        if (s == null || s.getAttribute("loginUser") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return false;
        }
        return true;
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s == null ? "0" : s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    /** 支持 seatNo 单值、重复字段和逗号分隔三种提交方式。 */
    private List<String> collectSeatNos(HttpServletRequest req) {
        List<String> result = new ArrayList<String>();
        String[] values = req.getParameterValues("seatNo");
        if (values == null) values = new String[]{req.getParameter("seatNo")};
        for (String value : values) {
            if (value == null) continue;
            for (String part : value.split(",")) {
                String seatNo = part.trim();
                if (seatNo.isEmpty()) continue;
                if (!seatNo.matches("[A-Za-z0-9]{1,10}") || result.contains(seatNo)) return new ArrayList<String>();
                result.add(seatNo);
            }
        }
        return result;
    }

    private String valueAt(String[] values, int index) {
        if (values == null || index < 0 || index >= values.length) return null;
        return values[index];
    }

    private Ticket makeTicket(OrderDAO dao, int scheduleId, String seatNo,
                              String name, String idCard, String phone, Schedule sched) {
        Ticket t = new Ticket();
        t.setSeatId(dao.findSeatId(scheduleId, seatNo));
        t.setIdCard(idCard == null ? "" : idCard.trim().toUpperCase());
        t.setPassengerPhone(phone == null ? "" : phone.trim());
        t.setPassengerName(name == null ? "" : name.trim());
        t.setPrice(sched.getPrice());
        return t;
    }

    private String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }

    /** 选座页的提示来自 URL 参数，输出前做转义，避免被注入脚本 */
    private String escapeHtml(String s) {
        if (s == null) return null;
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
