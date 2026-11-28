package com.ferry.servlet;

import com.ferry.dao.CheckInDAO;
import com.ferry.entity.User;
import com.ferry.util.Page;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Map;

/** 售票员检票控制器：检票、检票记录、检票统计 */
@WebServlet({"/seller/checkin", "/admin/checkin"})
public class CheckInServlet extends HttpServlet {
    private final CheckInDAO dao = new CheckInDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isSeller(req, resp)) return;
        String action = req.getParameter("action");
        String view = "/seller/checkin.jsp";

        if ("records".equals(action)) {
            int pageSize = 15;
            int total = dao.countCheckIns();
            int page = Page.clamp(Page.parse(req.getParameter("page"), 1), total, pageSize);
            req.setAttribute("pageData", new Page<Map<String, Object>>(
                    dao.listCheckIns(page, pageSize), page, pageSize, total));
            req.setAttribute("pageUrl", "checkin?action=records&page=");
            view = "/seller/checkin_records.jsp";
        } else if ("stats".equals(action)) {
            req.setAttribute("stats", dao.checkInStats());
            view = "/seller/checkin_stats.jsp";
        }
        req.getRequestDispatcher(view).forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isSeller(req, resp)) return;
        HttpSession session = req.getSession();
        User u = (User) session.getAttribute("loginUser");
        String orderNo = req.getParameter("orderNo");
        if (orderNo == null || orderNo.trim().isEmpty()) {
            resp.sendRedirect(req.getContextPath() + "/seller/checkin?msg=" + enc("请输入订单号"));
            return;
        }
        orderNo = orderNo.trim();
        Integer ticketId = null;
        String ticketIdText = req.getParameter("ticketId");
        if (ticketIdText != null && !ticketIdText.trim().isEmpty()) {
            try {
                ticketId = Integer.valueOf(ticketIdText.trim());
            } catch (NumberFormatException e) {
                resp.sendRedirect(req.getContextPath() + "/seller/checkin?msg="
                        + enc("船票编号格式不正确"));
                return;
            }
        }
        String err = dao.checkIn(orderNo, ticketId, u.getId());
        if (err != null) {
            resp.sendRedirect(req.getContextPath() + "/seller/checkin?msg=" + enc("检票失败：" + err)
                    + "&orderNo=" + enc(orderNo));
        } else {
            req.setAttribute("details", dao.findTicketDetails(orderNo));
            req.setAttribute("msg", "检票成功！乘客已登船");
            req.getRequestDispatcher("/seller/checkin_success.jsp").forward(req, resp);
        }
    }

    private boolean isSeller(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession s = req.getSession(false);
        if (s == null || s.getAttribute("loginUser") == null) {
            resp.sendRedirect(req.getContextPath() + "/login.jsp");
            return false;
        }
        User u = (User) s.getAttribute("loginUser");
        if (!"seller".equals(u.getRole()) && !"admin".equals(u.getRole())) {
            resp.sendRedirect(req.getContextPath() + "/passenger/home");
            return false;
        }
        return true;
    }

    private String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }
}
