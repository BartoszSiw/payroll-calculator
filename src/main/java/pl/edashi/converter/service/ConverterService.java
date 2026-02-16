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
import pl.edashi.dms.parser.DmsParserRD;
import pl.edashi.dms.parser.DmsParserRO;
import pl.edashi.dms.parser.DmsParserRZ;
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
        // Sprawdź, czy parser dla tego typu jest włączony
        ParserRegistry registry = ParserRegistry.getInstance();
        String docType = type.trim().toUpperCase();
        if (!registry.isEnabled(docType)) {
            log.info(String.format("Pominięto plik %s: typ '%s' nie jest włączony do przetwarzania", sourceFile, docType));
            return new SkippedDocument(docType, "Parser disabled");
        }
        switch (type) {
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
    }
    }
    public DmsParsedContractorList processContractorDictionary(String xml, String sourceFile) throws Exception {
    	
        Document doc = load(xml);
        
        Element root = doc.getDocumentElement();
        String type = root.getAttribute("id");

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
        		    if (Set.of("FV", "PR", "FZL", "FVK", "RWS", "PRK", "FZLK", "FVU", "FVM", "FVG").contains(type)) {
        		        String nr = firstNonBlank(
        		                doc.getInvoiceShortNumber(),
        		                doc.getInvoiceNumber()
        		        );

        		        if (nr != null) {
        		            out.add(new ExtractedNumber(nr, doc.getId()));
        		        }

        		        return out;
        		    }
        		    	if ("DK".equals(type)) {
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


}
