package pl.edashi.payroll.servlet;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;

@WebServlet("/PayrollServlet1")
public class PayrollServlet1 extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final String DB_URL = "jdbc:mysql://10.10.40.126:3306/dbkalkulator2025?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "EDASHI";
    private static final String DB_PASS = "Egas2025@)@%";
    private static final int BASE_HOURS = 160;
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        int employeeIdInput = Integer.parseInt(request.getParameter("employeeIdInput"));
                
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // Retrieve base salary
        	double baseSalary = 0;
            PreparedStatement stmt = conn.prepareStatement("SELECT salary_month FROM employees WHERE idemployees = ?");
            stmt.setInt(1, employeeIdInput);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                baseSalary = rs.getDouble("salary_month");
            }
            // Retrieve work hours
            double totalHours = 0;
            stmt = conn.prepareStatement("SELECT SUM(hours_worked) AS total_hours FROM work_hours WHERE employee_id = ?");
            stmt.setInt(1, employeeIdInput);
            rs = stmt.executeQuery();
            if (rs.next()) {
            	totalHours = rs.getDouble("total_hours");
            }

            // Apply calculations
            double overtimeRate = baseSalary / BASE_HOURS * 1.5; // Example: 1.5x rate for overtime
            double overtimePay = totalHours > BASE_HOURS ? (totalHours - BASE_HOURS) * overtimeRate : 0;
            double netSalary = baseSalary + overtimePay;

            // Send response to frontend
            response.setContentType("text/html");
            response.getWriter().println("<h2>Payroll Calculation</h2>");
            response.getWriter().println("<p>Base Salary: " + baseSalary + "</p>");
            response.getWriter().println("<p>Total Hours: " + totalHours + "</p>");
            response.getWriter().println("<p>Overtime Pay: " + overtimePay + "</p>");
            response.getWriter().println("<p><strong>Net Salary: " + netSalary + "</strong></p>");
        } catch (SQLException e) {
            throw new ServletException("Database error", e);
        }
    }
}
