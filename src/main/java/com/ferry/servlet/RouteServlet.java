package com.ferry.servlet;

import com.ferry.dao.RouteDAO;
import com.ferry.entity.Route;
import com.ferry.entity.User;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;

/** 航线管理：list / add / edit / delete / status，仅管理员可用 */
@WebServlet("/admin/route")
public class RouteServlet extends HttpServlet {
    private final RouteDAO dao = new RouteDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isAdmin(req, resp)) return;
        String action = req.getParameter("action");
        if (action == null) action = "list";

        try {
            switch (action) {
                case "delete": {
                    int id = parseInt(req.getParameter("id"));
                    dao.logicDelete(id);
                    resp.sendRedirect(req.getContextPath() + "/admin/route?msg=" + enc("航线已删除（逻辑删除）"));
                    return;
                }
                case "status": {
                    int id = parseInt(req.getParameter("id"));
                    String st = "active".equals(req.getParameter("to")) ? "closed" : "active";
                    dao.changeStatus(id, st);
                    resp.sendRedirect(req.getContextPath() + "/admin/route?msg=" + enc("航线状态已更新"));
                    return;
                }
                default:
                    req.setAttribute("list", dao.findAll());
                    req.setAttribute("msg", req.getParameter("msg"));
                    req.getRequestDispatcher("/admin/route.jsp").forward(req, resp);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            req.setAttribute("list", dao.findAll());
            req.setAttribute("msg", "操作失败：" + e.getMessage());
            req.getRequestDispatcher("/admin/route.jsp").forward(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isAdmin(req, resp)) return;
        String action = req.getParameter("action");
        Route r = new Route();
        String idStr = req.getParameter("id");
        if (idStr != null && !idStr.trim().isEmpty()) r.setId(parseInt(idStr));
        r.setRouteName(req.getParameter("routeName").trim());
        r.setStartPort(req.getParameter("startPort").trim());
        r.setEndPort(req.getParameter("endPort").trim());
        r.setDurationMin(parseInt(req.getParameter("durationMin")));
        r.setDescription(req.getParameter("description"));

        try {
            if ("edit".equals(action)) {
                dao.update(r);
            } else {
                dao.insert(r);
            }
            resp.sendRedirect(req.getContextPath() + "/admin/route?msg=" + enc("保存成功"));
        } catch (SQLException e) {
            e.printStackTrace();
            String tip = e.getMessage() != null && e.getMessage().contains("UQ_route")
                    ? "航线名称+出发港+到达港不能重复" : "保存失败：" + e.getMessage();
            resp.sendRedirect(req.getContextPath() + "/admin/route?msg=" + enc(tip));
        }
    }

    /** 管理员校验：未登录或非 admin 跳回登录页 */
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

    private String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }
}
