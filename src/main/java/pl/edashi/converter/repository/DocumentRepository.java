package pl.edashi.converter.repository;

import pl.edashi.converter.model.DocumentMetadata;
import pl.edashi.converter.model.DocumentStructure;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DocumentRepository {

    // klucz: gen_doc_id
    private final Map<String, DocumentStructure> structures = new ConcurrentHashMap<>();
    private final Map<String, DocumentMetadata> metadataMap = new ConcurrentHashMap<>();

    public DocumentStructure findStructureByGenDocId(String genDocId) {
        return structures.get(genDocId);
    }

    public DocumentMetadata findMetadataByGenDocId(String genDocId) {
        return metadataMap.get(genDocId);
    }

    public void save(String genDocId, DocumentMetadata metadata, DocumentStructure structure) {
        metadataMap.put(genDocId, metadata);
        structures.put(genDocId, structure);
    }
}
