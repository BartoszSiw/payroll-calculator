package pl.edashi.dms.parser;

import org.w3c.dom.*;

import pl.edashi.common.logging.AppLogger;
import pl.edashi.dms.model.*;
import pl.edashi.dms.parser.util.DocumentNumberExtractor;

import java.util.ArrayList;

public class DmsParserKZ {
	private final AppLogger log = new AppLogger("Parser KZ");
    public DmsParsedDocument parse(Document doc, String fileName) {

        DmsParsedDocument out = new DmsParsedDocument();
        out.setDocumentType("KZ");
        out.setSourceFileName(fileName);
        // ============================
        // 1. METADATA
        // ============================
        Element root = doc.getDocumentElement();
        Element document = (Element) doc.getElementsByTagName("document").item(0);
        Element numer = (Element) document.getElementsByTagName("numer").item(0);
        String genDocId = root.getAttribute("gen_doc_id");
        String id = root.getAttribute("id");
        String trans = root.getAttribute("trans");
        if (numer == null) {
            //log.info("KO DEBUG: element <numer> NOT FOUND in document element for file=" + fileName);
        } else {
            String rawText = numer.getTextContent();
            String attrNr = numer.getAttribute("nr");
            String attrRok = numer.getAttribute("rok");
            String attrKodDok = numer.getAttribute("kod_dok");
            String normalized = DocumentNumberExtractor.normalizeNumber(rawText); 
            out.setReportNumber(normalized);
            //log.info("KO DEBUG: <numer> found rawText='" + rawText + "' nrAttr='" + attrNr+ "' rokAttr='" + attrRok + "' kod_dok='" + attrKodDok + "' file=" + fileName);
        }
        Element daty = (Element) doc.getElementsByTagName("daty").item(0);
        Element warto = (Element) doc.getElementsByTagName("wartosci").item(0);
        try {
            NodeList docList = doc.getElementsByTagName("document");
            for (int i = 0; i < docList.getLength(); i++) {
                Element docEl = (Element) docList.item(i);
                if ("02".equals(docEl.getAttribute("typ"))) {
                    Element daneEl = firstElementByTag(docEl, "dane");
                    if (daneEl != null && daneEl.hasAttribute("kasa")) {
                        String kasa = daneEl.getAttribute("kasa").trim();
                        String oddzial = daneEl.getAttribute("oddzial").trim();
                        log.info(String.format("Parser KZ kasa='%s ' oddzial='%s '  ", kasa, oddzial));
                        out.setDaneRejestr(kasa); // upewnij się, że DmsParsedDocument ma setter
                    	out.setOddzial(oddzial);}
                    break; // zwykle tylko jeden rekord 02
                }
            }
        } catch (Exception ex) {
            log.warn("Parser KZ: nie udało się odczytać kasa: " + ex.getMessage());
        }
        out.setMetadata(new DocumentMetadata(
                genDocId,
                id,
                trans,
                fileName,
                daty.getAttribute("data"),
                daty.getAttribute("data_sprzed"),
                daty.getAttribute("data_zatw"),
                daty.getAttribute("data"),
                warto.getAttribute("waluta")
        ));

        // ============================
        // 2. DANE KZ (typ 02)
        // ============================
        Element dane02 = (Element) doc.getElementsByTagName("dane").item(0);

        if (dane02 != null) {

            // numer dokumentu
            //Element numer = (Element) dane02.getElementsByTagName("numer").item(0);
            //String fullNumber = numer != null ? numer.getTextContent() : "";

            // operator
            Element operator = (Element) dane02.getElementsByTagName("operatorzy").item(0);
            String operatorName = operator != null ? operator.getAttribute("nazwa") : "";

            // kwota
            Element wartosci = (Element) dane02.getElementsByTagName("wartosci").item(0);
            String kwota = wartosci != null ? wartosci.getAttribute("kwota") : "";
            String waluta = wartosci != null ? wartosci.getAttribute("waluta") : "";

            // zapisujemy do notes
            out.setNotes(new ArrayList<>()); 
            if (!operatorName.isEmpty()) {
                out.getNotes().add("Operator: " + operatorName);
            }
            if (!kwota.isEmpty()) {
                out.getNotes().add("Kwota: " + kwota + " " + waluta);
            }

            // dodatkowy opis
            //out.setAdditionalDescription("KZ " + fullNumber);
            //out.setNumer(fullNumber);
            //out.setInvoiceNumber("KZ " + fullNumber); // jeśli chcesz trzymać w invoiceNumber
            out.setDocumentType("KZ");
        }

        // ============================
        // 3. PUSTE POLA (KZ ich nie ma)
        // ============================
        out.setContractor(null);
        out.setPositions(new ArrayList<DmsPosition>());

        out.setPayments(new ArrayList<>());

        out.setVatRate("");
        out.setVatBase("");
        out.setVatAmount("");

        out.setFiscalNumber("");
        out.setFiscalDevice("");
        out.setFiscalDate("");

        return out;
    }
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
}
