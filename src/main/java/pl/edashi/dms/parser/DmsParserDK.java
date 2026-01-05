package pl.edashi.dms.parser;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.model.DocumentMetadata;
import pl.edashi.dms.parser.util.DocumentNumberExtractor;
import pl.edashi.dms.model.Contractor;
import pl.edashi.dms.model.DmsPayment;
import pl.edashi.dms.model.DmsPosition;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
/**
 * Parser DK.xml
 * - wyciąga główny numer dokumentu (z elementu <numer> w <dane>)
 * - wyciąga gen_doc_id z atrybutu DMS (jeśli obecny)
 * - wyciąga powiązane numery z zagnieżdżonych dokumentów (np. pozycje dowodu kasowego -> numer wewnętrzny DS)
 * - wyciąga kwotę z <wartosci kwota="..."> jeśli występuje
 */
public class DmsParserDK {
	private final AppLogger log = new AppLogger("DmsParserDK");
	public DmsParsedDocument parse(Document doc, String fileName) {
    //public DmsParsedDocument parse(InputStream xmlStream, String sourceFileName) throws Exception {
        if (doc == null) throw new IllegalArgumentException("Document is null");
        Element root = doc.getDocumentElement();

        DmsParsedDocument out = new DmsParsedDocument();
        out.setDocumentType("DK");
        out.setSourceFileName(fileName);

        boolean found = DocumentNumberExtractor.extractForParserType(root, out, "DK", fileName);

        if (!found) {
            // fallback: jeśli nie znaleziono, spróbuj z get_info
        	DocumentNumberExtractor.extractFromGenInfo(root, out, fileName);
        }

        // ============================
        // 1. METADATA
        // ============================
        String genDocId = safeAttr(root, "gen_doc_id");
        String id = safeAttr(root, "id");
        String trans = safeAttr(root, "trans");
        if (!isEmpty(genDocId)) out.setId(genDocId);
        else if (!isEmpty(id)) out.setId(id);
        Element daty = firstElementByTag(doc, "daty");
        String data = daty != null ? safeAttr(daty, "data") : "";

        DocumentMetadata metadata = new DocumentMetadata(
                !isEmpty(genDocId) ? genDocId : "",
                !isEmpty(id) ? id : "",
                !isEmpty(trans) ? trans : "",
                fileName != null ? fileName : "",
                data,
                data,
                data
        );
        out.setMetadata(metadata);
        // ============================
        // 2. KONTRAHENT (typ 35)
        // ============================
        Contractor contractor = extractContractor(doc);
        out.setContractor(contractor);

        // ============================
        // 3. PŁATNOŚCI (typ 48 + 49)
        // ============================
        List<DmsPayment> payments = extractPayments(doc);
        out.setPayments(payments);

        // ============================
        // 4. UWAGI (operator, opis pozycji)
        // ============================
        List<String> notes = extractNotes(doc);
        out.setNotes(notes);

        // ============================
        // 5. DODATKOWY OPIS
        // ============================
        //String mainNumber = extractMainNumber(doc);
        //out.setAdditionalDescription("DK " + (mainNumber != null ? mainNumber : ""));
        // ============================
        // 6. POZYCJE (typ 49)
        // ============================
        extractPositions(doc, out);
        // ============================
        // 7. PUSTE POLA (DK ich nie ma)
        // ============================
        out.setPositions(new ArrayList<>()); // puste
        out.setVatRate("");
        out.setVatBase("");
        out.setVatAmount("");
        out.setFiscalNumber("");
        out.setFiscalDevice("");
        out.setFiscalDate("");

        return out;
    }
    public DmsParsedDocument parse(InputStream xmlStream, String fileName) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(xmlStream);
        return parse(doc, fileName);
    }

    // ------------------------------
    // KONTRAHENT (typ 35)
    // ------------------------------
    private Contractor extractContractor(Document doc) {

        NodeList list = doc.getElementsByTagName("document");

        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("35".equals(el.getAttribute("typ"))) {

                Element dane = firstElementByTag(el, "dane");
                if (dane == null) continue;
                Element rozs = firstElementByTag(dane, "rozszerzone");
                if (rozs == null) continue;

                Contractor c = new Contractor();
                c.setId(safeAttr(rozs, "kod_klienta"));
                c.setNip(safeAttr(rozs, "nip"));
                c.setName1(safeAttr(rozs, "nazwa1"));
                c.setName2(safeAttr(rozs, "nazwa2"));
                c.setName3(safeAttr(rozs, "nazwa3"));
                c.setStreet(safeAttr(rozs, "ulica"));
                c.setZip(safeAttr(rozs, "kod_poczta"));
                c.setCity(safeAttr(rozs, "miejscowosc"));
                c.setCountry(safeAttr(rozs, "kod_kraju"));

                return c;
            }
        }
        return null;
    }

    // ------------------------------
    // PŁATNOŚCI (typ 48 + 49)
    // ------------------------------
    private List<DmsPayment> extractPayments(Document doc) {

        List<DmsPayment> listOut = new ArrayList<>();
        NodeList list = doc.getElementsByTagName("document");

        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("48".equals(el.getAttribute("typ"))) {

                Element dane48 = firstElementByTag(el, "dane");
                if (dane48 == null) continue;

                // kwota
                Element wart = firstElementByTag(dane48, "wartosci");
                String kwota = wart != null ? safeAttr(wart, "kwota") : "";

                // data zatwierdzenia
                Element daty = firstElementByTag(dane48, "daty");
                String date = daty != null ? safeAttr(daty, "data_zatw") : "";

                // forma płatności (kod)
                Element klasyf = firstElementByTag(dane48, "klasyfikatory");
                String forma = klasyf != null ? safeAttr(klasyf, "kod") : "";

                // operator
                Element operator = firstElementByTag(dane48, "operatorzy");
                String operatorName = operator != null ? safeAttr(operator, "nazwa") : "";

                // pozycje (typ 49) - opis/relacje
                String opis = extractPaymentDescription(dane48);

                DmsPayment p = new DmsPayment();
                p.setKwota(kwota);
                p.setTermin(date);
                p.setForma(forma);
                p.setKierunek("przychód");
                p.setOpis(opis);
                p.setOperatorName(operatorName);

                listOut.add(p);
            }
        }

        return listOut;
    }


    private String extractPaymentDescription(Element dane48) {
        NodeList docs = dane48.getElementsByTagName("document");

        for (int i = 0; i < docs.getLength(); i++) {
            Element el = (Element) docs.item(i);

            if ("49".equals(el.getAttribute("typ"))) {
                Element dane49 = firstElementByTag(el, "dane");
                if (dane49 == null) continue;
                Element rozs = firstElementByTag(dane49, "rozszerzone");
                
                if (rozs != null && rozs.hasAttribute("opis1")) {
                	return safeAttr(rozs, "opis1");
                }
                // jeśli numer jest bezpośrednio w <numer> wewnątrz 49
                Element numer = firstElementByTag(dane49, "numer");
                if (numer != null) {
                    String txt = numer.getTextContent();
                    if (txt != null && !txt.trim().isEmpty()) return txt.trim();
                    String attrNr = safeAttr(numer, "nr");
                    if (attrNr != null && !attrNr.isEmpty()) return attrNr;
                }
            }
        }
        return "";
    }

    // ------------------------------
    // UWAGI (operator + opis pozycji)
    // ------------------------------
    private List<String> extractNotes(Document doc) {

        List<String> notes = new ArrayList<>();

        // operator z typ 02 (pierwsze dane)
        Element dane02 = firstElementByTag(doc, "dane");
        if (dane02 != null) {
            Element operator = firstElementByTag(dane02, "operatorzy");
            if (operator != null) {
                String name = safeAttr(operator, "nazwa");
                if (name != null && !name.isEmpty()) notes.add("Operator: " + name);
            }
        }
        return notes;
    }
    private void extractPositions(Document xml, DmsParsedDocument out){
    	NodeList docs = xml.getElementsByTagName("document");
    	//System.out.println(">>> SZUKAM document typ=49");
log.info("1>>> ParserDK: file='%s'" + out.getSourceFileName());
    	for (int i = 0; i < docs.getLength(); i++) {
    	    Element el = (Element) docs.item(i);
    	    //System.out.println(">>> ZNALAZŁEM document typ=" + el.getAttribute("typ"));
    	    if (!"49".equals(el.getAttribute("typ"))) continue;

    	    //System.out.println(">>> TO JEST TYP 49");
    	    

    	    Element dane49 = firstElementByTag(el, "dane");
    	    if (dane49 == null) continue;

    	    // klasyfikacja
    	    String klasyf = null;
    	    Element kl = firstElementByTag(dane49, "klasyfikatory");
    	    if (kl != null) {
    	        klasyf = safeAttr(kl, "klasyfikacja");
    	    }

    	    // numer DS
    	    String numer = null;
    	    Element numEl = firstElementByTag(dane49, "numer");
    	    if (numEl != null) {
    	        numer = numEl.getTextContent().trim();
    	    }
    	    log.info("2>>> ParserDK: klasyf='%s '" + klasyf);
    	    log.info("3>>> ParserDK:  numer='%s '" + numer);
    	    // zapisujemy tylko DS
    	    if ("DS".equalsIgnoreCase(klasyf) && numer != null && !numer.isBlank()) {
    	    	DmsPosition pos = new DmsPosition();
    	    	pos.setKlasyfikacja(klasyf);
    	    	pos.setNumer(numer);
    	    	out.getPositions().add(pos);

    	    }
    	}

  
    }

    // ------------------------------
    // Pomocnicze metody
    // ------------------------------
    private static Element firstElementByTag(Node parent, String tagName) {
        if (parent == null) return null;
        NodeList list = null;
        if (parent instanceof Document) {
            list = ((Document) parent).getElementsByTagName(tagName);
        } else if (parent instanceof Element) {
            list = ((Element) parent).getElementsByTagName(tagName);
        }
        if (list == null || list.getLength() == 0) return null;
        return (Element) list.item(0);
    }

    private static String safeAttr(Element el, String name) {
        if (el == null) return "";
        String v = el.getAttribute(name);
        return v != null ? v : "";
    }
    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Pomocnicza metoda do serializacji Document -> String (jeśli potrzebujesz)
     */
    private static String documentToString(Document doc) throws TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}

