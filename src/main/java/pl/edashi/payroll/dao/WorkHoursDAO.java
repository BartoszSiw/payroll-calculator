package pl.edashi.payroll.dao;

import pl.edashi.payroll.model.WorkHours;
import java.sql.*;

public class WorkHoursDAO {

    private final String url = "jdbc:mysql://10.10.40.126:3306/dbkalkulator2025?useSSL=false&serverTimezone=UTC";
    private final String user = "EDASHI";
    private final String pass = "Egas2025@)@%";

    public WorkHours findByEmployeeId(int employeeId) {
        WorkHours workHours = null;
        String sql = "SELECT SUM(hours_worked) AS total_hours FROM work_hours WHERE employee_id = ?";

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, employeeId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                double totalHours = rs.getDouble("total_hours");
                if (rs.wasNull()) {
                    totalHours = 0; // handle case when no records exist
                }
                workHours = new WorkHours(employeeId, totalHours);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return workHours;
    }
}

