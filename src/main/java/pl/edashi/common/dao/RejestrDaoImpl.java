package pl.edashi.common.dao;
import javax.sql.DataSource;

import pl.edashi.common.logging.AppLogger;
import pl.edashi.dms.model.MappingTarget;

import java.sql.*;
import java.util.Optional;
import java.math.BigDecimal;
public class RejestrDaoImpl implements RejestrDao {
	    //private final DataSource ds;
		private final AppLogger log = new AppLogger("RejestrDaoImpl");
	    //private final String url = "jdbc:sqlserver://10.10.70.30:1433/CON_AUTO_JDG?useSSL=false&serverTimezone=UTC";
	    private final String url = "jdbc:sqlserver://10.10.70.30:1433;databaseName=CONVERTER;encrypt=false;trustServerCertificate=true";
	    private final String user = "sa";
	    private final String pass = "Enova2023@^";
	    //public RejestrDaoImpl(DataSource ds) { this.ds = ds; }

	    @Override
	    public Optional<String> findByFullKey(String fullKey, MappingTarget target) throws SQLException {
	    	final String SQL_INSERT_PURCHASES = "SELECT nr_id_plat FROM dbo.rejestr_zakupow WHERE full_key = ?";
	    	final String SQL_INSERT_SALES = "SELECT nr_id_plat FROM dbo.rejestr_sprzedazy WHERE full_key = ?";
    	    String sql = (target == MappingTarget.PURCHASES)
	    	        ? SQL_INSERT_PURCHASES
	    	        : SQL_INSERT_SALES;
	        try (Connection c = DriverManager.getConnection(url, user, pass);
	             PreparedStatement ps = c.prepareStatement(sql)) {
	            ps.setString(1, fullKey);
	            try (ResultSet rs = ps.executeQuery()) {
	                if (rs.next()) return Optional.ofNullable(rs.getString(1));
	                return Optional.empty();
	            }
	        }
	    }

	    @Override
	    public boolean existsByNrIdPlat(String nrIdPlat, MappingTarget target) throws SQLException {
	    	final String SQL_INSERT_PURCHASES = "SELECT 1 FROM dbo.rejestr_zakupow WHERE nr_id_plat = ?";
	    	final String SQL_INSERT_SALES = "SELECT 1 FROM dbo.rejestr_sprzedazy WHERE nr_id_plat = ?";
    	    String sql = (target == MappingTarget.PURCHASES)
	    	        ? SQL_INSERT_PURCHASES
	    	        : SQL_INSERT_SALES;
	        try {
	            // wymuszenie rejestracji sterownika (pomaga przy izolacji classloaderów)
	            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
	            //log.info(String.format("Sterownik JDBC zarejestrowany: %s", "com.microsoft.sqlserver.jdbc.SQLServerDriver"));
	        } catch (ClassNotFoundException cnfe) {
	            //log.error(String.format("Sterownik JDBC nie znaleziony: %s", cnfe.getMessage()), cnfe);
	            throw new SQLException("Driver not found", cnfe);
	        }

	        try (Connection c = DriverManager.getConnection(url, user, pass);
	             PreparedStatement ps = c.prepareStatement(sql)) {
	        	//log.info(String.format("Uzyskano połączenie DB (autocommit=%s) dla url='%s '", c.getAutoCommit(), url));
	            ps.setString(1, nrIdPlat);
	            try (ResultSet rs = ps.executeQuery()) {
	                return rs.next();
	            }
	        }
	    }

