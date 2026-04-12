package pl.edashi.converter.service;
import pl.edashi.common.dao.RejestrDao;
import pl.edashi.common.logging.AppLogger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import pl.edashi.converter.repository.DocumentRepository;
import pl.edashi.dms.model.DmsParsedContractorList;
import pl.edashi.dms.parser.DmsParser;
import pl.edashi.dms.parser.DmsParserSL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.io.StringReader;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.xml.sax.InputSource;
//import com.sun.org.apache.xerces.internal.impl.dv.dtd.StringDatatypeValidator;
import pl.edashi.converter.analysis.DocumentRelationAnalyzer;
import pl.edashi.converter.analysis.DocumentRelationAnalyzer.ExtractedNumber;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.model.DmsPosition;
import pl.edashi.dms.model.MappingTarget;
import java.util.ArrayList; 
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
public class ConverterService {

    private final DocumentRepository repository;
    private final RejestrDao rejestrDao;
    private final XmlStructureComparator structureComparator;
    private final AppLogger log = new AppLogger("ConverterService");

    public ConverterService(DocumentRepository repository, RejestrDao rejestrDao) {
        this.repository = repository;
        this.rejestrDao = rejestrDao;
        this.structureComparator = new XmlStructureComparator();
    }
    public ProcessingResult processFile(File file, boolean allowUpdate, Set<String> filtrRejestry, String filtrOddzial) {
        try {
            String xml = Files.readString(file.toPath(), StandardCharsets.UTF_8);

            // call your existing parser/processor
            Object parsed = processSingleDocument(xml, file.getName(), allowUpdate, filtrRejestry, filtrOddzial);
            log.info(String.format("1 processFile: file=%s allowUpdate=%s",file.getName(),allowUpdate));
            if (parsed instanceof SkippedDocument skipped) {
            	log.info(String.format("2 processFile skipped: file=%s allowUpdate=%s",file.getName(),allowUpdate));
                return new ProcessingResult(ProcessingResult.Status.SKIPPED, parsed, Optional.empty());
            }

            // if your processSingleDocument sometimes returns an XML fragment string, keep it
            if (parsed instanceof String s && s != null && !s.isBlank()) {
            	log.info(String.format("3 processFile OK: file=%s allowUpdate=%s",file.getName(),allowUpdate));
                return new ProcessingResult(ProcessingResult.Status.OK, parsed, Optional.of(s));
            }

            // if it returns a DmsParsedDocument or DmsParsedContractorList, build optional fragment if needed
            if (parsed instanceof DmsParsedDocument d) {
                String fragment = toXmlFragment(d); // implement toXmlFragment as you need
                log.info(String.format("4 processFile fragment: file=%s allowUpdate=%s",file.getName(),allowUpdate));
                return new ProcessingResult(ProcessingResult.Status.OK, d, Optional.ofNullable(fragment).filter(str -> !str.isBlank()));
            }

            if (parsed instanceof DmsParsedContractorList sl) {
            	 log.info(String.format("5 processFile SL: file=%s allowUpdate=%s",file.getName(),allowUpdate));
                // you may want to produce a fragment or not; here we return the object
                return new ProcessingResult(ProcessingResult.Status.OK, sl, Optional.empty());
            }

            // default: return object as OK but no fragment
            return new ProcessingResult(ProcessingResult.Status.OK, parsed, Optional.empty());

        } catch (Throwable t) {
            log.error("6 Error processing file " + file.getName(), t);
            return new ProcessingResult(ProcessingResult.Status.ERROR, null, Optional.empty());
        }
    }

