package pl.edashi.converter.model;

public class ConversionResult {
    private final DocumentMetadata metadata;
    private final ConflictStatus status;
    private final String convertedXml;   // docelowy XML (lub null przy konflikcie)
    private final String message;

    public ConversionResult(DocumentMetadata metadata,
                            ConflictStatus status,
                            String convertedXml,
                            String message) {
        this.metadata = metadata;
        this.status = status;
        this.convertedXml = convertedXml;
        this.message = message;
    }

    public DocumentMetadata getMetadata() { return metadata; }
    public ConflictStatus getStatus() { return status; }
    public String getConvertedXml() { return convertedXml; }
    public String getMessage() { return message; }
}
