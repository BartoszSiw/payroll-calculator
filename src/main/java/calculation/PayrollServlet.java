package calculation;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;

@WebServlet("/calculatePayroll")
public class PayrollServlet extends HttpServlet {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final String DB_URL = "jdbc:mysql://10.10.40.126:3306/dbkalkulator2025?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "EDASHI";
    private static final String DB_PASS = "Egas2025@)@%";

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        int employeeIdInput = Integer.parseInt(request.getParameter("employeeIdInput"));
        try {
			Class.forName("com.mysql.cj.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // Retrieve base salary
            PreparedStatement stmt = conn.prepareStatement("SELECT salary_month FROM employees WHERE idemployees = ?");
            stmt.setInt(1, employeeIdInput);
            ResultSet rs = stmt.executeQuery();
            double baseSalary = rs.next() ? rs.getDouble("salary_month") : 0;

            // Retrieve work hours
            stmt = conn.prepareStatement("SELECT SUM(hours_worked) AS total_hours FROM work_hours WHERE employee_id = ?");
            stmt.setInt(1, employeeIdInput);
            rs = stmt.executeQuery();
            double totalHours = rs.next() ? rs.getDouble("total_hours") : 0;

            // Apply calculations
            double overtimeRate = baseSalary / 160 * 1.5; // Example: 1.5x rate for overtime
            double overtimePay = totalHours > 160 ? (totalHours - 160) * overtimeRate : 0;
            double netSalary = baseSalary + overtimePay;

            // Send response to frontend
            response.setContentType("text/html");
            response.getWriter().println("<h2>Net Salary: " + netSalary + "</h2>");

        } catch (SQLException e) {
            throw new ServletException("Database error", e);
        }
    }
}
