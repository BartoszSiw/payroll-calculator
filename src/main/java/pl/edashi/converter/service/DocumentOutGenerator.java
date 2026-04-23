package pl.edashi.converter.service;

import pl.edashi.dms.model.*;
import pl.edashi.dms.mapper.DmsToDmsMapper;
import pl.edashi.converter.service.ProcessingResult;
import pl.edashi.converter.service.ParserRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

public class DocumentOutGenerator {

    private static final Logger log = LoggerFactory.getLogger(DocumentOutGenerator.class);
    private static final Pattern ILLEGAL_FILENAME_CHARS = Pattern.compile("[\\\\/:*?\"<>|]");

    private final ConverterService converterService;
    private final DmsToDmsMapper mapper;

    // thread-safe queues
    private final ConcurrentLinkedQueue<String> resultsQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<DmsDocumentOut> docOutQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<SerializedDoc> finalDocQueue = new ConcurrentLinkedQueue<>();

    public DocumentOutGenerator(ConverterService converterService) {
    	log.info(String.format("1 DocumentOutGenerator: this.converterService this.mapper"));
        this.converterService = converterService;
        this.mapper = new DmsToDmsMapper();
    }

    public enum Status { OK, SKIPPED, ERROR }

    private static String normalizeOddzial(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.isEmpty()) return "";
        // DMS bywa niespójny: "2" vs "02"
        if (s.length() == 1 && Character.isDigit(s.charAt(0))) {
            return "0" + s;
        }
        return s;
    }

    public static class DocOutGenerationResult {
        public final Status status;
        public final DmsDocumentOut docOut;
        public final Object parsedDocument;
        public final String message;

        private DocOutGenerationResult(Status status, DmsDocumentOut docOut, Object parsedDocument, String message) {
            this.status = status;
            this.docOut = docOut;
            this.parsedDocument = parsedDocument;
            this.message = message;
        }

        public static DocOutGenerationResult ok(DmsDocumentOut docOut, Object parsedDocument) {
            return new DocOutGenerationResult(Status.OK, docOut, parsedDocument, "OK");
        }
        public static DocOutGenerationResult skipped(String reason, Object parsedDocument) {
            return new DocOutGenerationResult(Status.SKIPPED, null, parsedDocument, reason);
        }
        public static DocOutGenerationResult error(String message) {
            return new DocOutGenerationResult(Status.ERROR, null, null, message);
        }
    }

    public DocOutGenerationResult generateAndRecord(File savedFile,
                                                    boolean allowUpdate,
                                                    Set<String> filtrRejestry,
                                                    String filtrOddzial) {
        String fileName = savedFile == null ? "<null>" : savedFile.getName();
        log.info(String.format("2 generateAndRecord: processing file=%s allowUpdate=%s", fileName, allowUpdate));

        try {
            ProcessingResult result = converterService.processFile(savedFile, allowUpdate, filtrRejestry, filtrOddzial);
            if (result == null) {
                String msg = "processFile returned null";
                resultsQueue.add(String.format("Błąd w pliku %s: %s", fileName, msg));
                log.error(String.format("2 generateAndRecord mess=%s for file %s", msg, fileName));
                return DocOutGenerationResult.error(msg);
            }

            if (result.getStatus() == ProcessingResult.Status.ERROR) {
                resultsQueue.add(String.format("Błąd w pliku %s", fileName));
                log.error(String.format("2 generateAndRecord Processing failed for file %s", fileName));
                return DocOutGenerationResult.error("Processing error");
            }

            if (result.getStatus() == ProcessingResult.Status.SKIPPED) {
                String reason = "SKIPPED";
                Optional<Object> parsed = result.getParsedObject();
                if (parsed.isPresent() && parsed.get() instanceof SkippedDocument sd) {
                    reason = sd.getType();
                }
                resultsQueue.add(String.format("Pominięto: %s (typ=%s)", fileName, reason));
                log.info(String.format("2 generateAndRecord processFile skipped file %s reason=%s", fileName, reason));
                return DocOutGenerationResult.skipped(reason, parsed.orElse(null));
            }

            Optional<Object> parsedOpt = result.getParsedObject();
            if (parsedOpt.isEmpty()) {
                resultsQueue.add(String.format("Pominięto lub błąd: %s", fileName));
                log.warn(String.format("2 generateAndRecord processFile returned OK but no parsed object for file %s", fileName));
                return DocOutGenerationResult.skipped("No parsed object", parsedOpt.orElse(null));
            }

            Object parsed = parsedOpt.get();
            if (parsed instanceof DmsParsedContractorList sl) {
                resultsQueue.add(String.format("Dodano SL: %s", sl.fileName));
                log.warn(String.format("3 generateAndRecord processFile sl file %s", fileName));
                return DocOutGenerationResult.ok(null, sl); // docOut = null, parsedDocument = SL
            }
            if (!(parsed instanceof DmsParsedDocument)) {
                resultsQueue.add(String.format("Pominięto: %s", fileName));
                log.warn(String.format("3 generateAndRecord Parsed object is not DmsParsedDocument for file %s -> class=%s", fileName, parsed.getClass().getName()));
                return DocOutGenerationResult.skipped("Unsupported parsed type", null);
            }
            DmsParsedDocument d = (DmsParsedDocument) parsed;

            String docRejestr = d.getDaneRejestr() != null ? d.getDaneRejestr().trim().toUpperCase() : "";
            String docOddzial = normalizeOddzial(d.getOddzial());
            if (d.getDocumentType() != null) d.setDocumentType(d.getDocumentType().trim().toUpperCase());

            if (filtrRejestry != null && !filtrRejestry.isEmpty()) {
                if (docRejestr.isEmpty() || !filtrRejestry.contains(docRejestr)) {
                    String reason = "docRejestr not in filter";
                    resultsQueue.add(String.format("Pominięto: %s (%s)", fileName, reason));
                    log.info(String.format("4 generateAndRecord: skipping file %s because docRejestr='%s' not in filter %s", fileName, docRejestr, filtrRejestry));
                    return DocOutGenerationResult.skipped(reason, d);
                }
            }

            if (filtrOddzial != null && !filtrOddzial.isBlank()) {
                String filt = normalizeOddzial(filtrOddzial);
                String effectiveDocOddzial = docOddzial.isBlank() ? "01" : docOddzial;
                if (!filt.equals(effectiveDocOddzial)) {
                    String reason = "docOddzial mismatch";
                    resultsQueue.add(String.format("Pominięto: %s (%s)", fileName, reason));
                    log.info(String.format("5 generateAndRecord: skipping file %s because docOddzial='%s' (effective='%s') != filter '%s' (normalized='%s')",
                            fileName, d.getOddzial(), effectiveDocOddzial, filtrOddzial, filt));
                    return DocOutGenerationResult.skipped(reason, d);
                }
            }

            ParserRegistry.getInstance().setFilters(filtrRejestry);
            ParserRegistry.getInstance().setOddzial(filtrOddzial);

            DmsDocumentOut docOut;
            try {
                docOut = mapper.map(d);
            } catch (Exception e) {
                String em = "Mapping error: " + e.getMessage();
                resultsQueue.add(String.format("Błąd mapowania: %s -> %s", fileName, e.getMessage()));
                log.error(String.format("6 Mapping error for file %s: %s", fileName, e.getMessage()), e);
                return DocOutGenerationResult.error(em);
            }
            if (docOut == null) {
                resultsQueue.add(String.format("Pominięto: %s (mapping produced null)", fileName));
                log.warn(String.format("7 docOut is null for file: %s", fileName));
                return DocOutGenerationResult.skipped("Mapping produced null", d);
            }

            String invoice = determineInvoice(docOut, d, fileName);
            invoice = sanitizeForFilename(invoice);
            docOut.setInvoiceShortNumber(invoice);

            String rawDocType = docOut.getDocumentType();
            String docType = rawDocType != null ? rawDocType.trim().toUpperCase() : "";

            resultsQueue.add(String.format("Przetworzono: %s typ=%s invoice=%s", fileName, docType, invoice));
            // dodajemy docOut do kolejki, watcher może ją pobrać
            docOutQueue.add(docOut);

            log.info(String.format("8 generateAndRecord: file=%s mapped to docOut invoice='%s' docType='%s'", fileName, invoice, docOut.getDocumentType()));
            return DocOutGenerationResult.ok(docOut, d);

        } catch (Throwable t) {
            String em = "Exception: " + t.getMessage();
            resultsQueue.add(String.format("Błąd w pliku %s: %s", fileName, t.getMessage()));
            log.error(String.format("9 Exception in generateAndRecord for file %s", fileName), t);
            return DocOutGenerationResult.error(em);
        }
    }

    /** Pobiera i usuwa wszystkie komunikaty (jak dotychczasowy results list). */
    public List<String> drainResults() {
        List<String> out = new ArrayList<>();
        String s;
        while ((s = resultsQueue.poll()) != null) out.add(s);
        return out;
    }

    /** Podgląd komunikatów bez czyszczenia. */
    public List<String> peekResults() {
        return new ArrayList<>(resultsQueue);
    }

    /** Pobiera i usuwa wszystkie wygenerowane DmsDocumentOut (watcher). */
    public List<DmsDocumentOut> drainDocOuts() {
        List<DmsDocumentOut> out = new ArrayList<>();
        DmsDocumentOut d;
        while ((d = docOutQueue.poll()) != null) out.add(d);
        return out;
    }

    /** Podgląd docOut bez czyszczenia. */
    public List<DmsDocumentOut> peekDocOuts() {
        return new ArrayList<>(docOutQueue);
    }
    public static class SerializedDoc {
        public final String outputGroup;
        public final String xml;
        public final String timestamp;
        public SerializedDoc(String outputGroup, String xml, String timestamp) {
            this.outputGroup = outputGroup;
            this.xml = xml;
            this.timestamp = timestamp;
        }
    }
 // metoda publikująca finalDoc (wywołuje servlet)
    public void publishFinalDoc(String outputGroup, String xml, String timestamp) {
        if (xml == null || xml.isBlank()) {
            log.info(String.format("10 DocumentOutGenerator: publishFinalDoc called but xml empty for group=%s", outputGroup));
            return;
        }
        finalDocQueue.add(new SerializedDoc(outputGroup, xml, timestamp));
        log.info(String.format("10 DocumentOutGenerator: published finalDoc group=%s ts=%s", outputGroup, timestamp));
    }

    // watcher pobiera wszystkie opublikowane finalDocy
    public List<SerializedDoc> drainFinalDocs() {
        List<SerializedDoc> out = new ArrayList<>();
        SerializedDoc s;
        while ((s = finalDocQueue.poll()) != null) out.add(s);
        return out;
    }
    private String determineInvoice(DmsDocumentOut docOut, DmsParsedDocument d, String fileName) {
        if (docOut != null && docOut.getInvoiceShortNumber() != null && !docOut.getInvoiceShortNumber().isBlank()) {
            return docOut.getInvoiceShortNumber();
        } else if (docOut != null && docOut.getInvoiceNumber() != null && !docOut.getInvoiceNumber().isBlank()) {
            return docOut.getInvoiceNumber();
        } else if (d.getInvoiceShortNumber() != null && !d.getInvoiceShortNumber().isBlank()) {
            return d.getInvoiceShortNumber();
        } else if (d.getInvoiceNumber() != null && !d.getInvoiceNumber().isBlank()) {
            return d.getInvoiceNumber();
        } else {
            return d.getSourceFileName() != null ? d.getSourceFileName() : fileName;
        }
    }

    private String sanitizeForFilename(String input) {
        if (input == null) return "";
        String trimmed = input.trim();
        return ILLEGAL_FILENAME_CHARS.matcher(trimmed).replaceAll("_");
    }
}
