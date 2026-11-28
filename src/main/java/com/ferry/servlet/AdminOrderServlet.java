package com.ferry.servlet;

import com.ferry.dao.OrderDAO;
import com.ferry.entity.Order;
import com.ferry.entity.Ticket;
import com.ferry.entity.User;
import com.ferry.util.ExcelExportUtil;
import com.ferry.util.Page;
import com.ferry.util.XlsxWriter;

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

        if ("xlsx".equals(req.getParameter("format"))) {
            export(ordersForExport(status, keyword), resp, status, keyword);
            return;
        }

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
        req.setAttribute("exportUrl", "orders?format=xlsx&status=" + enc(status)
                + "&keyword=" + enc(keyword));
        req.setAttribute("msg", req.getParameter("msg"));
        req.getRequestDispatcher("/admin/orders.jsp").forward(req, resp);
    }

    private List<Order> ordersForExport(String status, String keyword) {
        return dao.findAllForAdmin(status, keyword);
    }

    private void export(List<Order> orders, HttpServletResponse resp,
            String status, String keyword) throws IOException {
        XlsxWriter workbook = new XlsxWriter();
        int ticketCount = 0;
        for (Order order : orders) ticketCount += order.getTickets().size();

        XlsxWriter.Sheet info = workbook.addSheet("导出说明");
        info.header("项目", "内容")
            .row("导出时间", ExcelExportUtil.formatTime(new java.util.Date()))
            .row("状态筛选", "all".equals(status) ? "全部" : status)
            .row("关键字筛选", keyword == null || keyword.isEmpty() ? "无" : keyword)
            .row("订单数", orders.size())
            .row("船票数", ticketCount)
            .row("隐私说明", "身份证与手机号已脱敏；仅管理员可导出");

        XlsxWriter.Sheet sheet = workbook.addSheet("订单明细");
        sheet.header("订单号", "订单ID", "状态", "订单金额", "乘客姓名", "座位",
                "航线", "船舶", "发船时间", "下单时间", "支付时间", "退款金额",
                "退款原因", "退款时间", "售票员ID", "班次ID");
        for (Order o : orders) {
            sheet.row(text(o.getOrderNo()), o.getId(), text(o.getStatus()),
                    o.getTotalAmount(), text(o.getPassengerName()), text(o.getSeatNo()),
                    text(o.getRouteName()), text(o.getShipName()),
                    ExcelExportUtil.formatTime(o.getDepartTime()),
                    ExcelExportUtil.formatTime(o.getCreateTime()),
                    ExcelExportUtil.formatTime(o.getPayTime()),
                    o.getRefundAmount(), text(o.getRefundReason()),
                    ExcelExportUtil.formatTime(o.getRefundTime()),
                    o.getSellerId() == null ? "" : o.getSellerId(), o.getScheduleId());
        }

        XlsxWriter.Sheet tickets = workbook.addSheet("船票明细");
        tickets.header("订单号", "票ID", "乘客姓名", "身份证（脱敏）", "手机号（脱敏）",
                "座位", "票价", "票状态", "票码", "航线", "发船时间");
        for (Order o : orders) {
            for (Ticket t : o.getTickets()) {
                tickets.row(text(o.getOrderNo()), t.getId(), text(t.getPassengerName()),
                        maskIdCard(t.getIdCard()), maskPhone(t.getPassengerPhone()),
                        text(t.getSeatNo()), t.getPrice(), text(t.getStatus()),
                        text(t.getQrCode()), text(o.getRouteName()),
                        ExcelExportUtil.formatTime(o.getDepartTime()));
            }
        }
        ExcelExportUtil.send(resp, "订单明细", workbook);
    }

    private Object text(Object value) {
        return value == null ? "" : value;
    }

    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) return text(idCard).toString();
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return text(phone).toString();
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
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
