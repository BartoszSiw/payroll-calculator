package pl.edashi.converter.service;

import java.util.Optional;

import pl.edashi.dms.model.DmsParsedDocument;

public class ProcessingResult {
    public enum Status { OK, SKIPPED, ERROR }

    private final Status status;
    private final Object parsedObject;          // the original object returned by processSingleDocument
    private final Optional<String> xmlFragment; // optional fragment for grouped output

    public ProcessingResult(Status status, Object parsedObject, Optional<String> xmlFragment) {
        this.status = status;
        this.parsedObject = parsedObject;
        this.xmlFragment = xmlFragment == null ? Optional.empty() : xmlFragment;
    }

    public Status getStatus() { return status; }
    public Optional<Object> getParsedObject() { return Optional.ofNullable(parsedObject); }
    public Optional<String> getXmlFragment() { return xmlFragment; }
}
