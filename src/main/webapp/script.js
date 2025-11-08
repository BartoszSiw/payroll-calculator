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
