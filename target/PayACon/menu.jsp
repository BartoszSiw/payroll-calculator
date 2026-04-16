<%@ page contentType="text/html; charset=UTF-8" %>
<%
String payrollParam = application.getInitParameter("payacon.payroll.enabled");
boolean showPayroll = payrollParam == null || payrollParam.trim().isEmpty()
    || Boolean.parseBoolean(payrollParam.trim());
%>
<link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/styles.css">
<div class="navbar">
    <a href="${pageContext.request.contextPath}/index.jsp">HOME</a>
    <a href="${pageContext.request.contextPath}/converter/converter.jsp">CONVERTER</a>
    <% if (showPayroll) { %>
    <a href="${pageContext.request.contextPath}/payroll/payroll.jsp">PAYROLL</a>
    <% } %>
</div>
