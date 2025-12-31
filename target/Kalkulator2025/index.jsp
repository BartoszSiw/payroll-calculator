<%@ page language="java" import="java.util.*" pageEncoding="ISO-8859-1"%>
<%
String path = request.getContextPath();
String basePath = request.getScheme()+"://"+request.getServerName()+":"+request.getServerPort()+path+"/";
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<html>
  <head>
    <base href="<%=basePath%>">
    <meta http-equiv="pragma" content="no-cache">
	<meta http-equiv="cache-control" content="no-cache">
	<meta http-equiv="expires" content="0">    
	<meta http-equiv="keywords" content="keyword1,keyword2,keyword3">
	<meta http-equiv="description" content="This is my page">
    <title>Payroll Calculator 2025</title>
	<link rel="stylesheet" type="text/css" href="styles.css">
	<a href="converter/converter.jsp">Converter</a>
	<script src="script.js"></script>
  </head>
  <body>
<h2>Welcome to Payroll System</h2>
<textarea id="aiInput" placeholder="Ask AI something..."></textarea>
<button onclick="askiAI()">Ask AI</button>
<div id="aiOutput"></div>
  <button onclick="showaddEmployeeModal()">Nowy pracownik</button>
  <button onclick="loadAllEmployees()">Pokaz osoby</button>
<table id="employeesTable" class="hiddenTable">
  <thead>
    <tr>
      <th>ID</th>
      <th>First Name</th>
      <th>Last Name</th>
      <th>Job Title</th>
      <th>Hire Date</th>
      <th>Salary (Month)</th>
    </tr>
  </thead>
  <tbody id="employeesTableBody">
    <!-- rows will be inserted here -->
  </tbody>
</table>
<button onclick="saveAllEmployees()">Save All Employees</button>
  <label for="employeeSelect">Wybierz pracownika:</label>
	<select id="employeeSelect" onchange="fetchEmployeeData()">
    	<option value="">Wybierz pracownika z listy</option>
	</select><br>
  <div class="overlay" id="overlayAdd"></div>
  <div class="modal" id="addEmployeeModal">
  <h3>Add New Employee ?</h3>
  <form action="AddEmployeeServlet" method="post">
        <label for="firstName">First Name:</label>
        <input type="text" id="firstName" name="firstName" required><br>

        <label for="lastName">Last Name:</label>
        <input type="text" id="lastName" name="lastName" required><br>

        <label for="email">Email:</label>
        <input type="email" id="email" name="email" required><br>

        <label for="jobTitleAdd">Job Title:</label>
        <input type="text" id="jobTitleAdd" name="jobTitleAdd"><br>

        <label for="salary_month">Salary per month:</label>
        <input type="number" step="0.01" id="salary_month" name="salary_month" required><br>

        <button type="submit">Add Employee</button>
        <button type="button" onclick="closeaddEmployeeModal('addEmployeeModal')">Cancel</button>
    </form>
      </div>
          <script>
        function showaddEmployeeModal() {
            document.getElementById("addEmployeeModal").style.display = "block";
            document.getElementById("overlayAdd").style.display = "block";
        }

        function closeaddEmployeeModal() {
            document.getElementById("addEmployeeModal").style.display = "none";
            document.getElementById("overlayAdd").style.display = "none";
        }
    </script>
    <form action="PayrollServlet" method="post">
        <label for="employeeIdInput">Enter Employee ID:</label>
        <input type="number" id="employeeIdInput" name="employeeIdInput" required>
        <br>
        <button type="submit">Calculate Salary</button>
    </form>
	    <form action="UserList" method="GET">
        <label for="name">Enter your name:</label>
        <input type="text" id="name" name="name">
        <input type="submit" value="Submit">
        </form>
        <form action="UserList" method="POST">
        	<button type="submit">Post Something</button>
    </form>
    <h2>Employee List</h2>
    <button onclick="showgetEmployeeModal(1)">View Employee 1</button>
    <div class="overlay" id="overlayGet"></div>
    <div class="modal" id="getEmployeeModal">
        <h3>Employee Details</h3>
        <p><strong>Employee Name:</strong> <span id="empName">Loading...</span></p>
        <p><strong>Job Title:</strong> <span id="jobTitle">Loading...</span></p>
        <p><strong>Data zatrudnienia:</strong> <span id="hireDate">Loading...</span></p>
        <p><strong>Stawka:</strong> <span id="salaryMonth">Loading...</span></p>

        <form action="UpdateEmployeeServlet" method="post">
            <label>Base Salary:</label>
            <input type="number" id="baseSalary" name="baseSalary"><br>

            <label>Hours Worked:</label>
            <input type="number" id="hoursWorked" name="hoursWorked"><br>

            <label>Overtime Hours:</label>
            <input type="number" id="overtimeHours" name="overtimeHours"><br>

            <button type="submit">Update Data</button>
            <button type="button" onclick="closegetEmployeeModal('getEmployeeModal')">Close</button>     
        </form>
    </div>
    <form action="ImportEmployees" method="post" enctype="multipart/form-data">
    <input type="file" name="file" accept=".csv,.xlsx">
    <button type="submit">Import</button>
</form>
  </body>
</html>
