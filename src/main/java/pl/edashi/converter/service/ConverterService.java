package pl.edashi.converter.service;
import pl.edashi.common.logging.AppLogger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import pl.edashi.converter.model.*;
import pl.edashi.converter.repository.DocumentRepository;
import pl.edashi.dms.model.DmsParsedContractorList;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.parser.DmsParserDK;
import pl.edashi.dms.parser.DmsParserDS;
import pl.edashi.dms.parser.DmsParserKO;
import pl.edashi.dms.parser.DmsParserKZ;
import pl.edashi.dms.parser.DmsParserSL;
import pl.edashi.dms.parser.DmsParserWZ;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.StringReader;
import java.io.StringWriter;

import org.xml.sax.InputSource;

public class ConverterService {

    private final DocumentRepository repository;
    private final XmlStructureComparator structureComparator;
    private final AppLogger log = new AppLogger("CONVERTER");

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
        Element root = doc.getDocumentElement(); // <DMS ...>
        String type = root.getAttribute("id");
        switch (type) {
        case "DS":
            return new DmsParserDS().parse(doc, sourceFile);

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
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }
}
