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
	   
	   // conv-form.js
	   // Samodzielny skrypt obsługujący formularz konwertera.
	   // Wymaga: elementy o id: convForm, xmlFile, selectedFiles, submitBtn.
	   // Opcjonalnie: atrybut data-enabled na formularzu z CSV włączonych typów (np. "DS,DZ,DK,SL").

	   (function () {
	     'use strict';

	     function parseEnabled(csv) {
	       if (!csv) return [];
	       return csv.split(',').map(s => s.trim()).filter(Boolean);
	     }

	     function isAnyEnabled(enabledArr, values) {
	       if (!enabledArr || enabledArr.length === 0) return false;
	       for (let v of values) {
	         if (enabledArr.indexOf(v) !== -1) return true;
	       }
	       return false;
	     }

	     function updateFileList(fileInput, selectedFilesEl, submitBtn) {
	       const files = fileInput.files;
	       if (!files || files.length === 0) {
	         selectedFilesEl.textContent = 'Brak wybranych plików.';
	         submitBtn.disabled = true;
	         return;
	       }
	       submitBtn.disabled = false;
	       const list = Array.from(files).map(f => {
	         const sizeKb = (f.size / 1024).toFixed(1);
	         return `${f.name} (${sizeKb} KB)`;
	       });
	       selectedFilesEl.innerHTML = '<strong>Wybrane pliki:</strong><br>' + list.join('<br>');
	     }

	     function initForm(formId) {
	       const form = document.getElementById(formId);
	       if (!form) return;

	       const enabledCsv = form.getAttribute('data-enabled') || '';
	       const enabledArr = parseEnabled(enabledCsv);

	       // jeśli chcesz dodatkowo ukrywać/wyłączać opcje rejestrów po stronie klienta,
	       // możesz to zrobić tutaj. Przykład: zablokuj CD/CP/CR jeśli nie są enabled.
	       const rejestrSelect = form.querySelector('#rejestr');
	       if (rejestrSelect) {
	         const need = ['CD', 'CP', 'CR'];
	         if (!isAnyEnabled(enabledArr, need)) {
	           // ustaw disabled na opcjach CD/CP/CR lub usuń je
	           ['CD', 'CP', 'CR'].forEach(v => {
	             const opt = rejestrSelect.querySelector(`option[value="${v}"]`);
	             if (opt) opt.disabled = true;
	           });
	           // opcjonalnie dodaj informację
	           const hint = document.createElement('div');
	           hint.className = 'field-help';
	           hint.textContent = 'Opcje Z1 tymczasowo wyłączone.';
	           rejestrSelect.parentNode.appendChild(hint);
	         }
	       }

	       const fileInput = form.querySelector('#xmlFile');
	       const selectedFiles = form.querySelector('#selectedFiles');
	       const submitBtn = form.querySelector('#submitBtn');

	       if (!fileInput || !selectedFiles || !submitBtn) return;

	       // inicjalizacja stanu
	       updateFileList(fileInput, selectedFiles, submitBtn);

	       fileInput.addEventListener('change', function () {
	         updateFileList(fileInput, selectedFiles, submitBtn);
	       });

	       form.addEventListener('reset', function () {
	         // po natywnym reset trzeba odczekać chwilę, żeby input się zresetował
	         setTimeout(function () {
	           selectedFiles.textContent = '';
	           submitBtn.disabled = true;
	         }, 0);
	       });

	       form.addEventListener('submit', function (e) {
	         if (!fileInput.files || fileInput.files.length === 0) {
	           e.preventDefault();
	           alert('Wybierz przynajmniej jeden plik XML przed konwersją.');
	         }
	       });
	     }

	     // public API (jeśli chcesz inicjalizować ręcznie)
	     window.ConvForm = {
	       init: initForm
	     };

	     // automatyczna inicjalizacja dla id="convForm"
	     document.addEventListener('DOMContentLoaded', function () {
	       initForm('convForm');
	     });
	   })();
	   //przycisk wyboru plików xml
const bigBtn = document.getElementById('bigFileBtn');
const fileInput = document.getElementById('xmlFile');

	   bigBtn.addEventListener('keydown', (e) => {
	     if (e.key === 'Enter' || e.key === ' ') {
	       e.preventDefault();
	       fileInput.click();
	     }
	   });
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


