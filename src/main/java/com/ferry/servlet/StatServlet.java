package com.ferry.servlet;

import com.ferry.dao.RouteDAO;
import com.ferry.dao.StatDAO;
import com.ferry.entity.Route;
import com.ferry.entity.User;
import com.ferry.util.DateRange;
import com.ferry.util.ExcelExportUtil;
import com.ferry.util.XlsxWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

/** 统计报表控制器：聚合 SQL 结果汇总到一页 */
@WebServlet("/admin/stats")
public class StatServlet extends HttpServlet {
    private final StatDAO dao = new StatDAO();
    private final RouteDAO routeDAO = new RouteDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession s = req.getSession(false);
        User u = s == null ? null : (User) s.getAttribute("loginUser");
        if (u == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return;
        }
        // 权限：统计报表仅管理员可见，普通乘客/售票员不得查看全局营收
        if (!"admin".equals(u.getRole())) {
            resp.sendRedirect(req.getContextPath() + "/passenger/home");
            return;
        }
        String startText = trim(req.getParameter("startDate"));
        String endText = trim(req.getParameter("endDate"));
        DateRange range = DateRange.parse(startText, endText);
        String filterError = range.error();

        Integer routeId = null;
        String routeText = trim(req.getParameter("routeId"));
        if (!routeText.isEmpty()) {
            try {
                routeId = Integer.valueOf(routeText);
            } catch (NumberFormatException e) {
                filterError = joinError(filterError, "航线筛选参数不正确");
            }
        }
        if (range.hasError()) {
            range = DateRange.all();
            routeId = null;
        }
        if ("xlsx".equals(req.getParameter("format")) && filterError != null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, filterError);
            return;
        }

        java.sql.Timestamp start = range.startInclusive();
        java.sql.Timestamp end = range.endExclusive();
        Map<String, Object> ov = dao.overview(start, end, routeId);
        List<Map<String, Object>> byRoute = dao.byRoute(start, end, routeId);
        List<Map<String, Object>> bySchedule = dao.bySchedule(start, end, routeId);
        List<Map<String, Object>> sellers = dao.sellerPerformance(start, end, routeId);
        List<Map<String, Object>> statusDist = dao.orderStatus(start, end, routeId);
        List<Map<String, Object>> refundReasons = dao.refundReasons(start, end, routeId);
        List<Route> routes = routeDAO.findAll();

        if ("xlsx".equals(req.getParameter("format"))) {
            export(resp, u, startText, endText, routeId, routes,
                    ov, byRoute, bySchedule, sellers, statusDist, refundReasons);
            return;
        }

        req.setAttribute("ov", ov);
        req.setAttribute("byRoute", byRoute);
        req.setAttribute("bySchedule", bySchedule);
        req.setAttribute("sellers", sellers);
        req.setAttribute("statusDist", statusDist);
        req.setAttribute("refundReasons", refundReasons);
        req.setAttribute("routes", routes);
        req.setAttribute("startDate", startText);
        req.setAttribute("endDate", endText);
        req.setAttribute("routeId", routeId);
        req.setAttribute("filterError", filterError);
        req.setAttribute("exportUrl", "stats?format=xlsx&startDate=" + enc(startText)
                + "&endDate=" + enc(endText)
                + "&routeId=" + (routeId == null ? "" : routeId));
        req.getRequestDispatcher("/admin/stats.jsp").forward(req, resp);
    }

    private void export(HttpServletResponse resp, User user, String startText, String endText,
            Integer routeId, List<Route> routes, Map<String, Object> ov,
            List<Map<String, Object>> byRoute, List<Map<String, Object>> bySchedule,
            List<Map<String, Object>> sellers, List<Map<String, Object>> statusDist,
            List<Map<String, Object>> refundReasons) throws IOException {
        String routeLabel = "全部航线";
        if (routeId != null) {
            for (Route route : routes) {
                if (route.getId() == routeId) {
                    routeLabel = route.getRouteName() + "（"
                            + route.getStartPort() + " → " + route.getEndPort() + "）";
                    break;
                }
            }
        }

        XlsxWriter workbook = new XlsxWriter();
        workbook.addSheet("导出说明")
                .header("项目", "内容")
                .row("导出时间", ExcelExportUtil.formatTime(new java.util.Date()))
                .row("导出人", user.getRealName() + "（" + user.getUsername() + "）")
                .row("开始日期", startText.isEmpty() ? "不限" : startText)
                .row("结束日期", endText.isEmpty() ? "不限（结束日含当天）" : endText + "（含当天）")
                .row("航线", routeLabel)
                .row("统计口径", "营收/订单按下单时间；上座率按发船时间；退票原因按退款时间；检票数按检票时间");

        XlsxWriter.Sheet metrics = workbook.addSheet("核心指标");
        metrics.header("指标", "数值");
        metric(metrics, "订单总数", ov.get("total_orders"));
        metric(metrics, "净营收", ov.get("total_income"));
        metric(metrics, "有效船票/已售座位", ov.get("sold_seats"));
        metric(metrics, "已检票订单（含已完成）", ov.get("checked_orders"));
        metric(metrics, "已完成订单", ov.get("completed_orders"));
        metric(metrics, "退票订单", ov.get("refunded_orders"));
        metric(metrics, "退款金额", ov.get("refund_amount"));
        metric(metrics, "系统用户数", ov.get("user_count"));
        metric(metrics, "班次数", ov.get("schedule_count"));

        XlsxWriter.Sheet routeSheet = workbook.addSheet("航线统计");
        routeSheet.header("航线", "出发港", "到达港", "成交订单", "已检票订单",
                "净营收", "退款金额", "有效船票", "已检票票数");
        for (Map<String, Object> row : byRoute) {
            routeSheet.row(text(row.get("route_name")), text(row.get("start_port")),
                    text(row.get("end_port")), ExcelExportUtil.number(row.get("order_count")),
                    ExcelExportUtil.number(row.get("checked_orders")),
                    ExcelExportUtil.number(row.get("income")),
                    ExcelExportUtil.number(row.get("refund_amount")),
                    ExcelExportUtil.number(row.get("sold")),
                    ExcelExportUtil.number(row.get("checked")));
        }

        XlsxWriter.Sheet scheduleSheet = workbook.addSheet("班次上座率");
        scheduleSheet.header("班次ID", "航线", "发船时间", "班次状态", "总座位",
                "已售", "上座率%", "已检票", "预警");
        for (Map<String, Object> row : bySchedule) {
            scheduleSheet.row(ExcelExportUtil.number(row.get("schedule_id")),
                    text(row.get("route_name")), text(row.get("depart_time")),
                    text(row.get("status")), ExcelExportUtil.number(row.get("total_seats")),
                    ExcelExportUtil.number(row.get("sold_seats")),
                    ExcelExportUtil.number(row.get("rate")),
                    ExcelExportUtil.number(row.get("checked")),
                    text(row.get("alert_level")));
        }

        XlsxWriter.Sheet sellerSheet = workbook.addSheet("售票员业绩");
        sellerSheet.header("姓名", "账号", "成交订单", "有效船票", "净销售额",
                "退款单数", "退款金额", "检票数", "平均单额");
        for (Map<String, Object> row : sellers) {
            sellerSheet.row(text(row.get("real_name")), text(row.get("username")),
                    ExcelExportUtil.number(row.get("order_count")),
                    ExcelExportUtil.number(row.get("ticket_count")),
                    ExcelExportUtil.number(row.get("total_amount")),
                    ExcelExportUtil.number(row.get("refund_count")),
                    ExcelExportUtil.number(row.get("refund_amount")),
                    ExcelExportUtil.number(row.get("checked_count")),
                    ExcelExportUtil.number(row.get("avg_amount")));
        }

        XlsxWriter.Sheet refundSheet = workbook.addSheet("退票原因");
        refundSheet.header("退票原因", "退票单数", "原订单金额", "实际退款");
        for (Map<String, Object> row : refundReasons) {
            refundSheet.row(text(row.get("reason")),
                    ExcelExportUtil.number(row.get("refund_count")),
                    ExcelExportUtil.number(row.get("refunded_total")),
                    ExcelExportUtil.number(row.get("refund_amount")));
        }

        XlsxWriter.Sheet statusSheet = workbook.addSheet("订单状态");
        statusSheet.header("合计", "待支付", "已支付", "已检票", "已完成", "已退票", "已取消");
        for (Map<String, Object> row : statusDist) {
            statusSheet.row(ExcelExportUtil.number(row.get("total")),
                    ExcelExportUtil.number(row.get("pending")),
                    ExcelExportUtil.number(row.get("paid")),
                    ExcelExportUtil.number(row.get("checked")),
                    ExcelExportUtil.number(row.get("completed")),
                    ExcelExportUtil.number(row.get("refunded")),
                    ExcelExportUtil.number(row.get("cancelled")));
        }
        ExcelExportUtil.send(resp, "经营报表", workbook);
    }

    private void metric(XlsxWriter.Sheet sheet, String label, Object value) {
        sheet.row(label, ExcelExportUtil.number(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String joinError(String current, String extra) {
        return current == null || current.isEmpty() ? extra : current + "；" + extra;
    }

    private String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
