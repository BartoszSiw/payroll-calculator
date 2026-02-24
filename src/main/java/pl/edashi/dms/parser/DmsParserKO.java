package pl.edashi.dms.parser;

import org.w3c.dom.*;

import pl.edashi.common.logging.AppLogger;
import pl.edashi.dms.model.*;
import pl.edashi.dms.parser.util.DocumentNumberExtractor;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.List;

public class DmsParserKO implements DmsParser{
	private final AppLogger log = new AppLogger("Parser KO");
    public DmsParsedDocument parse(Document doc, String fileName) {

        DmsParsedDocument out = new DmsParsedDocument();
        out.setDocumentType("KO");
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
            //String normalized = DocumentNumberExtractor.normalizeNumber(rawText); 
            out.setReportNumber(rawText);
            out.setNrRep(attrNr);
            //log.info("KO DEBUG: <numer> found rawText='" + rawText + "' nrAttr='" + attrNr+ "' rokAttr='" + attrRok + "' kod_dok='" + attrKodDok + "' file=" + fileName);
        }
        // KO ma tylko jedną sekcję <daty>
        Element daty = (Element) doc.getElementsByTagName("daty").item(0);
        Element warto = (Element) doc.getElementsByTagName("wartosci").item(0);
        //boolean found = DocumentNumberExtractor.extractFromGenInfo(root, out, fileName,hasNumberInDane);
        try {
            NodeList docList = doc.getElementsByTagName("document");
            for (int i = 0; i < docList.getLength(); i++) {
                Element docEl = (Element) docList.item(i);
                if ("02".equals(docEl.getAttribute("typ"))) {
                    Element daneEl = firstElementByTag(docEl, "dane");
                    if (daneEl != null && daneEl.hasAttribute("kasa")) {
                        String kasa = daneEl.getAttribute("kasa").trim();
                        String oddzial = daneEl.getAttribute("oddzial").trim();
                        //log.info(String.format("Parser KO kasa='%s ' oddzial='%s '  ", kasa, oddzial));
                        out.setDaneRejestr(kasa); // upewnij się, że DmsParsedDocument ma setter
                    	out.setOddzial(oddzial);}
                    break; // zwykle tylko jeden rekord 02
                }
            }
        } catch (Exception ex) {
            log.warn("Parser KO: nie udało się odczytać kasa: " + ex.getMessage());
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
        // 2. DANE KO (typ 02)
        // ============================
        NodeList list = doc.getElementsByTagName("document");

        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("02".equals(el.getAttribute("typ"))) {

                Element dane = (Element) el.getElementsByTagName("dane").item(0);

                // numer dokumentu
                numer = (Element) dane.getElementsByTagName("numer").item(0);
                String fullNumber = numer.getTextContent();
                String nr = numer.getAttribute("nr");
                String rok = numer.getAttribute("rok");

                // daty
                Element datyEl = (Element) dane.getElementsByTagName("daty").item(0);
                String date = datyEl != null ? datyEl.getAttribute("data") : "";

                // kwota
                Element wartosci = (Element) dane.getElementsByTagName("wartosci").item(0);
                String kwota = wartosci != null ? wartosci.getAttribute("kwota") : "";
                String waluta = wartosci != null ? wartosci.getAttribute("waluta") : "";

                // operator
                Element operator = (Element) dane.getElementsByTagName("operatorzy").item(0);
                String operatorName = operator != null ? operator.getAttribute("nazwa") : "";
                String operatorCode = operator != null ? operator.getAttribute("kod_pracownika") : "";

                // ZAPISUJEMY DO POL DMS (przez settery)
                out.setAdditionalDescription("KO Description" + fullNumber);
                out.setDocumentType("KO");
                //out.setInvoiceNumber("KO InvoiceNumber" + fullNumber); // jeśli chcesz trzymać w invoiceNumber
                //out.setNumer(fullNumber);

                List<String> notes = new ArrayList<>();
                if (!operatorName.isEmpty()) {
                    notes.add("Operator: " + operatorName + " (" + operatorCode + ")");
                }
                if (!kwota.isEmpty()) {
                    notes.add("Kwota: " + kwota + " " + waluta);
                }
                out.setNotes(notes);


                break;
            }
        }

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
