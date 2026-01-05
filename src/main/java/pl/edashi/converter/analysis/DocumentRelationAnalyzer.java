package pl.edashi.converter.analysis;
import pl.edashi.dms.model.DmsParsedDocument;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Enterprise DocumentRelationAnalyzer
 *
 * - wielowątkowy bezpieczny indeks (ConcurrentHashMap)
 * - rejestrowalne ekstraktory numerów (dla różnych typów dokumentów)
 * - normalizacja numerów (domyślna i możliwość podmiany)
 * - generowanie tabeli HTML z powiązaniami
 */
public class DocumentRelationAnalyzer {

        @FunctionalInterface
        public interface Extractor {
            List<ExtractedNumber> extract(DmsParsedDocument doc);
        }

    public static final class ExtractedNumber {
        public final String raw;
        public final String id; // optional id of the document (e.g., internal id)
        public ExtractedNumber(String raw, String id) {
            this.raw = raw;
            this.id = id;
        }
    }

    private final Map<String, List<DocumentIndex>> indexByNormalizedNumber;
    private final List<Extractor> extractors;
    private final Function<String, String> normalizer;
    private final Pattern numberFilterPattern;

    private DocumentRelationAnalyzer(Builder builder) {
        this.indexByNormalizedNumber = new ConcurrentHashMap<>();
        this.extractors = Collections.unmodifiableList(new ArrayList<>(builder.extractors));
        this.normalizer = builder.normalizer;
        this.numberFilterPattern = builder.numberFilterPattern;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
    	private final List<Extractor> extractors = new ArrayList<>();
        private Function<String, String> normalizer = DocumentRelationAnalyzer::defaultNormalizer;
        private Pattern numberFilterPattern = Pattern.compile(".+"); // accept all by default

        /**
         * Rejestruje ekstraktor. Ekstraktor przyjmuje obiekt dokumentu (Twoje DmsParsedDocument)
         * i zwraca listę znalezionych numerów.
         */
        public Builder registerExtractor(Extractor extractor) {
        	
            this.extractors.add(extractor);
            return this;
        }

        /**
         * Ustawia normalizator numerów (np. usuwanie spacji, zer wiodących, zamiana myślników).
         */
        public Builder withNormalizer(Function<String, String> normalizer) {
            this.normalizer = normalizer;
            return this;
        }

        /**
         * Opcjonalny filtr akceptowanych numerów (regex).
         */
        public Builder withNumberFilterPattern(Pattern pattern) {
            this.numberFilterPattern = pattern;
            return this;
        }

        public DocumentRelationAnalyzer build() {
            return new DocumentRelationAnalyzer(this);
        }
    }

    /**
     * Indeksuje listę dokumentów. Dokumenty mogą być dowolnego typu; ekstraktory muszą umieć je obsłużyć.
     */
    public void indexDocuments(List<DmsParsedDocument> documents, Function<DmsParsedDocument, DocumentMeta> metaProvider) {
        // równoległe przetwarzanie
        documents.parallelStream().forEach(doc -> {
            List<ExtractedNumber> extracted = new ArrayList<>();
            for (Extractor ext : extractors) {
                try {
                	List<ExtractedNumber> res = ext.extract(doc);
                    if (res != null) extracted.addAll(res);
                } catch (Exception ignored) { /* ekstraktor nie powinien przerywać indeksowania */ }
            }
            DocumentMeta meta = metaProvider.apply(doc);
            for (ExtractedNumber en : extracted) {
                if (en == null || en.raw == null) continue;
                String raw = en.raw.trim();
                if (!numberFilterPattern.matcher(raw).matches()) continue;
                String normalized = normalizer.apply(raw);
                DocumentIndex di = new DocumentIndex(normalized, raw, meta.getDocType(), meta.getSourceFile(), en.id != null ? en.id : meta.getDocumentId());
                indexByNormalizedNumber.compute(normalized, (k, list) -> {
                    if (list == null) list = Collections.synchronizedList(new ArrayList<>());
                    list.add(di);
                    return list;
                });
            }
        });
    }

