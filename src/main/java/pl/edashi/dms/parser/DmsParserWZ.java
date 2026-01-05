package pl.edashi.dms.parser;

import org.w3c.dom.*;
import pl.edashi.dms.model.*;

import java.util.ArrayList;
import java.util.List;

public class DmsParserWZ {

    public DmsParsedDocument parse(Document doc, String fileName) {

        DmsParsedDocument out = new DmsParsedDocument();

        // ============================
        // 1. METADATA
        // ============================
        Element root = doc.getDocumentElement();

        String genDocId = root != null ? root.getAttribute("gen_doc_id") : "";
        String id = root != null ? root.getAttribute("id") : "";
        String trans = root != null ? root.getAttribute("trans") : "";

        Element daty = (Element) doc.getElementsByTagName("daty").item(0);
        String data = daty != null ? daty.getAttribute("data") : "";
        String dataZatw = daty != null ? daty.getAttribute("data_zatw") : "";
        DocumentMetadata meta = new DocumentMetadata(
                genDocId,
                id,
                trans,
                fileName,
                data,
                data,
                dataZatw
        );
        out.setMetadata(meta);
        out.setSourceFileName(fileName);
        out.setId(id);
        // ============================
        // 2. DANE WZ (typ 02)
        // ============================
        Element dane02 = (Element) doc.getElementsByTagName("dane").item(0);

        if (dane02 != null) {

            // numer dokumentu
            Element numer = (Element) dane02.getElementsByTagName("numer").item(0);
            String fullNumber = numer != null ? numer.getTextContent() : "";

            // operator
            Element operator = (Element) dane02.getElementsByTagName("operatorzy").item(0);
            String operatorName = operator != null ? operator.getAttribute("nazwa") : "";

            // kwota netto zakupu
            Element wartosci = (Element) dane02.getElementsByTagName("wartosci").item(0);
            String nettoZakup = wartosci != null ? wartosci.getAttribute("netto_zakup") : "";

         // przygotuj notes
            List<String> notes = new ArrayList<>();
            if (!operatorName.isEmpty()) {
                notes.add("Operator: " + operatorName);
            }
            if (!nettoZakup.isEmpty()) {
                notes.add("Wartość netto zakupu: " + nettoZakup);
            }
            out.setNotes(notes);

            // dodatkowy opis i pola dokumentu
            out.setAdditionalDescription("WZ " + fullNumber);
            out.setDocumentType("WZ");
            out.setInvoiceNumber("WZ " + fullNumber);
            //out.setNumer(fullNumber);

            // puste listy / brak kontrahenta
            out.setContractor(null);
            out.setPositions(new ArrayList<>());
            out.setPayments(new ArrayList<>());

            // VAT / fiskalne pola
            out.setVatRate("");
            out.setVatBase("");
            out.setVatAmount("");

            out.setFiscalNumber("");
            out.setFiscalDevice("");
            out.setFiscalDate("");
        } else {
            // jeśli brak sekcji dane, ustaw minimalne wartości
            out.setPositions(new ArrayList<>());
            out.setPayments(new ArrayList<>());
            out.setNotes(new ArrayList<>());
        }
        return out;
    }
}

