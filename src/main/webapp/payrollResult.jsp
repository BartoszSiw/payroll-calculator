<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="pl.edashi.payroll.model.PayrollResult" %>
<html>
<head>
    <title>Payroll Result</title>
    <link rel="stylesheet" type="text/css" href="styles.css">
</head>
<body>
    <h2>Payroll Calculation</h2>

    <%
        PayrollResult result = (PayrollResult) request.getAttribute("payrollResult");
    %>

    <table class="table" id="payrollTable">
        <tr><th>Nazwa</th><th>Wartość</th></tr>
        <tr><td>Base Salary</td><td><%= result.getBaseSalary() %></td></tr>
       <!-- <tr><td>Total Hours</td><td><%= result.getTotalHours() %></td></tr>
        <tr><td>Overtime Pay</td><td><%= result.getOvertimePay() %></td></tr> -->
        <tr><td>Gross Salary</td><td><%= result.getGrossSalary() %></td></tr>
        <tr><td>Social Contributions</td><td><%= result.getSocialContributions() %></td></tr>
        <tr><td>Health Insurance</td><td><%= result.getHealthInsurance() %></td></tr>
        <tr><td>Income Tax</td><td><%= result.getIncomeTax() %></td></tr>
        <tr><td><strong>Net Salary</strong></td><td><strong><%= result.getNetSalary() %></strong></td></tr>
    </table>
</body>
</html>

