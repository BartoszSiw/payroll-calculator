package pl.edashi.common.dao;
import java.sql.SQLException;
import java.util.Optional;

import pl.edashi.dms.model.MappingTarget;

public interface RejestrDao {
    Optional<String> findByFullKey(String fullKey, MappingTarget target) throws SQLException;
    boolean existsByNrIdPlat(String nrIdPlat, MappingTarget target) throws SQLException;
    void insertMapping(String fullKey, String podmiot, String nrFaktury, String nrIdPlat, String hash, String docKey, MappingTarget target) throws SQLException;
    void incrementCollision(String fullKey, MappingTarget target) throws SQLException;
}
