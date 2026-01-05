package pl.edashi.converter.analysis;

import java.util.Objects;

/**
 * Niemutowalny model indeksu dokumentu.
 */
public final class DocumentIndex {
    private final String normalizedNumber;
    private final String rawNumber;
    private final String docType;
    private final String sourceFile;
    private final String documentId; // opcjonalne unikalne id dokumentu w repozytorium

    public DocumentIndex(String normalizedNumber, String rawNumber, String docType, String sourceFile, String documentId) {
        this.normalizedNumber = normalizedNumber;
        this.rawNumber = rawNumber;
        this.docType = docType;
        this.sourceFile = sourceFile;
        this.documentId = documentId;
    }

    public String getNormalizedNumber() { return normalizedNumber; }
    public String getRawNumber() { return rawNumber; }
    public String getDocType() { return docType; }
    public String getSourceFile() { return sourceFile; }
    public String getDocumentId() { return documentId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentIndex)) return false;
        DocumentIndex that = (DocumentIndex) o;
        return Objects.equals(normalizedNumber, that.normalizedNumber) &&
               Objects.equals(documentId, that.documentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(normalizedNumber, documentId);
    }

    @Override
    public String toString() {
        return "DocumentIndex{" +
                "normalizedNumber='" + normalizedNumber + '\'' +
                ", rawNumber='" + rawNumber + '\'' +
                ", docType='" + docType + '\'' +
                ", sourceFile='" + sourceFile + '\'' +
                ", documentId='" + documentId + '\'' +
                '}';
    }
}