	    @Override
	    public void insertMapping(String fullKey, String podmiot, String nrFaktury, String nrIdPlat,String hash, String docKey, String dateDoc, BigDecimal kwNetto, BigDecimal kwVat,
	    		BigDecimal kwBrutto, BigDecimal kwPlatn, String docRejestr, MappingTarget target) throws SQLException {
	    	final String SQL_INSERT_SALES = "INSERT INTO dbo.rejestr_sprzedazy(full_key, podmiot, nr_faktury, nr_id_plat, hash_val, created_at, collision_cnt, doc_id, date_document,kw_netto, kw_vat, kw_brutto, kwota_platn, rejestr) " +
                    "VALUES (?, ?, ?, ?, ?, SYSUTCDATETIME(), 0, ?, ?, ?, ?, ?, ?, ?)";
	        final String SQL_INSERT_PURCHASES = "INSERT INTO dbo.rejestr_zakupow(full_key, podmiot, nr_faktury, nr_id_plat, hash_val, created_at, collision_cnt, doc_id,date_document,kw_netto, kw_vat, kw_brutto, kwota_platn, rejestr) VALUES (?, ?, ?, ?, ?, SYSUTCDATETIME(), 0, ?, ?, ?, ?, ?, ?, ?)";
	    	//log.info(String.format("RejestrDaoImpl fullKey='%s' nrIdPlat='%s' podmiot='%s' nrFaktury='%s' hash='%s' docKey='%s'",fullKey, nrIdPlat, podmiot, nrFaktury, hash, docKey));
    	    String sql = (target == MappingTarget.PURCHASES)
	    	        ? SQL_INSERT_PURCHASES
	    	        : SQL_INSERT_SALES;
	        //log.info(String.format("Ins SQL: ='%s '", sql));
	        try (Connection conn = DriverManager.getConnection(url, user, pass);
	        		PreparedStatement ps = conn.prepareStatement(sql)) {
	                //CallableStatement cs = conn.prepareCall(sql)) {
	            ps.setString(1, fullKey);
	            ps.setString(2, podmiot);
	            ps.setString(3, nrFaktury);
	            ps.setString(4, nrIdPlat);
	            ps.setString(5, hash);
	            ps.setString(6, docKey);
	            ps.setString(7, dateDoc);
	            ps.setBigDecimal(8, kwNetto);
	            ps.setBigDecimal(9, kwVat);
	            ps.setBigDecimal(10, kwBrutto);
	            ps.setBigDecimal(11, kwPlatn);
	            ps.setString(12, docRejestr);
	            //ps.registerOutParameter(5, Types.INTEGER); // out_status
	            //ps.registerOutParameter(6, Types.INTEGER); // out_err_num
	            //ps.registerOutParameter(7, Types.NVARCHAR); // out_err_msg

	            ps.executeUpdate();

	            //int status = cs.getInt(5);
	            //int errNum = cs.getInt(6);
	            //String errMsg = cs.getString(7);

	            //return new InsertResult(status == 1, errNum, errMsg);
	        }
	    }
	    public void incrementCollision(String fullKey, MappingTarget target) throws SQLException {
	    	final String SQL_INSERT_PURCHASES = "UPDATE dbo.rejestr_zakupow SET collision_cnt = collision_cnt + 1 WHERE full_key = ?";
	    	final String SQL_INSERT_SALES = "UPDATE dbo.rejestr_sprzedazy SET collision_cnt = collision_cnt + 1 WHERE full_key = ?";
    	    String upd = (target == MappingTarget.PURCHASES)
	    	        ? SQL_INSERT_PURCHASES
	    	        : SQL_INSERT_SALES;
    	    try (Connection conn = DriverManager.getConnection(url, user, pass);
	             PreparedStatement ps = conn.prepareStatement(upd)) {
	            ps.setString(1, fullKey);
	            int updated = ps.executeUpdate();
	            log.info(String.format("incrementCollision executed, rows=%d for fullKey='%s '", updated, fullKey));
	        } catch (SQLException ex) {
	            log.error(String.format("SQL error in incrementCollision: SQLState=%s, ErrorCode=%d, Message=%s",
	                    ex.getSQLState(), ex.getErrorCode(), ex.getMessage()), ex);
	            throw ex;
	        }
	    }
	    /*public static class InsertResult {
	        public final boolean inserted;
	        public final Integer errorNumber;
	        public final String errorMessage;
	        public InsertResult(boolean inserted, Integer errorNumber, String errorMessage) {
	            this.inserted = inserted; this.errorNumber = errorNumber; this.errorMessage = errorMessage;
	        }
	    }*/
	}

