function showForm() {
    document.getElementById("employeeForm").style.display = "block";
}
function showgetEmployeeModal(employeeId) {
	let employeeIdStr = String(employeeId).trim();
    let url = `GetEmployeeServlet?idemployees=`+employeeIdStr;
    document.getElementById("getEmployeeModal").style.display = "block";
    document.getElementById("overlayGet").style.display = "block";
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
    document.getElementById("overlayGet").style.display = "none";
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
	       document.getElementById("overlayGet").style.display = "block";
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
	   function loadAllEmployees() {
	       fetch("GetListEmployees")
	           .then(response => response.json())
	           .then(data => {
	               //let data = JSON.parse(text);   // convert to JSON array
	               //console.log("Employee list:", data);

	               //let table = document.getElementById("employeesTable");
	               let tbody = document.getElementById("employeesTableBody");
	               tbody.innerHTML = ""; // clear old rows

	               data.forEach(emp => {
	                   let row = document.createElement("tr");

	                   row.innerHTML = `
	                       <td>${emp.idemployees}</td>
	                       <td contenteditable="true">${emp.first_name}</td>
	                       <td contenteditable="true">${emp.last_name}</td>
	                       <td contenteditable="true">${emp.job_title}</td>
	                       <td contenteditable="true">${emp.hire_date}</td>
	                       <td contenteditable="true">${emp.salary_month}</td>
						   <td><button onclick="saveEmployee(this)">Save</button></td>
	                   `;

	                   tbody.appendChild(row);
	               });

	              document.getElementById("employeesTable").classList.remove("hiddenTable"); // show table
	           })
	           .catch(error => console.error("Error loading employees:", error));
	   }
	   function saveEmployee(button, showAlert = false) {
	       let row = button.closest("tr");
	       let emp = {
	           idemployees: row.cells[0].innerText,
	           first_name: row.cells[1].innerText,
	           last_name: row.cells[2].innerText,
	           job_title: row.cells[3].innerText,
	           hire_date: row.cells[4].innerText,
	           salary_month: row.cells[5].innerText
	       };

	       fetch("UpdateEmployeeServlet", {
	           method: "POST",
	           headers: { "Content-Type": "application/json" },
	           body: JSON.stringify(emp)
	       })
	       .then(response => response.json())
		   .then(result => {
		       if (showAlert) {
		           alert("Employee updated successfully!");
		       } else {
				row.style.backgroundColor = "#d4edda";
		           console.log("Updated employee:", emp.idemployees);
		       }
		   })
	       .catch(error => console.error("Error updating employee:", error));
	   }
	   function saveAllEmployees() {
	       let rows = document.querySelectorAll("#employeesTable tbody tr");
	       rows.forEach(row => {
	           // simulate clicking each row’s Save button
	           saveEmployee(row.querySelector("button"), false);
	       });
		   alert("All employees updated successfully!");
	   }
function askiAI() {
	       let text = document.getElementById("aiInput").value;

	       fetch("AskAIServlet", {
	           method: "POST",
	           headers: { "Content-Type": "application/json" },
	           body: JSON.stringify({ prompt: text })
	       })
	       .then(res => res.json())
	       .then(data => {
	           document.getElementById("aiOutput").innerText = data.answer;
	       })
	       .catch(err => console.error("AI error:", err));
	   }
	  /* not use for now: function saveAllEmployees() {
	       let rows = document.querySelectorAll("#employeesTable tbody tr");
	       let employees = [];

	       rows.forEach(row => {
			let cells = row.querySelectorAll("td");
	           let emp = {
	               idemployees: cells[0].innerText,
	               first_name: cells[1].innerText,
	               last_name: cells[2].innerText,
	               job_title: cells[3].innerText,
	               hire_date: cells[4].innerText,
	               salary_month: cells[5].innerText
	           };
	           employees.push(emp);
	       });

	       fetch("UpdateAllEmployeesServlet", {
	           method: "POST",
	           headers: { "Content-Type": "application/json" },
	           body: JSON.stringify(employees)
	       })
	       .then(response => response.json())
	       .then(result => {
	           alert("Updated " + result.updatedRows + " employees successfully!");
	       })
	       .catch(error => console.error("Error updating employees:", error));
	   }*/


