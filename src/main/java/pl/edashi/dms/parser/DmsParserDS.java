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
        Element dms = (Element) doc.getElementsByTagName("DMS").item(0);
        // ============================
        // 2. KONTRAHENT (typ 35)
        // ============================
        out.contractor = extractContractor(doc);
        // ============================
        // 2. DMS (typ dokumentu)
        // ============================
        extractDocumentNumberFromGenInfo(dms, out); 
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
        out.payments = extractPayments(doc, out);
        
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
    // NR Dokumentu (typ 03)
    // ------------------------------
    private void extractDocumentNumberFromGenInfo(Element dms, DmsParsedDocument out) {

        String info = dms.getAttribute("gen_info");
        if (info == null || info.isEmpty()) {
            return;
        }

        // usuń nawiasy
        info = info.replace("(", "").replace(")", "");

        // split po przecinku
        String[] parts = info.split(",");

        if (parts.length < 6) {
            return;
        }

        // parts[3] = typ dokumentu (Pr, FV, ...)
        // parts[4] = numer dokumentu
        // parts[5] = data

        out.documentType = parts[4].trim();   // jeśli chcesz
        out.invoiceNumber = parts[5].trim();  // ← TO JEST NUMER DOKUMENTU
        out.invoiceShortNumber = parts[5].trim(); // możesz użyć tego samego
        System.out.println(parts[5].trim());
    }
    // ------------------------------
    // POZYCJE (typ 03)
    // ------------------------------
    private List<DmsPosition> extractPositions(Document doc) {

        List<DmsPosition> listOut = new ArrayList<>();
        NodeList list = doc.getElementsByTagName("document");


        for (int i = 0; i < list.getLength(); i++) {
            Element document = (Element) list.item(i);
            String typ = document.getAttribute("typ");
            // Obsługujemy tylko typy 03, 04, 05
            if (!typ.equals("03") && !typ.equals("04") && !typ.equals("05")) {
                continue;
            }

            // 🔥 Pętla po WSZYSTKICH <dane> w danym <document>
            NodeList daneList = document.getElementsByTagName("dane");
            for (int j = 0; j < daneList.getLength(); j++) {

                Element dane = (Element) daneList.item(j);
                // nrPar
                //Element nrPar = (Element) dane.getElementsByTagName("numer").item(0);
                // <wartosci>
                Element wart = (Element) dane.getElementsByTagName("wartosci").item(0);
                // <klasyfikatory>
                Element klas = (Element) dane.getElementsByTagNameNS("*","klasyfikatory").item(0);
                // <rozszerzone>
                Element rozs = (Element) dane.getElementsByTagName("rozszerzone").item(0);
                DmsPosition p = new DmsPosition();
                String kanalKategoria = "";
                // Ustaw typ pozycji
                p.type = typ;
                // KATEGORIA → z <klasyfikatory kod="NM1">
                p.kategoria = klas != null ? klas.getAttribute("kod") : "";
                p.kanal = klas != null ? klas.getAttribute("kanal") : "";
                //System.out.println("klas = " + klas);
                if (!p.kategoria.isBlank()) {
                	p.kanalKategoria = p.kanal + "-" + p.kategoria;
                    System.out.println("kod=" + klas.getAttribute("kod"));
                    System.out.println("kanal=" + klas.getAttribute("kanal"));
                } else {
                	p.kanalKategoria = "";
                }

                // VIN → z <rozszerzone vin="...">
                p.vin = rozs != null ? rozs.getAttribute("vin") : "";

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
             // 🔥 RÓŻNE ZASADY DLA RÓŻNYCH TYPÓW
                switch (typ) {

                                case "03":
                                    // Materiały handlowe / zakupy kosztowe
                                    p.rodzajSprzedazy = "towary";
                                    //p.stawkaVat = "23";
                                    //p.kategoria = "MATERIAŁY_HANDLOWE";
                                    break;

                                case "04":
                                    // Robocizna i usługi
                                    p.rodzajSprzedazy = "uslugi";
                                    //p.stawkaVat = "23";

                                    // czas robocizny
                                    if (rozs != null) {
                                        //p.czasRob = rozs.getAttribute("czas_rob");
                                        //p.czasKat = rozs.getAttribute("czas_kat");
                                    }
                                    break;

                                case "05":
                                    // Usługi obce
                                    p.rodzajSprzedazy = "uslugi_obce";
                                    //p.stawkaVat = "23";

                                    // kod klienta, nr
                                    if (rozs != null) {
                                        //p.kodKlienta = rozs.getAttribute("kod_klienta");
                                        //p.nr = rozs.getAttribute("nr");
                                    }
                                    break;
                            }

                listOut.add(p);
            }

        }
     // ===============================
     // KOREKTA VAT – zgodność z typem 06
     // ===============================

     // Pobierz VAT z dokumentu typ 06
     NodeList vatDocs = doc.getElementsByTagName("document");
     double vatFromDms = 0.0;

     for (int i = 0; i < vatDocs.getLength(); i++) {
         Element el = (Element) vatDocs.item(i);
         if ("06".equals(el.getAttribute("typ"))) {
             Element daneVat = (Element) el.getElementsByTagName("dane").item(0);
             Element wartVat = (Element) daneVat.getElementsByTagName("wartosci").item(0);
             vatFromDms = Double.parseDouble(wartVat.getAttribute("vat"));
             break;
         }
     }

     // Suma VAT z pozycji
     double vatFromPositions = listOut.stream()
             .mapToDouble(p -> Double.parseDouble(p.vat))
             .sum();

     // Różnica
     double diff = vatFromDms - vatFromPositions;

     // Jeśli różnica jest minimalna (0.01 lub -0.01)
     if (Math.abs(diff) <= 0.10 && !listOut.isEmpty()) {
         DmsPosition last = listOut.get(listOut.size() - 1);

         double correctedVat = Double.parseDouble(last.vat) + diff;
         last.vat = String.format(Locale.US, "%.2f", correctedVat);

         // Możesz dodać log:
         System.out.println("KOREKTA VAT: różnica " + diff + " dodana do ostatniej pozycji.");
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
    private List<DmsPayment> extractPayments(Document doc, DmsParsedDocument out) {

        List<DmsPayment> listOut = new ArrayList<>();
        NodeList list = doc.getElementsByTagName("document");
        String terminPlatn = "";
        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);
// Płatności do dokumentu
            if ("40".equals(el.getAttribute("typ"))) {

                Element dane = (Element) el.getElementsByTagName("dane").item(0);
                Element wart = (Element) dane.getElementsByTagName("wartosci").item(0);
                
                DmsPayment p = new DmsPayment();
                p.kwota = wart.getAttribute("kwota");
                // VAT = VAT dokumentu (typ 06)
                p.vatZ = out.vatAmount != null ? out.vatAmount : "0.00";
                out.vatZ = p.vatZ;
                p.termin = ((Element) dane.getElementsByTagName("daty").item(0)).getAttribute("data");
                terminPlatn = p.termin;
                p.forma = ((Element) dane.getElementsByTagName("klasyfikatory").item(0)).getAttribute("kod");
                p.nrBank = ((Element) dane.getElementsByTagName("rozszerzone").item(0)).getAttribute("nr_rach");
                p.kierunek = "przychód";

                listOut.add(p);
            }
         // ------------------------------
         // ZALICZKI (typ 45)
         // ------------------------------
         if ("45".equals(el.getAttribute("typ"))) {

             Element dane = (Element) el.getElementsByTagName("dane").item(0);
             Element wart = (Element) dane.getElementsByTagName("wartosci").item(0);

             DmsPayment p = new DmsPayment();
             p.kwota = wart.getAttribute("brutto");   // kwota zaliczki
             // VAT = brutto - netto
             try {
                 double brutto = Double.parseDouble(wart.getAttribute("brutto"));
                 double netto  = Double.parseDouble(wart.getAttribute("netto"));
                 double vatZaliczki    = brutto - netto;

                 p.vatZ = String.format(Locale.US, "%.2f", vatZaliczki);
                 out.vatZ = p.vatZ;
             } catch (Exception ex) {
                 p.vatZ = "0.00";
                 out.vatZ = p.vatZ;
             }
             p.termin = terminPlatn;                           // brak terminu w zaliczce
             p.forma = "przelew";                     // możesz zmienić jeśli chcesz
             p.nrBank = "";                           // brak danych bankowych
             p.kierunek = "przychód";

             listOut.add(p);
         }

        }
        return listOut;
    }
    // ------------------------------
    // FAKTURA (typ 94)
    // ------------------------------
    //private void extractOtherNumber(Document doc, DmsParsedDocument out) {

        //NodeList list = doc.getElementsByTagName("document");

        //for (int i = 0; i < list.getLength(); i++) {
            //Element el = (Element) list.item(i);

            //if ("94".equals(el.getAttribute("typ"))) {

                //Element dane = (Element) el.getElementsByTagName("dane").item(0);

                // <numer nr="175">EAH1901348177/175</numer>
                //Element numer = (Element) dane.getElementsByTagName("numer").item(0);
                //if (numer != null) {
                  //  out.invoiceNumber = numer.getTextContent(); // EAH1901348177/175
                //}

                // <rozszerzone nr="EAH1901348177"/>
                //Element rozs = (Element) dane.getElementsByTagName("rozszerzone").item(0);
                //if (rozs != null) {
                    //out.invoiceShortNumber = rozs.getAttribute("nr"); // EAH1901348177
                //}
            //}
        //}
    //}
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
