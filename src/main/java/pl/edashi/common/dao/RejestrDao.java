package pl.edashi.common.dao;
import java.sql.SQLException;
import java.util.Optional;

import pl.edashi.dms.model.MappingTarget;
import java.math.BigDecimal;
public interface RejestrDao {
    Optional<String> findByFullKey(String fullKey, MappingTarget target) throws SQLException;
    boolean existsByNrIdPlat(String nrIdPlat, MappingTarget target) throws SQLException;
    void insertMapping(String fullKey, String podmiot, String nrFaktury, String nrIdPlat, String hash, String docKey, String dateDoc, BigDecimal kwNetto, BigDecimal kwVat,
    		BigDecimal kwBrutto, BigDecimal kwPlatn, String docRejestr, String dateUpd, MappingTarget target) throws SQLException;
    int updateMapping(String fullKey, String podmiot, String nrFaktury, String nrIdPlat, String hash, String docKey, String dateDoc, BigDecimal kwNetto, BigDecimal kwVat,
    		BigDecimal kwBrutto, BigDecimal kwPlatn, String docRejestr, String dateUpd, MappingTarget target) throws SQLException;
    void incrementCollision(String fullKey, MappingTarget target) throws SQLException;
}
