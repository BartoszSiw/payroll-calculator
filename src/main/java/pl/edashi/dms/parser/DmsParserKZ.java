package pl.edashi.dms.parser;

import org.w3c.dom.*;
import pl.edashi.dms.model.*;

import java.util.ArrayList;

public class DmsParserKZ {

    public DmsParsedDocument parse(Document doc, String fileName) {

        DmsParsedDocument out = new DmsParsedDocument();

        // ============================
        // 1. METADATA
        // ============================
        Element root = doc.getDocumentElement();

        String genDocId = root.getAttribute("gen_doc_id");
        String id = root.getAttribute("id");
        String trans = root.getAttribute("trans");

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
        // 2. DANE KZ (typ 02)
        // ============================
        Element dane02 = (Element) doc.getElementsByTagName("dane").item(0);

        if (dane02 != null) {

            // numer dokumentu
            Element numer = (Element) dane02.getElementsByTagName("numer").item(0);
            String fullNumber = numer != null ? numer.getTextContent() : "";

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
            out.setAdditionalDescription("KZ " + fullNumber);
            //out.setNumer(fullNumber);
            out.setInvoiceNumber("KZ " + fullNumber); // jeśli chcesz trzymać w invoiceNumber
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
}
