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
		<!-- 
		    <style>
        
    </style>
    <script>
        function showForm() {
            document.getElementById("employeeForm").style.display = "block";
        }
    </script>
	<link rel="stylesheet" type="text/css" href="styles.css">
	-->
  </head>
  
  <body>
  <h2>Welcome to Payroll System</h2>
  <button onclick="showaddEmployeeModal()">Nowy pracownik</button>
  <button>Pokaz osoby</button>
  <label for="employeeSelect">Wybierz pracownika:</label>
	<select id="employeeSelect" onchange="fetchEmployeeData()">
    	<option value="">Wybierz pracownika...</option>
	</select><br>
  <div class="overlay" id="overlay"></div>
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
            document.getElementById("overlay").style.display = "block";
        }

        function closeaddEmployeeModal() {
            document.getElementById("addEmployeeModal").style.display = "none";
            document.getElementById("overlay").style.display = "none";
        }
    </script>
    <form action="calculatePayroll" method="post">
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
    <div class="overlay" id="overlay"></div>
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
    <!--<button onclick="showgetEmployeeModal(2)">View Employee 2</button>
    <button onclick="showgetEmployeeModal(5)">View Employee 5</button>

     <p id="empName">Loading employee name...</p>
    <p id="jobTitle">Loading job title...</p>-->
    <script>
    function showgetEmployeeModal(employeeId) {
		let employeeIdStr = String(employeeId).trim();
        let url = `GetEmployeeServlet?idemployees=`+employeeIdStr;
        document.getElementById("getEmployeeModal").style.display = "block";
        document.getElementById("overlay").style.display = "block";
        fetch(url)
        .then(response => response.json()) // Read raw response instead of JSON first
            .then(data => {
            	console.log("Parsed JSON:", data);  // Debug output        }
            	if (!data) {
                    console.error("Error: Empty response received from server.");
                    return;
                }
            	document.getElementById("empName").innerText = data.first_name + " " + data.last_name;
            	document.getElementById("jobTitle").innerText = data.job_title;
            })
        .catch(error => console.error("Error fetching employee data:", error));
    }
        
        
    function closegetEmployeeModal() {
        document.getElementById("getEmployeeModal").style.display = "none";
        document.getElementById("overlay").style.display = "none";
    }
        </script>
        <script>
        document.addEventListener("DOMContentLoaded", function () {
        	console.log("JavaScript Loaded!");
            fetch(`GetListEmployees`)
                .then(response => response.text())
                .then(text => {
                	console.log("Raw Response Text:", text);
                	let data = JSON.parse(text); // Convert manually to JSON
                	console.log("Employee Data Received:", data);
                    let select = document.getElementById("employeeSelect");
                    data.forEach(employee => {
                        let option = document.createElement("option");
                        option.value = employee.idemployees;
                        option.innerText = employee.last_name;
                        //option.innerText = employee.hire_date;
                        //option.innerText = employee.salary_month;
                        select.appendChild(option);
                    });
                })
                .catch(error => console.error("Error fetching employee list:", error));
        });

        function fetchEmployeeData() {
            let employeeIdGetEmpl = document.getElementById("employeeSelect").value;
            document.getElementById("getEmployeeModal").style.display = "block";
            document.getElementById("overlay").style.display = "block";
            console.log("employeeIdGetEmpl:", employeeIdGetEmpl);
            if (!employeeIdGetEmpl) return; // Prevent empty request
					let employeeIdStrr = String(employeeIdGetEmpl).trim();
        			let url = `GetEmployeeServlet?idemployees=`+employeeIdStrr;
            console.log("Fetch URL:", url);
            fetch(url)
                .then(response => response.json())
                .then(data => {
                	console.log("Raw Response Text fetch(url):", data);
                	document.getElementById("empName").innerText = data.first_name + " " + data.last_name;
                    document.getElementById("jobTitle").innerText = data.job_title;
                    document.getElementById("hireDate").innerText = data.hire_date;
                    document.getElementById("salaryMonth").innerText = data.salary_month;
                })
                .catch(error => console.error("Error fetching employee data:", error));
        }
        </script>
  </body>
</html>
