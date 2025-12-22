package pl.edashi.dms.parser;

import org.w3c.dom.*;

import pl.edashi.dms.model.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DmsParserDS {

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
                daty.getAttribute("data"),
                daty.getAttribute("data_sprzed"),
                daty.getAttribute("data_zatw")
        );

        // ============================
        // 2. KONTRAHENT (typ 35)
        // ============================
        out.contractor = extractContractor(doc);

        // ============================
        // 3. POZYCJE (typ 03)
        // ============================
        out.positions = extractPositions(doc);

        // ============================
        // 4. VAT (typ 06)
        // ============================
        extractVat(doc, out);

        // ============================
        // 5. PŁATNOŚCI (typ 40 + 43)
        // ============================
        out.payments = extractPayments(doc);
        
        // ============================
        // 6. FAKTURA (typ 94)
        // ============================
        extractInvoiceNumber(doc, out);

        // ============================
        // 7. FISKALIZACJA (typ 94)
        // ============================
        extractFiscal(doc, out);

        // ============================
        // 8. UWAGI (typ 98)
        // ============================
        out.notes = extractNotes(doc);

        return out;
    }

    // ------------------------------
    // KONTRAHENT
    // ------------------------------
    private Contractor extractContractor(Document doc) {
        NodeList list = doc.getElementsByTagName("document");

        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("35".equals(el.getAttribute("typ"))) {

                Contractor c = new Contractor();
                Element dane = (Element) el.getElementsByTagName("dane").item(0);
                Element rozs = (Element) dane.getElementsByTagName("rozszerzone").item(0);
                c.id = rozs.getAttribute("kod_klienta");
                c.nip = rozs.getAttribute("nip");
                c.name1 = rozs.getAttribute("nazwa1");
                c.name2 = rozs.getAttribute("nazwa2");
                c.name3 = rozs.getAttribute("nazwa3");
                c.country = rozs.getAttribute("kod_kraju");
                c.city = rozs.getAttribute("miejscowosc");
                c.zip = rozs.getAttribute("kod_poczta");
                c.street = rozs.getAttribute("ulica");

                return c;
            }
        }
        return null;
    }

    // ------------------------------
    // POZYCJE (typ 03)
    // ------------------------------
    private List<DmsPosition> extractPositions(Document doc) {

        List<DmsPosition> listOut = new ArrayList<>();
        NodeList list = doc.getElementsByTagName("document");


        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);
            String typ = el.getAttribute("typ");
            if ("03".equals(el.getAttribute("typ")) || "04".equals(el.getAttribute("typ"))) {

                Element dane = (Element) el.getElementsByTagName("dane").item(0);
                // nrPar
                Element nrPar = (Element) dane.getElementsByTagName("numer").item(0);
                // <wartosci>
                Element wart = (Element) dane.getElementsByTagName("wartosci").item(0);
                // <klasyfikatory>
                Element klas = (Element) dane.getElementsByTagName("klasyfikatory").item(0);
                // <rozszerzone>
                Element rozs = (Element) dane.getElementsByTagName("rozszerzone").item(0);
                DmsPosition p = new DmsPosition();
                // Ustaw typ pozycji
                p.type = el.getAttribute("typ");
                // KATEGORIA → z <klasyfikatory kod="NM1">
                p.kategoria = klas != null ? klas.getAttribute("kod") : "";

                // STAWKA VAT → w typie 03 nie ma, więc ustawiamy domyślnie
                p.stawkaVat = "23"; // lub pobierz z innego dokumentu, jeśli masz

                // NETTO → z <wartosci netto_sprzed="2.46">
                p.netto = wart != null ? wart.getAttribute("netto_sprzed") : "";

                // VAT → w typie 03 nie ma VAT, więc musimy policzyć
                if (p.netto != null && !p.netto.isEmpty()) {
                    try {
                        double netto = Double.parseDouble(p.netto);
                        double vat = netto * 0.23;
                        p.vat = String.format(Locale.US, "%.2f", vat);
                    } catch (Exception ex) {
                        p.vat = "0.00";
                    }
                } else {
                    p.vat = "0.00";
                }

                // RODZAJ SPRZEDAŻY → brak w typie 03, ustawiamy domyślnie
                p.rodzajSprzedazy = "towary";

                // VIN → z <rozszerzone vin="...">
                p.vin = rozs != null ? rozs.getAttribute("vin") : "";

                listOut.add(p);
            }

        }
        return listOut;
    }

    // ------------------------------
    // VAT (typ 06)
    // ------------------------------
    private void extractVat(Document doc, DmsParsedDocument out) {

        NodeList list = doc.getElementsByTagName("document");

        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("06".equals(el.getAttribute("typ"))) {

                Element dane = (Element) el.getElementsByTagName("dane").item(0);
                Element wart = (Element) dane.getElementsByTagName("wartosci").item(0);

                out.vatRate = dane.getAttribute("stawka");
                out.vatBase = wart.getAttribute("podstawa");
                out.vatAmount = wart.getAttribute("vat");
            }
        }
    }

    // ------------------------------
    // PŁATNOŚCI (typ 40 + 43)
    // ------------------------------
    private List<DmsPayment> extractPayments(Document doc) {

        List<DmsPayment> listOut = new ArrayList<>();
        NodeList list = doc.getElementsByTagName("document");

        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("40".equals(el.getAttribute("typ"))) {

                Element dane = (Element) el.getElementsByTagName("dane").item(0);
                Element wart = (Element) dane.getElementsByTagName("wartosci").item(0);
                
                DmsPayment p = new DmsPayment();
                p.kwota = wart.getAttribute("kwota");
                p.termin = ((Element) dane.getElementsByTagName("daty").item(0)).getAttribute("data");
                p.forma = ((Element) dane.getElementsByTagName("klasyfikatory").item(0)).getAttribute("kod");
                p.nrBank = ((Element) dane.getElementsByTagName("rozszerzone").item(0)).getAttribute("nr_rach");
                p.kierunek = "przychód";

                listOut.add(p);
            }
        }
        return listOut;
    }
    // ------------------------------
    // FAKTURA (typ 94)
    // ------------------------------
    private void extractInvoiceNumber(Document doc, DmsParsedDocument out) {

        NodeList list = doc.getElementsByTagName("document");

        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("94".equals(el.getAttribute("typ"))) {

                Element dane = (Element) el.getElementsByTagName("dane").item(0);

                // <numer nr="175">EAH1901348177/175</numer>
                //Element numer = (Element) dane.getElementsByTagName("numer").item(0);
                //if (numer != null) {
                  //  out.invoiceNumber = numer.getTextContent(); // EAH1901348177/175
                //}

                // <rozszerzone nr="EAH1901348177"/>
                Element rozs = (Element) dane.getElementsByTagName("rozszerzone").item(0);
                if (rozs != null) {
                    out.invoiceShortNumber = rozs.getAttribute("nr"); // EAH1901348177
                }
            }
        }
    }
    // ------------------------------
    // FISKALIZACJA (typ 94)
    // ------------------------------
    private void extractFiscal(Document doc, DmsParsedDocument out) {

        NodeList list = doc.getElementsByTagName("document");

        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("94".equals(el.getAttribute("typ"))) {

                Element dane = (Element) el.getElementsByTagName("dane").item(0);

                out.fiscalNumber = ((Element) dane.getElementsByTagName("numer").item(0)).getTextContent();
                out.fiscalDate = ((Element) dane.getElementsByTagName("daty").item(0)).getAttribute("data");
                out.fiscalDevice = ((Element) dane.getElementsByTagName("rozszerzone").item(0)).getAttribute("nr");
            }
        }
    }

    // ------------------------------
    // UWAGI (typ 98)
    // ------------------------------
    private List<String> extractNotes(Document doc) {

        List<String> notes = new ArrayList<>();
        NodeList list = doc.getElementsByTagName("document");

        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("98".equals(el.getAttribute("typ"))) {

                NodeList daneList = el.getElementsByTagName("dane");

                for (int d = 0; d < daneList.getLength(); d++) {
                    Element dane = (Element) daneList.item(d);
                    Element rozs = (Element) dane.getElementsByTagName("rozszerzone").item(0);

                    if (rozs != null) {
                        for (int k = 1; k <= 4; k++) {
                            String attr = "opis" + k;
                            if (rozs.hasAttribute(attr)) {
                                notes.add(rozs.getAttribute(attr));
                            }
                        }
                    }
                }
            }
        }
        return notes;
    }
}
