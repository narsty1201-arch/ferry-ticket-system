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
 * 直接访问 /admin/*.jsp、/seller/*.jsp 也会被拦截，不只依赖 Servlet 内部校验。
 */
@WebFilter(filterName = "AuthFilter", urlPatterns = "/*")
public class AuthFilter implements Filter {

    /** 无需登录即可访问的路径 */
    private static final Set<String> PUBLIC = new HashSet<>(Arrays.asList(
            "/", "/index.jsp", "/login.jsp", "/register.jsp", "/dbtest.jsp",
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
        chain.doFilter(request, response);
    }

    private String homeOf(String role) {
        if ("admin".equals(role)) return "/admin/home.jsp";
        if ("seller".equals(role)) return "/seller/home.jsp";
        return "/passenger/home.jsp";
    }
}