    // przykładowy helper do budowy fragmentu; dopasuj pola do swoich potrzeb
    private String toXmlFragment(DmsParsedDocument d) {
        StringBuilder sb = new StringBuilder();
        sb.append("<document>");
        sb.append("<type>").append(escapeXml(d.getDocumentType())).append("</type>");
        sb.append("<fullKey>").append(escapeXml(d.getFullKey())).append("</fullKey>");
        sb.append("<filename>").append(escapeXml(d.getSourceFileName())).append("</filename>");
        sb.append("</document>");
        return sb.toString();
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
    public Object processSingleDocument(String xml, String sourceFile,boolean allowUpdate,Set<String> filtrRejestry, String filtrOddzial) throws Exception {
    	
    	
        // 1. parsowanie
    	log.info(String.format("7 processSingleDocument sourceFile=%s filtrRejestry=%s filtrOddzial=%s",sourceFile,filtrRejestry, filtrOddzial));
        Document doc = load(xml);
        if (doc == null) {
        	IllegalArgumentException iae = new IllegalArgumentException("Niepoprawny XML"); 
        	log.error("8 processSingleDocument Nie udało się sparsować XML: " + sourceFile, iae); 
        	throw iae;
        }
        Element root = doc.getDocumentElement(); // <DMS ...>
        if (root == null) throw new IllegalArgumentException("Brak elementu root w pliku: " + sourceFile);
        String type = root.getAttribute("id");

        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("Brak atrybutu id w DMS root: " + sourceFile);
        }
        if ("SL".equalsIgnoreCase(type)) {
            return new DmsParserSL().parse(doc, sourceFile);
        }
        log.info(String.format("9 processSingleDocument parserType=%s",type));
     // obsługa OUT_ONLY: nie traktujemy jako błąd, tylko pomijamy kontrolowanie
        if (ParserRegistry.getInstance().isOutOnly(type)) {
            log.info(String.format("10 processSingleDocument: type=%s is OUT_ONLY -> skipping without error", type));
            return new SkippedDocument(type, String.format("OUT_ONLY: archived to archive/out file=%s", sourceFile));

        }

     // wybór parsera
        DmsParser parser = DmsParserFactory.getParser(type);

        // parsowanie
        DmsParsedDocument d = parser.parse(doc, sourceFile);
        String docRejestr = d.getDaneRejestr() == null ? "" : d.getDaneRejestr().trim().toUpperCase();
        String docOddzial = d.getOddzial() == null ? "" : d.getOddzial().trim();
        ParserRegistry registry = ParserRegistry.getInstance();
        DateFilterRegistry dateFilter = DateFilterRegistry.getInstance();
        log.info(String.format("11 processSingleDocument AFTER PARSE: parsedClass=%s metadata=%s docRejestr=%s docOddzial=%s", d.getClass().getName(),d.getMetadata(),docRejestr,docOddzial));
        LocalDate fromDate = dateFilter.getFromDate();
        LocalDate toDate   = dateFilter.getToDate();
        LocalDate docDate = parseDateFromPossibleFormats(
                d.getMetadata() == null ? null : d.getMetadata().getDate()
        );
        String docType = type.trim().toUpperCase();
        String fullKey = d.getFullKey();
        String nrIdPlat = d.getNrIdPlat();
        String podmiot = d.getPodmiot();
        String numer = d.getInvoiceNumber();
        String hash = d.getHash();
        String docKey = d.getDocKey();
        String dateDoc = docDate.toString();
        String datUpd = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        BigDecimal kwNet = BigDecimal.ZERO;
        BigDecimal kwVat = BigDecimal.ZERO;
        BigDecimal kwBru = BigDecimal.ZERO;
        BigDecimal kwPla = BigDecimal.ZERO;
        if (d.getPayments() != null && !d.getPayments().isEmpty()) {
            kwPla = new BigDecimal(d.getPayments().get(0).kwota.replace(",", "."));
        }
    	//log.info(String.format("DEBUG pre-filter: docType='%s' fullKey='%s' docDate='%s'", docType, fullKey, docDate));
        //log.info(String.format("fullKey='%s ' nrIdPlat='%s ' podmiot='%s ' numer='%s ' hash='%s '",fullKey, nrIdPlat, podmiot, numer, hash));
        //LocalDate docDate = parseDateFromPossibleFormats(d.getMetadata() == null ? null : d.getMetadata().getDate()
        if (!filtrRejestry.isEmpty() && (docRejestr.isEmpty() || !filtrRejestry.contains(docRejestr))) {
        	log.info(String.format("12 processSingleDocument filtrRejestry='%s' docRejestr='%s'", filtrRejestry, docRejestr));
            return new SkippedDocument(d.getDocumentType(), "Rejestr not in filter");
        }
        if (filtrOddzial != null && !filtrOddzial.isBlank() && !filtrOddzial.equals(docOddzial)) {
        	log.info(String.format("13 processSingleDocument filtrOddzial='%s' docOddzial='%s'", filtrOddzial, docOddzial));
            return new SkippedDocument(d.getDocumentType(), "Oddzial not in filter");
        }
        if (!registry.isEnabled(docType)) {
            log.info(String.format("14 processSingleDocument Pominięto: registry disabled docType='%s' fullKey='%s'", docType, fullKey));
            return new SkippedDocument(docType, "Parser disabled");
        }
        if (dateFilter.hasFilter()) {
        	if (!isDateInRange(docDate, fromDate, toDate)) {
        	    log.info(String.format("15 processSingleDocument Pominięto: date out of range docDate='%s' range='%s' - ='%s'", docDate, fromDate, toDate));
        	    return new SkippedDocument(docType, "Date out of range");
        	}
        }

        ///
        /* if (!registry.isEnabled(docType)) {
            log.info(String.format("Pominięto plik %s: typ '%s' nie jest włączony do przetwarzania", sourceFile, docType));
            return new SkippedDocument(docType, "Parser disabled");
        }
        if (dateFilter.hasFilter()) {
            if (!isDateInRange(docDate, fromDate, toDate)) {
                return new SkippedDocument(docType, "Date out of range");
            }
        */ 
        //log.info(String.format("RejestrDao implementation: ='%s '", rejestrDao.getClass().getName()));
        boolean wasUpdated = false;
        try {
        	MappingTarget target = d.getMappingTarget();
            log.info(String.format("16 processSingleDocument fullKey='%s' nrIdPlat='%s' podmiot='%s' target='%s'", fullKey, nrIdPlat, podmiot, target));
            Optional<String> existing = rejestrDao.findByFullKey(fullKey, target);
            if (existing.isPresent()) {
                log.info("17 processSingleDocument Record with fullKey exists (nr_id_plat=" + existing.get() + ")");
                if (allowUpdate) {
                    log.info("18 processSingleDocument allowUpdate=true -> performing UPDATE for fullKey=" + fullKey);
                    int updatedRows = rejestrDao.updateMapping(fullKey, podmiot, numer, nrIdPlat, hash, docKey,
                                             dateDoc, kwNet, kwVat, kwBru, kwPla, docRejestr, datUpd, target);
                    if (updatedRows > 0) {
                        log.info("16 processSingleDocument DB update succeeded for fullKey=" + fullKey + " rows=" + updatedRows);
                        wasUpdated = true;
                    //return new ProcessedDocument(docType, "updated existing record");
                    } else {
                        log.warn("17 processSingleDocument Update affected 0 rows for fullKey=" + fullKey + " — treating as collision");
                        rejestrDao.incrementCollision(fullKey, target);
                        return new SkippedDocument(docType, "update affected 0 rows");
                    }
                } else {
                    rejestrDao.incrementCollision(fullKey, target);
                    log.warn(String.format("18 processSingleDocument Insert skipped: record exists and allowUpdate=%s for fullKey=",allowUpdate,fullKey));
                    return new SkippedDocument(docType, "nrIdPlat conflict on insert");
                }
            }
            try {
            	 // jedna próba insertu — jeśli rekord już istnieje, złapiemy to po kodzie SQL
            	log.info(String.format("19 processSingleDocument DEBUG pre-filter: docType='%s' fullKey='%s' docDate='%s' parsedClass='%s'",
            		    docType, fullKey, docDate, d.getClass().getName()));

            		log.info(String.format("20 processSingleDocument DEBUG registry.isEnabled('%s') = '%s'", docType, registry.isEnabled(docType)));
            		log.info(String.format("21 processSingleDocument DEBUG dateFilter.from='%s' to='%s' hasFilter='%s'",
            		    dateFilter.getFromDate(), dateFilter.getToDate(), dateFilter.hasFilter()));

            		log.info(String.format("22 processSingleDocument DEBUG BEFORE INSERT: fullKey='%s' nrIdPlat='%s' podmiot='%s' target='%s' thread='%s'",
            		    fullKey, nrIdPlat, podmiot, d.getMappingTarget(), Thread.currentThread().getName()));
            		log.info(String.format("23 processSingleDocument DEBUG BEFORE INSERT: fullKey='%s' docKey='%s' thread='%s' parsedHash='%s'",
            			    fullKey, docKey, Thread.currentThread().getName(), System.identityHashCode(d)));

                rejestrDao.insertMapping(fullKey, podmiot, numer, nrIdPlat, hash, docKey, dateDoc, kwNet, kwVat, kwBru, kwPla, docRejestr, datUpd, target);
                log.info(String.format("24 processSingleDocument insertMapping zakończone pomyślnie dla nrIdPlat='%s ' podmiot='%s '", nrIdPlat, podmiot));
            } catch (SQLException ex) {
                //log.error(String.format("SQL Error Code: ='%s ', Message: ='%s '", ex.getErrorCode(), ex.getMessage()), ex);
                int code = ex.getErrorCode();
                if (code == 2627 || code == 2601) { // SQL Server unique violation
                        //log.warn("Unique violation — performing UPDATE instead of skipping");
                	if (allowUpdate) {
                        int updatedRows = rejestrDao.updateMapping(fullKey, podmiot, numer, nrIdPlat, hash, docKey, dateDoc, kwNet, kwVat, kwBru, kwPla, docRejestr, datUpd, target);
                        if (updatedRows > 0) {
                            log.info("25 processSingleDocument Race: update succeeded for fullKey=" + fullKey);
                            wasUpdated = true;
                        } else {
                            rejestrDao.incrementCollision(fullKey, target);
                            return new SkippedDocument(docType, "nrIdPlat conflict on insert (race, update 0 rows)");
                        }
                        //return new ProcessedDocument(docType, "updated existing record");
                    } else {
                        rejestrDao.incrementCollision(fullKey, target);
                        return new SkippedDocument(docType, "nrIdPlat conflict on insert");
                    }
                    } else {
                        rejestrDao.incrementCollision(fullKey, target);
                        log.warn("26 processSingleDocument Insert ignored: unique constraint violation");
                        return new SkippedDocument(docType, "nrIdPlat conflict on insert");
                    }
                }
        } catch (Throwable t) {
            log.error(String.format("27 processSingleDocument Nieoczekiwany błąd przy pliku sourceFile='%s ': %s", sourceFile, t.getMessage()), t);
            //return new SkippedDocument(docType, "DB runtime error"); 
            return new SkippedDocument(docType, String.format("DB runtime error: file=%s", sourceFile));

        }
        return d;
    }
     // SL obsługujemy osobno, bo zwraca inny typ


