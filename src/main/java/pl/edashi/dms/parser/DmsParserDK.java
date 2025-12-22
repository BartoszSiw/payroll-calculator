package pl.edashi.dms.parser;

import org.w3c.dom.*;
import pl.edashi.dms.model.*;

import java.util.ArrayList;
import java.util.List;

public class DmsParserDK {

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
                daty != null ? daty.getAttribute("data") : ""
        );

        // ============================
        // 2. KONTRAHENT (typ 35)
        // ============================
        out.contractor = extractContractor(doc);

        // ============================
        // 3. PŁATNOŚCI (typ 48 + 49)
        // ============================
        out.payments = extractPayments(doc);

        // ============================
        // 4. UWAGI (operator, opis pozycji)
        // ============================
        out.notes = extractNotes(doc);

        // ============================
        // 5. DODATKOWY OPIS
        // ============================
        out.additionalDescription = "DK " + extractMainNumber(doc);

        // ============================
        // 6. PUSTE POLA (DK ich nie ma)
        // ============================
        out.positions = new ArrayList<>();
        out.vatRate = "";
        out.vatBase = "";
        out.vatAmount = "";
        out.fiscalNumber = "";
        out.fiscalDevice = "";
        out.fiscalDate = "";

        return out;
    }

    // ------------------------------
    // KONTRAHENT (typ 35)
    // ------------------------------
    private Contractor extractContractor(Document doc) {

        NodeList list = doc.getElementsByTagName("document");

        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("35".equals(el.getAttribute("typ"))) {

                Element dane = (Element) el.getElementsByTagName("dane").item(0);
                Element rozs = (Element) dane.getElementsByTagName("rozszerzone").item(0);

                Contractor c = new Contractor();

                c.id = rozs.getAttribute("kod_klienta");
                c.nip = rozs.getAttribute("nip");
                c.name1 = rozs.getAttribute("nazwa1");
                c.name2 = rozs.getAttribute("nazwa2");
                c.name3 = rozs.getAttribute("nazwa3");
                c.street = rozs.getAttribute("ulica");
                c.zip = rozs.getAttribute("kod_poczta");
                c.city = rozs.getAttribute("miejscowosc");
                c.country = rozs.getAttribute("kod_kraju");

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

                Element dane48 = (Element) el.getElementsByTagName("dane").item(0);

                // kwota
                Element wart = (Element) dane48.getElementsByTagName("wartosci").item(0);
                String kwota = wart != null ? wart.getAttribute("kwota") : "";

                // data zatwierdzenia
                Element daty = (Element) dane48.getElementsByTagName("daty").item(0);
                String date = daty != null ? daty.getAttribute("data_zatw") : "";

                // forma płatności (kod)
                Element klasyf = (Element) dane48.getElementsByTagName("klasyfikatory").item(0);
                String forma = klasyf != null ? klasyf.getAttribute("kod") : "";

                // operator
                Element operator = (Element) dane48.getElementsByTagName("operatorzy").item(0);
                String operatorName = operator != null ? operator.getAttribute("nazwa") : "";

                // pozycje (typ 49)
                String opis = extractPaymentDescription(dane48);

                DmsPayment p = new DmsPayment();
                p.kwota = kwota;
                p.termin = date;
                p.forma = forma;
                p.kierunek = "przychód";
                p.opis = opis;

                listOut.add(p);
            }
        }

        return listOut;
    }

    // opis z typ 49
    private String extractPaymentDescription(Element dane48) {

        NodeList docs = dane48.getElementsByTagName("document");

        for (int i = 0; i < docs.getLength(); i++) {
            Element el = (Element) docs.item(i);

            if ("49".equals(el.getAttribute("typ"))) {

                Element dane49 = (Element) el.getElementsByTagName("dane").item(0);
                Element rozs = (Element) dane49.getElementsByTagName("rozszerzone").item(0);

                if (rozs != null && rozs.hasAttribute("opis1")) {
                    return rozs.getAttribute("opis1");
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

        // operator z typ 02
        Element dane02 = (Element) doc.getElementsByTagName("dane").item(0);
        if (dane02 != null) {
            Element operator = (Element) dane02.getElementsByTagName("operatorzy").item(0);
            if (operator != null) {
                notes.add("Operator: " + operator.getAttribute("nazwa"));
            }
        }

        // opis z typ 49
        NodeList docs = doc.getElementsByTagName("document");
        for (int i = 0; i < docs.getLength(); i++) {
            Element el = (Element) docs.item(i);

            if ("49".equals(el.getAttribute("typ"))) {
                Element dane49 = (Element) el.getElementsByTagName("dane").item(0);
                Element rozs = (Element) dane49.getElementsByTagName("rozszerzone").item(0);

                if (rozs != null && rozs.hasAttribute("opis1")) {
                    notes.add(rozs.getAttribute("opis1"));
                }
            }
        }

        return notes;
    }

    // ------------------------------
    // Numer główny DK (typ 02)
    // ------------------------------
    private String extractMainNumber(Document doc) {

        Element dane = (Element) doc.getElementsByTagName("dane").item(0);
        if (dane == null) return "";

        Element numer = (Element) dane.getElementsByTagName("numer").item(0);
        if (numer == null) return "";

        return numer.getTextContent();
    }
}

