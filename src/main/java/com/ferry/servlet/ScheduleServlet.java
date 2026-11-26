package com.ferry.servlet;

import com.ferry.dao.RouteDAO;
import com.ferry.dao.ScheduleDAO;
import com.ferry.dao.ShipDAO;
import com.ferry.entity.Route;
import com.ferry.entity.Schedule;
import com.ferry.entity.Ship;
import com.ferry.entity.User;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

/** 班次管理：list / add / edit / cancel，含时间冲突校验与取消退票事务，仅管理员可用 */
@WebServlet("/admin/schedule")
public class ScheduleServlet extends HttpServlet {
    private final ScheduleDAO dao = new ScheduleDAO();
    private final RouteDAO routeDAO = new RouteDAO();
    private final ShipDAO shipDAO = new ShipDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isAdmin(req, resp)) return;
        String action = req.getParameter("action");
        if (action == null) action = "list";

        try {
            if ("cancel".equals(action)) {
                int id = parseInt(req.getParameter("id"));
                dao.cancelSchedule(id);   // 事务：退票 + 座位回滚 + 逻辑删除
                resp.sendRedirect(req.getContextPath() + "/admin/schedule?msg=" + enc("班次已取消，相关订单已自动全额退票"));
                return;
            }
            if ("status".equals(action)) {
                int id = parseInt(req.getParameter("id"));
                String to = req.getParameter("to");
                String err = dao.updateStatus(id, to);
                String msg = err == null ? "班次状态已改为「" + to + "」" : err;
                resp.sendRedirect(req.getContextPath() + "/admin/schedule?msg=" + enc(msg));
                return;
            }
            loadListPage(req, resp, req.getParameter("msg"));
        } catch (SQLException e) {
            e.printStackTrace();
            loadListPage(req, resp, "操作失败：" + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isAdmin(req, resp)) return;
        String action = req.getParameter("action");
        String idStr = req.getParameter("id");

        Schedule s = new Schedule();
        if (idStr != null && !idStr.trim().isEmpty()) s.setId(parseInt(idStr));
        s.setRouteId(parseInt(req.getParameter("routeId")));
        s.setShipId(parseInt(req.getParameter("shipId")));
        try {
            s.setDepartTime(parseTime(req.getParameter("departTime")));
        } catch (Exception e) {
            resp.sendRedirect(req.getContextPath() + "/admin/schedule?msg=" + enc("发船时间格式应为 yyyy-MM-dd HH:mm"));
            return;
        }
        s.setPrice(new BigDecimal(req.getParameter("price")));
        s.setTotalSeats(parseInt(req.getParameter("totalSeats")));
        if ("edit".equals(action)) s.setStatus(req.getParameter("status"));

        // 时间冲突校验：同一艘船同一发船时间不能有两个未删除班次
        if (dao.timeConflict(s.getShipId(), s.getDepartTime(), s.getId())) {
            resp.sendRedirect(req.getContextPath() + "/admin/schedule?msg=" + enc("时间冲突：该船在这个发船时间已有班次"));
            return;
        }

        try {
            if ("edit".equals(action)) {
                dao.update(s);
                resp.sendRedirect(req.getContextPath() + "/admin/schedule?msg=" + enc("保存成功"));
            } else {
                dao.insertWithSeats(s);   // 事务：插入班次 + 初始化座位
                resp.sendRedirect(req.getContextPath() + "/admin/schedule?msg="
                        + enc("班次创建成功，已自动生成 " + s.getTotalSeats() + " 个座位"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            resp.sendRedirect(req.getContextPath() + "/admin/schedule?msg=" + enc("保存失败：" + e.getMessage()));
        }
    }

    /** 加载列表页需要的数据：班次列表 + 航线/船舶下拉选项 */
    private void loadListPage(HttpServletRequest req, HttpServletResponse resp, String msg)
            throws ServletException, IOException {
        req.setAttribute("list", dao.findAll());
        req.setAttribute("routes", routeDAO.findAll());
        req.setAttribute("ships", shipDAO.findAll());
        req.setAttribute("msg", msg);
        req.getRequestDispatcher("/admin/schedule.jsp").forward(req, resp);
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

    private int parseInt(String s) {
        try { return Integer.parseInt(s == null ? "0" : s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private Timestamp parseTime(String s) throws Exception {
        return new Timestamp(new SimpleDateFormat("yyyy-MM-dd HH:mm").parse(s.trim()).getTime());
    }

    private String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }
}
