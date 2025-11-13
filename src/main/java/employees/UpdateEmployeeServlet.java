package employees;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.*;
import java.time.ZoneId;

import org.json.JSONObject;
@WebServlet("/UpdateEmployeeServlet")
public class UpdateEmployeeServlet extends HttpServlet {
	ZoneId zoneId = ZoneId.of("UTC");
	private static final long serialVersionUID = 1L;
    private static final String DB_URL = "jdbc:mysql://10.10.40.126:3306/dbkalkulator2025?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "EDASHI";
    private static final String DB_PASS = "Egas2025@)@%";

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader reader = request.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject emp = new JSONObject(sb.toString());

            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);

            String sql = "UPDATE employees SET first_name=?, last_name=?, job_title=?, hire_date=?, salary_month=? WHERE idemployees=?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, emp.getString("first_name"));
            stmt.setString(2, emp.getString("last_name"));
            stmt.setString(3, emp.getString("job_title"));
            stmt.setDate(4, java.sql.Date.valueOf(emp.getString("hire_date")));
            stmt.setDouble(5, emp.getDouble("salary_month"));
            stmt.setInt(6, emp.getInt("idemployees"));

            int rows = stmt.executeUpdate();
            response.setContentType("application/json");
            response.getWriter().write("{\"updatedRows\":" + rows + "}");

            stmt.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
            response.getWriter().write("{\"error\":\"Update failed\"}");
        }
    }
}

