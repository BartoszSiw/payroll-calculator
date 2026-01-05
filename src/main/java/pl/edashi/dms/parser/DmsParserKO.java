package pl.edashi.dms.parser;

import org.w3c.dom.*;
import pl.edashi.dms.model.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.List;

public class DmsParserKO {

    public DmsParsedDocument parse(Document doc, String fileName) {

        DmsParsedDocument out = new DmsParsedDocument();

        // ============================
        // 1. METADATA
        // ============================
        Element root = doc.getDocumentElement();

        String genDocId = root.getAttribute("gen_doc_id");
        String id = root.getAttribute("id");
        String trans = root.getAttribute("trans");

        // KO ma tylko jedną sekcję <daty>
        Element daty = (Element) doc.getElementsByTagName("daty").item(0);

        out.setMetadata(new DocumentMetadata(
                genDocId,
                id,
                trans,
                fileName,
                daty.getAttribute("data"),
                daty.getAttribute("data_sprzed"),
                daty.getAttribute("data_zatw")
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
                Element numer = (Element) dane.getElementsByTagName("numer").item(0);
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
                out.setAdditionalDescription("KO " + fullNumber);
                out.setDocumentType("KO");
                out.setInvoiceNumber("KO " + fullNumber); // jeśli chcesz trzymać w invoiceNumber
                //out.setNumer(fullNumber);

                // KO nie ma pozycji, VAT, płatności, kontrahenta
                out.setPositions(new ArrayList<>()); 
                out.setPayments(new ArrayList<>());

                List<String> notes = new ArrayList<>();
                if (!operatorName.isEmpty()) {
                    notes.add("Operator: " + operatorName + " (" + operatorCode + ")");
                }
                if (!kwota.isEmpty()) {
                    notes.add("Kwota: " + kwota + " " + waluta);
                }
                out.setNotes(notes);

                // VAT pola
                out.setVatRate("");
                out.setVatBase("");
                out.setVatAmount("");

                break;
            }
        }

        return out;
    }
}
