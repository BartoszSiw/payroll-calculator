package employees;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.time.ZoneId;

@WebServlet("/GetEmployeeServlet")
public class GetEmployeeServlet extends HttpServlet {
	ZoneId zoneId = ZoneId.of("UTC");
	private static final long serialVersionUID = 1L;
	private static final String DB_URL = "jdbc:mysql://10.10.40.126:3306/dbkalkulator2025?useSSL=false&serverTimezone=UTC";//?useSSL=false&serverTimezone=UTC
    private static final String DB_USER = "EDASHI";
    private static final String DB_PASS = "Egas2025@)@%";

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        int employeeIdGet = Integer.parseInt(request.getParameter("idemployees"));
/*
    	String employeeIdParam = request.getParameter("idemployees");
    	
    	if (employeeIdParam.isEmpty()) {
    		response.getWriter().write("{\"error\":\"Invalid Employee ID\"}");
    	}*/
    	//System.out.println("Received Employee ID: " + employeeIdParam);

        //int employeeId = Integer.parseInt(request.getParameter("idemployees"));
        try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
			System.out.println("Connected to Database Successfully GetEmployee");
            String sql = "SELECT * FROM employees WHERE idemployees = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, employeeIdGet);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) { // Move cursor to the first row
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"first_name\":\"" + rs.getString("first_name") +
                        "\", \"last_name\":\"" + rs.getString("last_name") +
                        "\", \"job_title\":\"" + rs.getString("job_title") +  
                        "\", \"salary_month\":\"" + rs.getDouble("salary_month") + 
                        "\", \"hire_date\":\"" + rs.getDate("hire_date")+"\"" +"}");
                //String firstName = rs.getString("first_name");
                //String lastName = rs.getString("last_name");
                System.out.println("Employee Name: " + rs.getString("first_name") + " " + rs.getString("last_name")+ " " + "jobTitle: " + rs.getString("job_title") + " " + "salary_month: " + rs.getString("salary_month")+" " + "hire_date: " + rs.getString("hire_date"));
            } else {
                System.out.println("No employee found!");
            }
            rs.close();
            stmt.close();
            conn.close();
            //System.out.println("Servlet received request for Employee idemployees: " + request.getParameter("idemployees"));
            //String jsonResponse = "{\"first_name\":\"" + rs.getString("first_name") + "\", \"last_name\":\"" + rs.getString("last_name") + "\", \"job_title\":\"" + rs.getString("job_title") + "\"}";
            //System.out.println("JSON Response: " + jsonResponse);
            //response.setContentType("application/json");
            //response.setCharacterEncoding("UTF-8");
            //response.getWriter().write("{\"first_name\":\"" + rs.getString("first_name") +
                    //"\", \"last_name\":\"" + rs.getString("last_name") +
                    //"\", \"job_title\":\"" + rs.getString("job_title") + "\"" + "}");// +
                    //"\", \"salary_month\":" + rs.getDouble("salary_month") +
                    //", \"hours_worked\":" + rs.getDouble("hours_worked") +
                    //", \"overtime_hours\":" + rs.getDouble("overtime_hours") + "}");
		} catch (SQLException | ClassNotFoundException e) {
			// TODO Auto-generated catch block
			//System.out.println("Driver mysql not found exception: " + e);
			e.printStackTrace();
			response.getWriter().write("{\"SQL error\":\"Database connection failed\"}");
		}
        /*try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "SELECT * FROM employees WHERE idemployees = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, employeeId);
            ResultSet rs = stmt.executeQuery();
        	// Simulated JSON response for debugging
            System.out.println("Servlet received request for Employee idemployees: " + request.getParameter("idemployees"));
            //String jsonResponse = "{\"first_name\":\"" + rs.getString("first_name") + "\", \"last_name\":\"" + rs.getString("last_name") + "\"}";
            //System.out.println("JSON Response: " + jsonResponse);
            //response.getWriter().write(jsonResponse);
         
            if (!rs.next()) { 
                response.getWriter().write("{\"error\":\"No employee found\"}");
                return;
            }

            if (rs.next()) {
                String jjsonResponse = "{\"first_name\":\"" + rs.getString("first_name") + "\", \"last_name\":\"" + rs.getString("last_name") + "\"}";
                System.out.println("JSON Response: " + jjsonResponse);
                response.getWriter().write(jjsonResponse);

                response.getWriter().write("{\"first_name\":\"" + rs.getString("first_name") +
                        "\", \"last_name\":\"" + rs.getString("last_name") +
                        "\", \"job_title\":\"" + rs.getString("job_title"));// +
                        //"\", \"salary_month\":" + rs.getDouble("salary_month") +
                        //", \"hours_worked\":" + rs.getDouble("hours_worked") +
                        //", \"overtime_hours\":" + rs.getDouble("overtime_hours") + "}");
            }
        } catch (SQLException e) {
            throw new ServletException("Database error", e);
        }*/
    }
}

