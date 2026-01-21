package pl.edashi.converter.service;
import pl.edashi.common.logging.AppLogger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import pl.edashi.converter.repository.DocumentRepository;
import pl.edashi.dms.model.DmsParsedContractorList;
import pl.edashi.dms.parser.DmsParserDK;
import pl.edashi.dms.parser.DmsParserDS;
import pl.edashi.dms.parser.DmsParserDZ;
import pl.edashi.dms.parser.DmsParserKO;
import pl.edashi.dms.parser.DmsParserKZ;
import pl.edashi.dms.parser.DmsParserSL;
import pl.edashi.dms.parser.DmsParserWZ;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import org.xml.sax.InputSource;
import pl.edashi.converter.analysis.DocumentRelationAnalyzer;
import pl.edashi.converter.analysis.DocumentRelationAnalyzer.ExtractedNumber;
import pl.edashi.converter.analysis.DocumentRelationAnalyzer.DocumentMeta;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.model.DmsPosition;

import java.util.ArrayList; 
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class ConverterService {

    private final DocumentRepository repository;
    private final XmlStructureComparator structureComparator;
    private final AppLogger log = new AppLogger("ConverterService");

    public ConverterService(DocumentRepository repository) {
        this.repository = repository;
        this.structureComparator = new XmlStructureComparator();
    }
    /**
     * Obsługa dokumentów DS, KO, DK, KZ, WZ
     */
    public Object processSingleDocument(String xml, String sourceFile) throws Exception {
        // 1. parsowanie
        Document doc = load(xml);
        if (doc == null) {
        	IllegalArgumentException iae = new IllegalArgumentException("Niepoprawny XML"); 
        	log.error("Nie udało się sparsować XML: " + sourceFile, iae); 
        	throw iae;
        }
        Element root = doc.getDocumentElement(); // <DMS ...>
        if (root == null) throw new IllegalArgumentException("Brak elementu root w pliku: " + sourceFile);
        String type = root.getAttribute("id");

        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("Brak atrybutu id w DMS root: " + sourceFile);
        }
        switch (type) {
        case "DS":
            //log.info("case DS");
            return new DmsParserDS().parse(doc, sourceFile);
        case "DZ":
        	//log.info("case DZ");
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

        default:
            throw new IllegalArgumentException("Nieobsługiwany typ dokumentu: " + type);
    }
    }
    /**
     * NOWA METODA — obsługa słownika kontrahentów SL
     */
    public DmsParsedContractorList processContractorDictionary(String xml, String sourceFile) throws Exception {

        Document doc = load(xml);

        Element root = doc.getDocumentElement();
        String type = root.getAttribute("id");

        if (!"SL".equals(type)) {
            throw new IllegalArgumentException("processContractorDictionary obsługuje tylko typ SL, otrzymano: " + type);
        }

        return new DmsParserSL().parse(doc, sourceFile);
    }


    // pomocnicza metoda do tworzenia elementów
    private Element makeEl(Document doc, String name, String value) {
        Element el = doc.createElement(name);
        el.setTextContent(value);
        return el;
    }
    // ============================
    // HELPERS
    // ============================

    private Document load(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
       } 
    /**
     * Indeksuje wszystkie sparsowane dokumenty i zwraca tabelę HTML z powiązaniami.
     */
    
    public String analyzeRelationsAndGenerateHtml(List<DmsParsedDocument> allParsedDocs) {
    	//allParsedDocs.forEach(d ->System.out.println("ANALYZER INPUT: " + d.getSourceFileName() + " type=" + d.getDocumentType()));
        DocumentRelationAnalyzer analyzer = DocumentRelationAnalyzer.builder()
                // normalizacja: usuń spacje, myślniki, uppercase
        		
        		.withNormalizer(s -> {
        			
        		    if (s == null) return "";
        		    return s.trim()
        		            .toUpperCase()
        		            .replaceAll("[\\s\\-\\\\]+", "") // usuń spacje, myślniki, backslash
        		            .replaceAll("/{2,}", "/");       // znormalizuj podwójne slashe
        		})

                // rejestrujemy ekstraktor, który bierze:
                // - invoiceNumber (np. z DS)
                // - invoiceShortNumber (jeśli używasz)
                // - dodatkowe relatedNumbers (np. z DK -> pozycje 49)
        		
        		.registerExtractor(doc -> {
        		    List<ExtractedNumber> out = new ArrayList<>();

        		    String type = doc.getDocumentType() != null
        		            ? doc.getDocumentType().toUpperCase()
        		            : "";


        		    //
        		    // ============================
        		    // 1) DS (FV, PR, FZL, FVK, RWS)
        		    // ============================
        		    //
        		    if (Set.of("FV", "PR", "FZL", "FVK", "RWS", "PRK", "FZLK", "FVU", "FVM", "FVG").contains(type)) {
        		    	//type = "DS";
        		    	//System.out.println("EXTRACTOR DS: type=" + type + " file=" + doc.getSourceFileName()
        		        //+ " nr=" + doc.getInvoiceShortNumber());

        		        String nr = firstNonBlank(
        		                doc.getInvoiceShortNumber(),
        		                doc.getInvoiceNumber()
        		        );

        		        if (nr != null) {
        		            out.add(new ExtractedNumber(nr, doc.getId()));
        		        }

        		        return out;
        		    }

        		    //
        		    // ============================
        		    // 2) DK — numer DK + numery DS z pozycji
        		    // ============================
        		    	if ("DK".equals(type)) {
        		    		//type = "DK";
        		    	    // 2.1 numer DK (nagłówek)
        		    	    /*log.info(String.format(
        		    	            "[DK] START file=%s invoiceShort='%s' invoice='%s' positions='%s'",
        		    	            doc.getSourceFileName(),
        		    	            doc.getInvoiceShortNumber(),
        		    	            doc.getInvoiceNumber(),
        		    	            doc.getPositions().size()
        		    	    ));*/
        		    	    addIfNotBlank(out, doc.getInvoiceShortNumber(), doc.getSourceFileName());
        		    	    addIfNotBlank(out, doc.getInvoiceNumber(), doc.getSourceFileName());
        		    	   /* log.info(String.format(
        		    	            "[DK] HEADER ADDED file=%s short='%s' full='%s'",
        		    	            doc.getSourceFileName(),
        		    	            doc.getInvoiceShortNumber(),
        		    	            doc.getInvoiceNumber()
        		    	    ));*/
        		    	    // 2.2 numery DS z pozycji DK (typ=49)
        		    	    if (doc.getPositions() != null) {
        		    	        for (DmsPosition pos : doc.getPositions()) {
        		    	           /* log.info(String.format(
        		    	                    "[DK] POS CHECK file=%s klasyf='%s' numer='%s'",
        		    	                    doc.getSourceFileName(),
        		    	                    pos.getKlasyfikacja(),
        		    	                    pos.getNumer()
        		    	            ));*/
        		    	            if (Set.of("DS", "PR").contains(pos.getKlasyfikacja().toUpperCase())) {
        		    	                addIfNotBlank(out, pos.getNumer(), doc.getSourceFileName());
        		    	               /* log.info(String.format(
        		    	                        "[DK] POS ADDED (DS) file=%s numer='%s'",
        		    	                        doc.getSourceFileName(),
        		    	                        pos.getNumer()
        		    	                ));*/
        		    	            }
        		    	        }
        		    	    }
        		    	   /* log.info(String.format(
        		    	            "[DK] END file=%s extractedCount=%d",
        		    	            doc.getSourceFileName(),
        		    	            out.size()
        		    	    ));*/
        		    	    return out;
        		    	}

        		    //
        		    // ============================
        		    // 3) INNE TYPY — tylko numer własny
        		    // ============================
        		    //
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

        // metaProvider: jak pobrać typ i nazwę pliku z obiektu dokumentu
        Function<DmsParsedDocument, DocumentRelationAnalyzer.DocumentMeta> metaProvider = d -> {
            String type = d.getDocumentType();

            if (type == null || type.isBlank()) {
                type = "UNKNOWN";
            }

            return new DocumentRelationAnalyzer.DocumentMeta(
                    type,
                    d.getSourceFileName(),
                    d.getId()
            );
        };
                /*allParsedDocs.forEach(d ->
                log.info(String.format("META CHECK: file='%s', type='%s'", d.getSourceFileName(),
                                 d.getDocumentType()))
            );*/

        analyzer.indexDocuments(allParsedDocs, metaProvider);

        // zwróć HTML
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


}
