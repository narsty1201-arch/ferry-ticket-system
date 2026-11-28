package com.ferry.servlet;

import com.ferry.dao.StatDAO;
import com.ferry.entity.User;
import com.ferry.util.DateRange;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

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

        req.setAttribute("summary", dao.sellerSummary(
                user.getId(), range.startInclusive(), range.endExclusive()));
        req.setAttribute("startDate", startText);
        req.setAttribute("endDate", endText);
        req.setAttribute("dateError", dateError);
        req.getRequestDispatcher("/seller/performance.jsp").forward(req, resp);
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
