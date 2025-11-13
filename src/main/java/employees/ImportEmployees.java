package employees;
import jakarta.servlet.*;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.ZoneId;
import java.util.Date;
import java.util.Iterator;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
@WebServlet("/ImportEmployees")
@MultipartConfig
public class ImportEmployees extends HttpServlet {
	ZoneId zoneId = ZoneId.of("UTC");
	private static final long serialVersionUID = 1L;
    private static final String DB_URL = "jdbc:mysql://10.10.40.126:3306/dbkalkulator2025?useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "EDASHI";
    private static final String DB_PASS = "Egas2025@)@%";
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
		Part filePart = request.getPart("file");
        if (filePart == null) {
            response.getWriter().println("No file uploaded!");
            return;
        }

        try (InputStream inputStream = filePart.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {

            XSSFSheet sheet = workbook.getSheetAt(0); // first sheet
            Iterator<Row> rowIterator = sheet.iterator();

            // Skip header row
            if (rowIterator.hasNext()) rowIterator.next();

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                    if (row == null) continue; // skip null rows
                    if (row.getCell(0) == null) continue; // skip empty first row

                //int id = (int) row.getCell(0).getNumericCellValue();
                String first_name = row.getCell(1).getStringCellValue();
                String last_name = row.getCell(2).getStringCellValue();
                String email = row.getCell(3).getStringCellValue();
                String job_title = row.getCell(4).getStringCellValue();
                Date hire_date = row.getCell(5).getDateCellValue();
                Cell salaryCell = row.getCell(6);
                double salary_month = 0;

                if (salaryCell != null) {
                    if (salaryCell.getCellType() == CellType.NUMERIC) {
                        salary_month = salaryCell.getNumericCellValue();
                    } else if (salaryCell.getCellType() == CellType.STRING) {
                        salary_month = Double.parseDouble(salaryCell.getStringCellValue().replace(",", "."));
                    }
                }
                // For now, just print to console (later: save to DB)
                Class.forName("com.mysql.cj.jdbc.Driver");
                Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                String sql = "INSERT INTO employees (first_name, last_name, email, job_title, hire_date, salary_month) VALUES (?, ?, ?, ?, ?, ?)";
                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setString(1, first_name);
                ps.setString(2, last_name);
                ps.setString(3, email);
                ps.setString(4, job_title);
                ps.setDate(5, new java.sql.Date(hire_date.getTime()));
                ps.setDouble(6, salary_month);
                ps.executeUpdate();

                //System.out.printf("%s, %s, %s, %s, %s, %.2f%n",
                        //first_name, last_name, email, job_title, hire_date, salary_month);
            }

            response.getWriter().println("Employees imported successfully!");

        } catch (Exception e) {
            e.printStackTrace();
            response.getWriter().println("Error reading Excel file: " + e.getMessage());
        }
    }
}

