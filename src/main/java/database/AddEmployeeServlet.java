package database;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;

@WebServlet("/AddEmployeeServlet")
public class AddEmployeeServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final String DB_URL = "jdbc:mysql://10.10.40.126:3306/dbkalkulator2025?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "EDASHI";
    private static final String DB_PASS = "Egas2025@)@%";

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String firstName = request.getParameter("firstName");
        String lastName = request.getParameter("lastName");
        String email = request.getParameter("email");
        String jobTitleAdd = request.getParameter("jobTitleAdd");
        double salary_month = Double.parseDouble(request.getParameter("salary_month"));
        try {
			Class.forName("com.mysql.cj.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "INSERT INTO employees (first_name, last_name, email, job_title, salary_month, hire_date) VALUES (?, ?, ?, ?, ?, CURDATE())";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, firstName);
            stmt.setString(2, lastName);
            stmt.setString(3, email);
            stmt.setString(4, jobTitleAdd);
            stmt.setDouble(5, salary_month);
            stmt.executeUpdate();

            response.getWriter().println("<h2>Employee added successfully!</h2>");
        } catch (SQLException e) {
            throw new ServletException("Database error", e);
        }
    }
}

