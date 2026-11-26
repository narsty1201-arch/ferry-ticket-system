package com.ferry.servlet;

import com.ferry.dao.ShipDAO;
import com.ferry.entity.Ship;
import com.ferry.entity.User;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;

/** 船舶管理：list / add / edit / delete，仅管理员可用 */
@WebServlet("/admin/ship")
public class ShipServlet extends HttpServlet {
    private final ShipDAO dao = new ShipDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isAdmin(req, resp)) return;
        String action = req.getParameter("action");
        if (action == null) action = "list";

        try {
            if ("delete".equals(action)) {
                int id = parseInt(req.getParameter("id"));
                int used = dao.usedBySchedules(id);
                if (used > 0) {
                    resp.sendRedirect(req.getContextPath() + "/admin/ship?msg="
                            + enc("该船舶仍有 " + used + " 个在用班次，不能删除"));
                } else {
                    dao.logicDelete(id);
                    resp.sendRedirect(req.getContextPath() + "/admin/ship?msg=" + enc("船舶已删除（逻辑删除）"));
                }
                return;
            }
            req.setAttribute("list", dao.findAll());
            req.setAttribute("msg", req.getParameter("msg"));
            req.getRequestDispatcher("/admin/ship.jsp").forward(req, resp);
        } catch (SQLException e) {
            e.printStackTrace();
            req.setAttribute("list", dao.findAll());
            req.setAttribute("msg", "操作失败：" + e.getMessage());
            req.getRequestDispatcher("/admin/ship.jsp").forward(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8");
        if (!isAdmin(req, resp)) return;
        String action = req.getParameter("action");
        Ship s = new Ship();
        String idStr = req.getParameter("id");
        if (idStr != null && !idStr.trim().isEmpty()) s.setId(parseInt(idStr));
        s.setShipName(req.getParameter("shipName").trim());
        s.setTotalSeats(parseInt(req.getParameter("totalSeats")));
        s.setShipType(req.getParameter("shipType"));
        s.setDescription(req.getParameter("description"));

        try {
            if ("edit".equals(action)) {
                dao.update(s);
                resp.sendRedirect(req.getContextPath() + "/admin/ship?msg=" + enc("保存成功"));
            } else {
                // 新增船：事务同时生成船舶座位
                dao.insertWithSeats(s);
                resp.sendRedirect(req.getContextPath() + "/admin/ship?msg=" + enc("保存成功，已自动生成 " + s.getTotalSeats() + " 个座位"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            String tip = e.getMessage() != null && e.getMessage().contains("UQ_ship")
                    ? "船名不能重复" : "保存失败：" + e.getMessage();
            resp.sendRedirect(req.getContextPath() + "/admin/ship?msg=" + enc(tip));
        }
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

    private String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return ""; }
    }
}
