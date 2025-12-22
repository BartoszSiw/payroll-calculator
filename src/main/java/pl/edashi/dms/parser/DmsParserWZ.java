package pl.edashi.dms.parser;

import org.w3c.dom.*;
import pl.edashi.dms.model.*;

import java.util.ArrayList;

public class DmsParserWZ {

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

        out.metadata = new DocumentMetadata(
                genDocId,
                id,
                trans,
                fileName,
                daty != null ? daty.getAttribute("data") : "",
                daty != null ? daty.getAttribute("data") : "",
                daty != null ? daty.getAttribute("data_zatw") : ""
        );

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

            // zapisujemy do notes
            out.notes = new ArrayList<>();
            if (!operatorName.isEmpty()) {
                out.notes.add("Operator: " + operatorName);
            }
            if (!nettoZakup.isEmpty()) {
                out.notes.add("Wartość netto zakupu: " + nettoZakup);
            }

            // dodatkowy opis
            out.additionalDescription = "WZ " + fullNumber;
        }

        // ============================
        // 3. PUSTE POLA (WZ ich nie ma)
        // ============================
        out.contractor = null;
        out.positions = new ArrayList<>();
        out.payments = new ArrayList<>();

        out.vatRate = "";
        out.vatBase = "";
        out.vatAmount = "";

        out.fiscalNumber = "";
        out.fiscalDevice = "";
        out.fiscalDate = "";

        return out;
    }
}

