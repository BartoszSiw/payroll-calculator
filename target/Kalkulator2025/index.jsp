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
    <style>
        body { font-family: Arial, sans-serif; text-align: center; margin: 50px; }
        input { padding: 10px; margin: 10px; }
        button { padding: 10px; margin: 10px; cursor: pointer; }

        /* Modal Styles */
        .modal {
            display: none; /* Hidden by default */
            position: fixed;
            left: 50%;
            top: 50%;
            transform: translate(-50%, -50%);
            background: white;
            padding: 20px;
            border-radius: 10px;
            box-shadow: 0px 0px 10px gray;
            z-index: 1000;
        }

        /* Overlay Background */
        .overlay {
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.5);
            z-index: 999;
        }
        <!-- #employeeForm { display: none; padding: 20px; border: 1px solid #ccc; width: 300px; margin: auto; } -->
    </style>
<script>

    </script>
	<!-- 
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
  <button onclick="showaddEmployeeModal()">Add Employee</button>
  <button>View Employee List</button>
  <div class="overlay" id="overlay"></div>
  <div class="modal" id="addEmployeeModal">
  <h3>Add New Employee</h3>

  <form action="AddEmployeeServlet" method="post">
        <label for="firstName">First Name:</label>
        <input type="text" id="firstName" name="firstName" required><br>

        <label for="lastName">Last Name:</label>
        <input type="text" id="lastName" name="lastName" required><br>

        <label for="email">Email:</label>
        <input type="email" id="email" name="email" required><br>

        <label for="jobTitleLab">Job Title:</label>
        <input type="text" id="jobTitleLab" name="jobTitleLab"><br>

        <!--<label for="salary_month">Salary per month:</label>
        <input type="number" step="0.01" id="salary_month" name="salary_month" required><br>-->

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
    <label for="employeeSelect">Choose an Employee:</label>
		<select id="employeeSelect" onchange="fetchEmployeeData()">
    	<option value="">Select an employee...</option>
		</select><br>
    <button onclick="showgetEmployeeModal(1)">View Employee 1</button>
    <button onclick="showgetEmployeeModal(2)">View Employee 2</button>
    <button onclick="showgetEmployeeModal(5)">View Employee 5</button>

    <!-- <p id="empName">Loading employee name...</p>
    <p id="jobTitle">Loading job title...</p>-->
    <div class="overlay" id="overlay"></div>
    <div class="modal" id="getEmployeeModal">
        <h3>Employee Details</h3>
        
		
        <p><strong>Employee Name:</strong> <span id="empName">Loading...</span></p>
        <p><strong>Job Title:</strong> <span id="jobTitle">Loading...</span></p>

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
    <script>
    function showgetEmployeeModal(employeeId) {
		let employeeIdStr = String(employeeId).trim();
        let url = `GetEmployeeServlet?idemployees=`+employeeIdStr;
        document.getElementById("getEmployeeModal").style.display = "block";
        document.getElementById("overlay").style.display = "block";
    	//console.log("Fetching URL:", url); //fetch(`GetEmployeeServlet?idemployees=${employeeId}`)
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
               // if (empNameEl) empNameEl.innerText = `${data.first_name} ${data.last_name}`;
                //if (jobTitleEl) jobTitleEl.innerText = data.job_title;  // Ensure input field uses `.value`
                /*try {
                    let jsonData = JSON.parse(rawData); // Safely parse JSON
                    console.log("Parsed JSON:", jsonData);
                } catch (error) {
                    console.error("JSON Parse Error:", error);
                }*/
            	//return JSON.parse(data); // Safely parse JSON
            	//document.getElementById("empName").innerText = data.first_name ? data.first_name + " " + data.last_name : "No Name Found";
    			//document.getElementById("jobTitle").innerText = data.job_title || "No Job Title Found";
    			//console.log("empName exists?", document.getElementById("empName"));
                //console.log("jobTitle exists?", document.getElementById("jobTitle"));
    			
                
                
                //document.getElementById("email").innerText = data.email;
               
                //try {
                   // return JSON.parse(data); // Parse JSON only if valid
                //} catch (error) {
                  //  console.error("JSON Parse Error:", error);
               // }
            	//return JSON.parse(data);
                //document.getElementById("baseSalary").value = data.salary_month;
                //document.getElementById("hoursWorked").value = data.hours_worked;
                //document.getElementById("overtimeHours").value = data.overtime_hours;
            })
                //.then(data => console.log("Parsed Data:", data))
        .catch(error => console.error("Error fetching employee data:", error));
            //.then(parsedData => console.log("Parsed Data:", parsedData));
        //.catch(error => console.error("Error fetching employee data:", error));
    }
        
        
    function closegetEmployeeModal() {
        document.getElementById("getEmployeeModal").style.display = "none";
        document.getElementById("overlay").style.display = "none";
    }
        </script>
  </body>
</html>
