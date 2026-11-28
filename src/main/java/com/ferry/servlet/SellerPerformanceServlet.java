package com.ferry.servlet;

import com.ferry.dao.StatDAO;
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
import java.util.Map;

/** 售票员本人业绩：仅展示当前登录账号的经办订单与检票量。 */
@WebServlet("/seller/performance")
public class SellerPerformanceServlet extends HttpServlet {
    private final StatDAO dao = new StatDAO();

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
        if ("admin".equals(user.getRole())) {
            resp.sendRedirect(req.getContextPath() + "/admin/stats");
            return;
        }
        if (!"seller".equals(user.getRole())) {
            resp.sendRedirect(req.getContextPath() + "/passenger/home");
            return;
        }

        String startText = trim(req.getParameter("startDate"));
        String endText = trim(req.getParameter("endDate"));
        DateRange range = DateRange.parse(startText, endText);
        String dateError = range.error();
        if (range.hasError()) range = DateRange.all();
        if ("xlsx".equals(req.getParameter("format")) && dateError != null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, dateError);
            return;
        }

        Map<String, Object> summary = dao.sellerSummary(
                user.getId(), range.startInclusive(), range.endExclusive());
        if ("xlsx".equals(req.getParameter("format"))) {
            export(resp, user, startText, endText, summary);
            return;
        }
        req.setAttribute("summary", summary);
        req.setAttribute("startDate", startText);
        req.setAttribute("endDate", endText);
        req.setAttribute("dateError", dateError);
        req.setAttribute("exportUrl", "performance?format=xlsx&startDate=" + enc(startText)
                + "&endDate=" + enc(endText));
        req.getRequestDispatcher("/seller/performance.jsp").forward(req, resp);
    }

    private void export(HttpServletResponse resp, User user, String startText,
            String endText, Map<String, Object> summary) throws IOException {
        XlsxWriter workbook = new XlsxWriter();
        workbook.addSheet("导出说明")
                .header("项目", "内容")
                .row("导出时间", ExcelExportUtil.formatTime(new java.util.Date()))
                .row("售票员", user.getRealName() + "（" + user.getUsername() + "）")
                .row("开始日期", startText.isEmpty() ? "不限" : startText)
                .row("结束日期", endText.isEmpty() ? "不限（结束日含当天）" : endText + "（含当天）")
                .row("统计口径", "成交与销售额按下单时间，本人检票数按检票时间");

        XlsxWriter.Sheet sheet = workbook.addSheet("我的业绩");
        sheet.header("指标", "数值", "说明");
        sheet.row("成交订单", ExcelExportUtil.number(summary.get("order_count")), "不含待支付和已取消");
        sheet.row("有效船票", ExcelExportUtil.number(summary.get("ticket_count")), "已支付或已检票");
        sheet.row("净销售额", ExcelExportUtil.number(summary.get("total_amount")), "已扣除实际退款");
        sheet.row("退款金额", ExcelExportUtil.number(summary.get("refund_amount")), "实际退款总额");
        sheet.row("退款单数", ExcelExportUtil.number(summary.get("refund_count")), "已退票订单");
        sheet.row("本人成功检票", ExcelExportUtil.number(summary.get("checked_count")), "按票计");
        sheet.row("平均单额", ExcelExportUtil.number(summary.get("avg_amount")), "净销售额 / 成交订单");
        ExcelExportUtil.send(resp, "我的业绩_" + user.getUsername(), workbook);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}
