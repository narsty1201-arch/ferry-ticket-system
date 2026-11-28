package com.ferry.filter;

import com.ferry.entity.User;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 全局权限过滤器：路径级角色控制，防止垂直越权。
 * 业务 JSP 已放入 WEB-INF，只能由 Servlet 转发，不能再被浏览器直接访问。
 */
@WebFilter(filterName = "AuthFilter", urlPatterns = "/*")
public class AuthFilter implements Filter {

    /** 无需登录即可访问的路径 */
    private static final Set<String> PUBLIC = new HashSet<>(Arrays.asList(
            "/", "/index.jsp", "/login.jsp", "/register.jsp",
            "/login", "/register", "/logout"
    ));

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String ctx = req.getContextPath();
        String path = req.getRequestURI();
        if (path.startsWith(ctx)) path = path.substring(ctx.length());
        if (path.isEmpty()) path = "/";

        // 静态资源放行
        if (path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.equals("/favicon.ico")) {
            chain.doFilter(request, response);
            return;
        }

        // 业务 JSP 只能由 Servlet 转发（FORWARD 不会再经过本过滤器），
        // 浏览器直接请求时一律返回 404，避免绕过 Servlet 的权限与参数校验。
        if (path.endsWith(".jsp")
                && (path.startsWith("/admin/") || path.startsWith("/seller/") || path.startsWith("/passenger/"))) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 公开页面放行
        if (PUBLIC.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession s = req.getSession(false);
        User u = s == null ? null : (User) s.getAttribute("loginUser");
        if (u == null) {
            resp.sendRedirect(ctx + "/login.jsp");
            return;
        }

        String role = u.getRole();
        if (path.startsWith("/admin/") && !"admin".equals(role)) {
            resp.sendRedirect(ctx + homeOf(role));
            return;
        }
        if (path.startsWith("/seller/") && !"seller".equals(role) && !"admin".equals(role)) {
            resp.sendRedirect(ctx + homeOf(role));
            return;
        }
        if (path.startsWith("/passenger/") && !"passenger".equals(role)) {
            resp.sendRedirect(ctx + homeOf(role));
            return;
        }
        chain.doFilter(request, response);
    }

    private String homeOf(String role) {
        if ("admin".equals(role)) return "/admin/home";
        if ("seller".equals(role)) return "/seller/home";
        return "/passenger/home";
    }
}
