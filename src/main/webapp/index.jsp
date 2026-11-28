<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    Object user = session.getAttribute("loginUser");
    String ctx = request.getContextPath();
    if (user == null) {
        response.sendRedirect(ctx + "/login.jsp");
        return;
    }
    String role = (String) session.getAttribute("role");
    if ("admin".equals(role)) response.sendRedirect(ctx + "/admin/home");
    else if ("seller".equals(role)) response.sendRedirect(ctx + "/seller/home");
    else response.sendRedirect(ctx + "/passenger/home");
%>
