package com.ferry.filter;

import com.ferry.dao.ScheduleDAO;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import java.io.IOException;

/**
 * 班次状态自动流转过滤器（对标 12306 开车后状态变化）：
 * 每 60 秒把已过发船时间的“售票中”班次改为“已发船”，已过到达时间的改为“已完成”。
 * 节流避免每个请求都查库，静态资源直接放行。
 */
@WebFilter(filterName = "ScheduleStatusFilter", urlPatterns = "/*")
public class ScheduleStatusFilter implements Filter {
    private static final long INTERVAL_MS = 60 * 1000L;
    private static volatile long lastCheck = 0;
    private final ScheduleDAO scheduleDAO = new ScheduleDAO();

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        String path = ((javax.servlet.http.HttpServletRequest) req).getRequestURI();
        if (!isStatic(path) && System.currentTimeMillis() - lastCheck > INTERVAL_MS) {
            synchronized (ScheduleStatusFilter.class) {
                if (System.currentTimeMillis() - lastCheck > INTERVAL_MS) {
                    scheduleDAO.refreshExpired();
                    lastCheck = System.currentTimeMillis();
                }
            }
        }
        chain.doFilter(req, resp);
    }

    private boolean isStatic(String path) {
        return path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".png")
            || path.endsWith(".jpg") || path.endsWith(".gif") || path.endsWith(".ico");
    }

    @Override public void init(FilterConfig cfg) { }
    @Override public void destroy() { }
}
