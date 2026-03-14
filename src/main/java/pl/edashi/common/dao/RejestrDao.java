package pl.edashi.common.dao;
import java.sql.CallableStatement;
//import pl.edashi.payroll.model.Employee;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Types;

import javax.sql.DataSource;

public class RejestrDao {
    //private final DataSource ds;
    //public RejestrDao(String url, String user, String pass) { this.url = url; this.user = user; this.pass = pass; }
	    private final String url = "jdbc:sqlserver://10.10.70.30:1433/CONVERTER?useSSL=false&serverTimezone=UTC";
	    private final String user = "sa";
	    private final String pass = "Enova2023@^";

	    public InsertResult insertMapping(String fullKey, String podmiot, String nrFaktury, String nrIdPlat) throws SQLException {
	        //String sql = "SELECT idemployees, first_name, last_name, job_title, salary_month FROM employees WHERE idemployees = ?";
	        String sql = "{call dbo.insert_rejestr_sprzedazy(?, ?, ?, ?, ?, ?, ?)}";
	        //try (Connection conn = DriverManager.getConnection(url, user, pass);
	             //PreparedStatement stmt = conn.prepareStatement(sql)) {
	        try (Connection conn = DriverManager.getConnection(url, user, pass);
	                CallableStatement cs = conn.prepareCall(sql)) {
	            cs.setString(1, fullKey);
	            cs.setString(2, podmiot);
	            cs.setString(3, nrFaktury);
	            cs.setString(4, nrIdPlat);
	            cs.registerOutParameter(5, Types.INTEGER); // out_status
	            cs.registerOutParameter(6, Types.INTEGER); // out_err_num
	            cs.registerOutParameter(7, Types.NVARCHAR); // out_err_msg

	            cs.execute();

	            int status = cs.getInt(5);
	            int errNum = cs.getInt(6);
	            String errMsg = cs.getString(7);

	            return new InsertResult(status == 1, errNum, errMsg);
	        }
	    }

	    public static class InsertResult {
	        public final boolean inserted;
	        public final Integer errorNumber;
	        public final String errorMessage;
	        public InsertResult(boolean inserted, Integer errorNumber, String errorMessage) {
	            this.inserted = inserted; this.errorNumber = errorNumber; this.errorMessage = errorMessage;
	        }
	    }
	}