        // wszystkie inne typy idą przez fabrykę
        

        // wszystkie inne typy idą przez fabrykę
        //DmsParser parser = DmsParserFactory.getParser(type);
        //DmsParsedDocument d = parser.parse(doc, sourceFile);

        /*switch (type) {
        case "DS":
            return new DmsParserDS().parse(doc, sourceFile);
        case "DZ":
            return new DmsParserDZ().parse(doc, sourceFile);
        case "KO":
            return new DmsParserKO().parse(doc, sourceFile);
            
        case "KZ":
            return new DmsParserKZ().parse(doc, sourceFile);

        case "DK":
            return new DmsParserDK().parse(doc, sourceFile);
        case "SL":
        	return new DmsParserSL().parse(doc, sourceFile);

        case "WZ":
            return new DmsParserWZ().parse(doc, sourceFile);
        case "RD":
            return new DmsParserRD().parse(doc, sourceFile);
        case "RO":
            return new DmsParserRO().parse(doc, sourceFile);
        case "RZ":
            return new DmsParserRZ().parse(doc, sourceFile);

        default:
            throw new IllegalArgumentException("Nieobsługiwany typ dokumentu: " + type);
    }*/
    
    public DmsParsedContractorList processContractorDictionary(String xml, String sourceFile) throws Exception {
    	
        Document doc = load(xml);
        
        Element root = doc.getDocumentElement();
        String type = root.getAttribute("id");
        log.info(String.format("28 processContractorDictionary sourceFile=%s",sourceFile));
        if (!"SL".equals(type)) {
            throw new IllegalArgumentException("processContractorDictionary obsługuje tylko typ SL, otrzymano: " + type);
        }
        return new DmsParserSL().parse(doc, sourceFile);
    }
    private Element makeEl(Document doc, String name, String value) {
        Element el = doc.createElement(name);
        el.setTextContent(value);
        return el;
    }
    private Document load(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
       } 
    public String analyzeRelationsAndGenerateHtml(List<DmsParsedDocument> allParsedDocs) {
        DocumentRelationAnalyzer analyzer = DocumentRelationAnalyzer.builder()
        		.withNormalizer(s -> {
        			
        		    if (s == null) return "";
        		    return s.trim()
        		            .toUpperCase()
        		            .replaceAll("[\\s\\-\\\\]+", "") // usuń spacje, myślniki, backslash
        		            .replaceAll("/{2,}", "/");       // znormalizuj podwójne slashe
        		})
        		.registerExtractor(doc -> {
        		    List<ExtractedNumber> out = new ArrayList<>();

        		    String type = doc.getDocumentType() != null
        		            ? doc.getDocumentType().toUpperCase()
        		            : "";
        		    if (Set.of("FV", "PR", "FZL", "FVK", "RWS", "PRK", "FZLK", "FVU", "FVM", "FVG", "FH", "UMUZ","FHK").contains(type)) {
        		        String nr = firstNonBlank(
        		                doc.getInvoiceShortNumber(),
        		                doc.getInvoiceNumber()
        		        );

        		        if (nr != null) {
        		            out.add(new ExtractedNumber(nr, doc.getId()));
        		        }

        		        return out;
        		    }
        		    	if ("DK".equals(type) || "RD".equals(type)) {
        		    	    addIfNotBlank(out, doc.getInvoiceShortNumber(), doc.getSourceFileName());
        		    	    addIfNotBlank(out, doc.getInvoiceNumber(), doc.getSourceFileName());
        		    	    if (doc.getPositions() != null) {
        		    	        for (DmsPosition pos : doc.getPositions()) {
        		    	            if (Set.of("DS", "PR").contains(pos.getKlasyfikacja().toUpperCase())) {
        		    	                addIfNotBlank(out, pos.getNumer(), doc.getSourceFileName());
        		    	            }
        		    	        }
        		    	    }
        		    	    return out;
        		    	}
        		    String nr = firstNonBlank(
        		            doc.getInvoiceShortNumber(),
        		            doc.getInvoiceNumber()
        		    );

        		    if (nr != null) {
        		        out.add(new ExtractedNumber(nr, doc.getId()));
        		    }

        		    return out;
        		})
                .build();
        Function<DmsParsedDocument, DocumentRelationAnalyzer.DocumentMeta> metaProvider = d -> {
            String type = d.getDocumentType();

            if (type == null || type.isBlank()) {
                type = "UNKNOWN";
            }

            return new DocumentRelationAnalyzer.DocumentMeta(
                    type,
                    d.getSourceFileName(),
                    d.getId(),
                    d.getTransId()
            );
        };
        analyzer.indexDocuments(allParsedDocs, metaProvider);
        return analyzer.generateHtmlTable();
    }
    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }
    private void addIfNotBlank(List<ExtractedNumber> out, String nr, String id) {
        if (nr != null && !nr.isBlank()) {
            out.add(new ExtractedNumber(nr.trim(), id));
        }
    }
    private static boolean isDateInRange(LocalDate docDate, LocalDate from, LocalDate to) {
        if (docDate == null) return false; // brak daty -> pomijamy; zmień jeśli chcesz inaczej
        if (from != null && docDate.isBefore(from)) return false;
        if (to   != null && docDate.isAfter(to))   return false;
        return true;
    }

    private static LocalDate parseDateFromPossibleFormats(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        // najpierw yyyy-MM-dd
        try { return LocalDate.parse(s); } catch (DateTimeParseException ignored) {}
        // spróbuj z czasem: "yyyy-MM-dd HH:mm:ss" lub "yyyy-MM-dd'T'HH:mm:ss"
        try {
            String datePart = s.split(" ")[0];
            return LocalDate.parse(datePart);
        } catch (Exception ignored) {}
        return null;
    }
}
