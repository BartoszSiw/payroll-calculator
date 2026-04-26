<%@ page contentType="text/html; charset=UTF-8" %>
<%
String payrollParam = application.getInitParameter("payacon.payroll.enabled");
boolean showPayroll = payrollParam == null || payrollParam.trim().isEmpty()
    || Boolean.parseBoolean(payrollParam.trim());

// also honor license modules (if available)
try {
    Object licObj = application.getAttribute("payacon.licenseStatus");
    if (licObj instanceof pl.edashi.converter.license.LicenseStatus lic) {
        showPayroll = showPayroll && lic.moduleEnabled("payroll", false);
    }
} catch (Throwable ignored) {}
%>
<link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/styles.css">
<div class="navbar">
    <a href="${pageContext.request.contextPath}/index.jsp">HOME</a>
    <a href="${pageContext.request.contextPath}/converter/converter.jsp">CONVERTER</a>
    <% if (showPayroll) { %>
    <a href="${pageContext.request.contextPath}/payroll/payroll.jsp">PAYROLL</a>
    <% } %>
    <a href="${pageContext.request.contextPath}/LogoutServlet" style="margin-left:auto;">WYLOGUJ</a>
</div>