    /**
     * Zwraca mapę: normalizedNumber -> lista DocumentIndex
     */
    public Map<String, List<DocumentIndex>> getIndexSnapshot() {
        return indexByNormalizedNumber.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Collections.unmodifiableList(new ArrayList<>(e.getValue()))));
    }

    /**
     * Znajdź powiązania dla konkretnego numeru (raw lub normalized).
     */
    public List<DocumentIndex> findRelationsFor(String rawOrNormalized) {
        String normalized = normalizer.apply(rawOrNormalized);
        return indexByNormalizedNumber.getOrDefault(normalized, Collections.emptyList());
    }

    /**
     * Generuje tabelę HTML z powiązaniami. Kolumny: Numer, Typy dokumentów i lista plików.
     */
    public String generateHtmlTable() {
        Map<String, List<DocumentIndex>> snapshot = getIndexSnapshot();

        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset='utf-8'><style>")
          .append("table{border-collapse:collapse;width:100%}th,td{border:1px solid #ccc;padding:6px;text-align:left}th{background:#f2f2f2}")
          .append(".doclist{font-family:monospace;font-size:0.9em;color:#333}")
          .append("</style></head><body>");
        sb.append("<h2>Powiązania numerów dokumentów</h2>");
        sb.append("<table><thead><tr><th>Numer (znormalizowany)</th><th>Wystąpienia</th><th>Typy dokumentów</th><th>Pliki / Id</th></tr></thead><tbody>");

        snapshot.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String normalizedKey = entry.getKey();
                    List<DocumentIndex> list = entry.getValue();
                 // wybierz reprezentatywny rawNumber (pierwszy unikalny)
                    String displayNumber = list.stream()
                            .map(DocumentIndex::getRawNumber)
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .distinct()
                            .findFirst()
                            .orElse(normalizedKey);
                 // jeśli chcesz, możesz też wyświetlić normalizowaną wersję z zachowanymi slashami:
                 // displayNumber = normalizer.apply(displayNumber);
                    String occurrences = String.valueOf(list.size());
                    String types = list.stream().map(DocumentIndex::getDocType).distinct().collect(Collectors.joining(", "));
                    String files = list.stream()
                            .map(di -> di.getDocType() + " : " + di.getSourceFile() + (di.getDocumentId() != null ? " [" + di.getDocumentId() + "]" : ""))
                            .collect(Collectors.joining("<br/>"));
                    sb.append("<tr>");
                    sb.append("<td>").append(escapeHtml(displayNumber)).append("</td>");
                    sb.append("<td>").append(occurrences).append("</td>");
                    sb.append("<td>").append(escapeHtml(types)).append("</td>");
                    sb.append("<td class='doclist'>").append(files).append("</td>");
                    sb.append("</tr>");
                });

        sb.append("</tbody></table></body></html>");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String defaultNormalizer(String raw) {
        if (raw == null) return "";
        String r = raw.trim().toUpperCase(Locale.ROOT);

        // usuń tylko spacje i znaki niebezpieczne
        r = r.replaceAll("[()\\,]", "");
        r = r.replaceAll("\\s+", "");

        // NIE USUWAJ NIC WIĘCEJ
        return r;
    }


    /**
     * Meta o dokumencie potrzebna do indeksowania.
     */
    public static class DocumentMeta {
        private final String docType;
        private final String sourceFile;
        private final String documentId;

        public DocumentMeta(String docType, String sourceFile, String documentId) {
            this.docType = docType;
            this.sourceFile = sourceFile;
            this.documentId = documentId;
        }

        public String getDocType() { return docType; }
        public String getSourceFile() { return sourceFile; }
        public String getDocumentId() { return documentId; }
        
    }
    // Prosty model indeksu (możesz przenieść do osobnego pliku)
    public static final class DocumentIndex {
        private final String normalizedNumber;
        private final String rawNumber;
        private final String docType;
        private final String sourceFile;
        private final String documentId;

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
    }
}

