package pl.edashi.payroll.dao;

import pl.edashi.payroll.model.Employee;
import java.sql.*;

public class EmployeeDAO {

    private final String url = "jdbc:mysql://10.10.40.126:3306/dbkalkulator2025?useSSL=false&serverTimezone=UTC";
    private final String user = "EDASHI";
    private final String pass = "Egas2025@)@%";

    public Employee findById(int id) {
        Employee employee = null;
        String sql = "SELECT idemployees, first_name, last_name, job_title, salary_month FROM employees WHERE idemployees = ?";

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                employee = new Employee(
                    rs.getInt("idemployees"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("job_title"),
                    rs.getDouble("salary_month")
                );
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return employee;
    }
}

