package employees;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.time.ZoneId;

import org.json.JSONArray;
import org.json.JSONObject;



@WebServlet("/GetListEmployees")
public class GetListEmployees extends HttpServlet {
	ZoneId zoneId = ZoneId.of("UTC");
	private static final long serialVersionUID = 1L;
	private static final String DB_URL = "jdbc:mysql://10.10.40.126:3306/dbkalkulator2025?useSSL=false&serverTimezone=UTC";//?useSSL=false&serverTimezone=UTC
    private static final String DB_USER = "EDASHI";
    private static final String DB_PASS = "Egas2025@)@%";

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        JSONArray employeesArray = new JSONArray();
        try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			Connection con = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
			System.out.println("Connected to Database Successfully GetList ?");
            String sqll = "SELECT idemployees, first_name, last_name, job_title, hire_date, salary_month FROM employees";
            PreparedStatement stmtt = con.prepareStatement(sqll);
            //stmt.setInt(1, employeeIdGet);
            ResultSet rss = stmtt.executeQuery();
            
            while (rss.next()) {
            	JSONObject employeeObj = new JSONObject();
            	String lastNameEmplo = rss.getString("last_name");
            	String firstNameEmplo = rss.getString("first_name");
            	
            	System.out.println("Found Employees: " + lastNameEmplo + " " + firstNameEmplo); 
            	employeeObj.put("idemployees", rss.getInt("idemployees"));
            	employeeObj.put("first_name", firstNameEmplo);
                employeeObj.put("last_name", lastNameEmplo);
                employeeObj.put("job_title", rss.getString("job_title"));
                employeeObj.put("hire_date", rss.getDate("hire_date").toString());
                employeeObj.put("salary_month", rss.getDouble("salary_month"));
            	employeesArray.put(employeeObj);
            }
        	String jsonResponse = employeesArray.toString();
            response.setContentType("application/json");
            response.getWriter().write(jsonResponse); 
            rss.close();
            stmtt.close();
            con.close();
		} catch (SQLException | ClassNotFoundException e) {
			e.printStackTrace();
			response.getWriter().write("{\"SQL error\":\"Database connection failed\"}");
		}

    }
}

