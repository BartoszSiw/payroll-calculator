package pl.edashi.converter.service;

public class ProcessedDocument {
    private final String type;
    private final String reason;

    public ProcessedDocument(String type, String reason) {
        this.type = type;
        this.reason = reason;
    }

    public String getType() { return type; }
    public String getReason() { return reason; }
}

