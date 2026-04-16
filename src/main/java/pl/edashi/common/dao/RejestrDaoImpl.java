package pl.edashi.common.dao;
import javax.sql.DataSource;

import pl.edashi.common.logging.AppLogger;
import pl.edashi.converter.service.ConverterConfig;
import pl.edashi.dms.model.MappingTarget;

import java.sql.*;
import java.util.Optional;
import java.math.BigDecimal;
public class RejestrDaoImpl implements RejestrDao {
	    //private final DataSource ds;
		private final AppLogger log = new AppLogger("RejestrDaoImpl");
	    private final String url;
	    private final String user;
	    private final String pass;

	    public RejestrDaoImpl() {
	        ConverterConfig cfg = new ConverterConfig();
	        this.url = cfg.getRejestrJdbcUrl();
	        this.user = cfg.getRejestrJdbcUser();
	        this.pass = cfg.getRejestrJdbcPassword();
	    }

	    //public RejestrDaoImpl(DataSource ds) { this.ds = ds; }

	    @Override
	    public Optional<String> findByFullKey(String fullKey, MappingTarget target) throws SQLException {
	    	final String SQL_INSERT_PURCHASES = "SELECT 1 FROM dbo.rejestr_zakupow WHERE full_key = ?";
	    	final String SQL_INSERT_SALES = "SELECT 1 FROM dbo.rejestr_sprzedazy WHERE full_key = ?";
	    	final String SQL_INSERT_CARD = "SELECT 1 FROM dbo.rejestr_card WHERE full_key = ?";
	    	final String SQL_INSERT_CASH = "SELECT 1 FROM dbo.rejestr_cash WHERE full_key = ?";
	        String sql = switch (target) {
	        case PURCHASES -> SQL_INSERT_PURCHASES;
	        case SALES     -> SQL_INSERT_SALES;
	        case CARD      -> SQL_INSERT_CARD;
	        case CASH      -> SQL_INSERT_CASH;};
	        try {Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
	        } catch (ClassNotFoundException cnfe) {
	            throw new SQLException("Driver not found", cnfe);
	        }
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
	    	final String SQL_INSERT_CARD = "SELECT 1 FROM dbo.rejestr_card WHERE nr_id_plat = ?";
	    	final String SQL_INSERT_CASH = "SELECT 1 FROM dbo.rejestr_cash WHERE nr_id_plat = ?";
	        String sql = switch (target) {
	        case PURCHASES -> SQL_INSERT_PURCHASES;
	        case SALES     -> SQL_INSERT_SALES;
	        case CARD      -> SQL_INSERT_CARD;
	        case CASH      -> SQL_INSERT_CASH;};
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
	    		BigDecimal kwBrutto, BigDecimal kwPlatn, String docRejestr, String datUpd, MappingTarget target) throws SQLException {
	    	final String SQL_INSERT_SALES = "INSERT INTO dbo.rejestr_sprzedazy(full_key, podmiot, nr_faktury, nr_id_plat, hash_val, created_at, collision_cnt, doc_id, date_document,kw_netto, kw_vat, kw_brutto, kwota_platn, rejestr, date_upd) " +
                    "VALUES (?, ?, ?, ?, ?, SYSUTCDATETIME(), 0, ?, ?, ?, ?, ?, ?, ?, ?)";
	        final String SQL_INSERT_PURCHASES = "INSERT INTO dbo.rejestr_zakupow(full_key, podmiot, nr_faktury, nr_id_plat, hash_val, created_at, collision_cnt, doc_id,date_document,kw_netto, kw_vat, kw_brutto, kwota_platn, rejestr, date_upd) VALUES (?, ?, ?, ?, ?, SYSUTCDATETIME(), 0, ?, ?, ?, ?, ?, ?, ?, ?)";
	        final String SQL_INSERT_CARD = "INSERT INTO dbo.rejestr_card(full_key, podmiot, nr_faktury, nr_id_plat, hash_val, created_at, collision_cnt, doc_id,date_document, kwota, date_upd) VALUES (?, ?, ?, ?, ?, SYSUTCDATETIME(), 0, ?, ?, ?, ?)";
	        final String SQL_INSERT_CASH = "INSERT INTO dbo.rejestr_cash(full_key, podmiot, nr_faktury, nr_id_plat, hash_val, created_at, collision_cnt, doc_id,date_document, kwota, date_upd) VALUES (?, ?, ?, ?, ?, SYSUTCDATETIME(), 0, ?, ?, ?, ?)";
	        //log.info(String.format("RejestrDaoImpl fullKey='%s' nrIdPlat='%s' podmiot='%s' nrFaktury='%s' hash='%s' docKey='%s'",fullKey, nrIdPlat, podmiot, nrFaktury, hash, docKey));
	        String sql = switch (target) {
	        case PURCHASES -> SQL_INSERT_PURCHASES;
	        case SALES     -> SQL_INSERT_SALES;
	        case CARD      -> SQL_INSERT_CARD;
	        case CASH      -> SQL_INSERT_CASH;
	    };
        try {Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException cnfe) {
            throw new SQLException("Driver not found", cnfe);
        }
	        //log.info(String.format("Ins SQL: ='%s '", sql));
	        try (Connection conn = DriverManager.getConnection(url, user, pass);
	        		PreparedStatement ps = conn.prepareStatement(sql)) {
	            ps.setString(1, fullKey);
	            ps.setString(2, podmiot);
	            ps.setString(3, nrFaktury);
	            ps.setString(4, nrIdPlat);
	            ps.setString(5, hash);
	            ps.setString(6, docKey);
	            ps.setString(7, dateDoc);
	        	switch (target) {
	            case SALES, PURCHASES -> {
	            ps.setBigDecimal(8, kwNetto);
	            ps.setBigDecimal(9, kwVat);
	            ps.setBigDecimal(10, kwBrutto);
	            ps.setBigDecimal(11, kwPlatn);
	            ps.setString(12, docRejestr);
	            ps.setString(13, datUpd);
	            }
	            case CARD, CASH -> {
	            	ps.setBigDecimal(8, kwPlatn);
	            	ps.setString(9, datUpd);
	            }
	            default -> throw new IllegalStateException("Unexpected target: " + target);
	        	}
	            ps.executeUpdate();
	            //return new InsertResult(status == 1, errNum, errMsg);
	        }
	    }
	    public int updateMapping(String fullKey, String podmiot, String nrFaktury, String nrIdPlat,String hash, String docKey, String dateDoc, BigDecimal kwNetto, BigDecimal kwVat,
	    		BigDecimal kwBrutto, BigDecimal kwPlatn, String docRejestr, String datUpd, MappingTarget target) throws SQLException {
	    	final String SQL_UPDATE_SALES = "UPDATE dbo.rejestr_sprzedazy SET " +
	                 "podmiot = ?, " +
	                 "nr_faktury = ?, " +
	                 "nr_id_plat = ?, " +
	                 "hash_val = ?, " +
	                 "doc_id = ?, " +
	                 "date_document = ?, " +
	                 "kw_netto = ?, " +
	                 "kw_vat = ?, " +
	                 "kw_brutto = ?, " +
	                 "kwota_platn = ?, " +
	                 "rejestr = ?, " +
	                 "date_upd = ? " +
	                 "WHERE full_key = ?";
	        final String SQL_UPDATE_PURCHASES = "UPDATE dbo.rejestr_zakupow SET " +
	                 "podmiot = ?, " +
	                 "nr_faktury = ?, " +
	                 "nr_id_plat = ?, " +
	                 "hash_val = ?, " +
	                 "doc_id = ?, " +
	                 "date_document = ?, " +
	                 "kw_netto = ?, " +
	                 "kw_vat = ?, " +
	                 "kw_brutto = ?, " +
	                 "kwota_platn = ?, " +
	                 "rejestr = ?, " +
	                 "date_upd = ? " +
	                 "WHERE full_key = ?";
	        final String SQL_UPDATE_CARD = "UPDATE dbo.rejestr_card SET " +
            "podmiot = ?, " +
            "nr_faktury = ?, " +
            "nr_id_plat = ?, " +
            "hash_val = ?, " +
            "doc_id = ?, " +
            "date_document = ?, " +
            "kwota = ?, " +
            "date_upd = ? " +
            "WHERE full_key = ?";
	        final String SQL_UPDATE_CASH = "UPDATE dbo.rejestr_cash SET " +
	                "podmiot = ?, " +
	                "nr_faktury = ?, " +
	                "nr_id_plat = ?, " +
	                "hash_val = ?, " +
	                "doc_id = ?, " +
	                "date_document = ?, " +
	                "kwota = ?, " +
	                "date_upd = ? " +
	                "WHERE full_key = ?";
	        String sql = switch (target) {
	        case PURCHASES -> SQL_UPDATE_PURCHASES;
	        case SALES     -> SQL_UPDATE_SALES;
	        case CARD      -> SQL_UPDATE_CARD;
	        case CASH      -> SQL_UPDATE_CASH;
	    };
        try {Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException cnfe) {
            throw new SQLException("Driver not found", cnfe);
        }
    	    try (Connection cn = DriverManager.getConnection(url, user, pass);
    	         PreparedStatement us = cn.prepareStatement(sql)) {
    	    	us.setString(1, podmiot);
    	        us.setString(2, nrFaktury);
    	        us.setString(3, nrIdPlat);
    	        us.setString(4, hash);
    	        us.setString(5, docKey);
    	        us.setString(6, dateDoc);
    	        switch (target) {
    	         case SALES, PURCHASES -> {
    	        if (kwNetto != null) us.setBigDecimal(7, kwNetto); else us.setNull(7, Types.DECIMAL);
    	        if (kwVat != null)   us.setBigDecimal(8, kwVat);   else us.setNull(8, Types.DECIMAL);
    	        if (kwBrutto != null)us.setBigDecimal(9, kwBrutto);else us.setNull(9, Types.DECIMAL);
    	        if (kwPlatn != null) us.setBigDecimal(10, kwPlatn); else us.setNull(10, Types.DECIMAL);
    	        us.setString(11, docRejestr);
    	        us.setString(12, datUpd == null ? null : datUpd);
    	        us.setString(13, fullKey);
    	         } case CARD, CASH -> {
    	        	 if (kwNetto != null) us.setBigDecimal(7, kwNetto); else us.setNull(7, Types.DECIMAL);
    	        	 us.setString(8, datUpd == null ? null : datUpd);
 	            	us.setString(9, fullKey);
 	            }
 	            default -> throw new IllegalStateException("Unexpected target: " + target);
 	        	}
    	       return us.executeUpdate();
}
}
	    public void incrementCollision(String fullKey, MappingTarget target) throws SQLException {
	    	final String SQL_UPDATE_PURCHASES = "UPDATE dbo.rejestr_zakupow SET collision_cnt = collision_cnt + 1 WHERE full_key = ?";
	    	final String SQL_UPDATE_SALES = "UPDATE dbo.rejestr_sprzedazy SET collision_cnt = collision_cnt + 1 WHERE full_key = ?";
	    	final String SQL_UPDATE_CARD = "UPDATE dbo.rejestr_card SET collision_cnt = collision_cnt + 1 WHERE full_key = ?";
	    	final String SQL_UPDATE_CASH = "UPDATE dbo.rejestr_cash SET collision_cnt = collision_cnt + 1 WHERE full_key = ?";
	        String upd = switch (target) {
	        case PURCHASES -> SQL_UPDATE_PURCHASES;
	        case SALES     -> SQL_UPDATE_SALES;
	        case CARD      -> SQL_UPDATE_CARD;
	        case CASH      -> SQL_UPDATE_CASH;
	    };
        try {Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        } catch (ClassNotFoundException cnfe) {
            throw new SQLException("Driver not found", cnfe);
        }
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

