package pl.edashi.dms.parser;
import pl.edashi.common.logging.AppLogger;
import org.w3c.dom.*;

import pl.edashi.dms.model.*;
import pl.edashi.dms.parser.util.DocumentNumberExtractor;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DmsParserDS {
	private final AppLogger log = new AppLogger("DmsParserDS");
    public DmsParsedDocument parse(Document doc, String fileName) {

        DmsParsedDocument out = new DmsParsedDocument();

        // ============================
        // 1. METADATA
        // ============================
        Element root = doc.getDocumentElement();

        String genDocId = root.getAttribute("gen_doc_id");
        String id = root.getAttribute("id");
        String trans = root.getAttribute("trans");
        out.setSourceFileName(fileName);

        Element daty = (Element) doc.getElementsByTagName("daty").item(0);
     // preferowana, centralna metoda ekstrakcji dla parsera DS
        
        boolean found = DocumentNumberExtractor.extractFromGenInfo(root, out, fileName);
        /*log.info(String.format(
        	    "[Parser DS] AFTER gen_info: type='%s', invoiceShort='%s', invoice='%s', file='%s'",
        	    out.getDocumentType(),
        	    out.getInvoiceShortNumber(),
        	    out.getInvoiceNumber(),
        	    fileName
        	));*/
        //log.info(String.format("[Parser DS] AFTER extractor: documentType='%s', invoiceShort='%s', file='%s'",
                //out.getDocumentType(), out.getInvoiceShortNumber(), fileName));

     // 2) Jeśli gen_info NIC nie ustawiło – fallback
        if (!found || (out.getInvoiceShortNumber() == null && out.getInvoiceNumber() == null)) {

            String main = DocumentNumberExtractor.extractMainNumberFromDmsElement(root);

            if (main != null && !main.isBlank()) {
                out.setInvoiceNumber(main);
                out.setInvoiceShortNumber(DocumentNumberExtractor.normalizeNumber(main));
            }

            // jeśli nadal brak typu – ustaw DS
            if (out.getDocumentType() == null || out.getDocumentType().isBlank()) {
                out.setDocumentType("DS");
            }
        }



        // debug (tymczasowo) — pokaże co mamy po ekstrakcji
        //log.info(String.format("Parser DS: extracted documentType='%s', invoiceShort='%s', file=%s",
                               //out.getDocumentType(), out.getInvoiceShortNumber(), fileName));

        out.setMetadata(new DocumentMetadata(
                genDocId,
                id,
                trans,
                fileName,
                daty.getAttribute("data"),
                daty.getAttribute("data_sprzed"),
                daty.getAttribute("data_zatw")
        ));
        Element dms = (Element) doc.getElementsByTagName("DMS").item(0);
        // ============================
        // 2. KONTRAHENT (typ 35)
        // ============================
        out.setContractor(extractContractor(doc));
        // ============================
        // 2. DMS (typ dokumentu)
        // ============================
        //extractDocumentNumberFromGenInfo(dms, out); 
        out.setTypDocAnalizer("DS");
        // ============================
        // 3. POZYCJE (typ 03)
        // ============================
        out.setPositions(extractPositions(doc));

        // ============================
        // 4. VAT (typ 06)
        // ============================
        extractVat(doc, out);

        // ============================
        // 5. PŁATNOŚCI (typ 40 + 43)
        // ============================
        out.setPayments(extractPayments(doc, out));
        
        // ============================
        // 7. FISKALIZACJA (typ 94)
        // ============================
        extractFiscal(doc, out);

        // ============================
        // 8. UWAGI (typ 98)
        // ============================
        out.setNotes(extractNotes(doc));

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
            Element document = (Element) list.item(i);
            String typ = document.getAttribute("typ");
            //log.info("22 typ: " + typ);
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
                    //System.out.println("kod=" + klas.getAttribute("kod"));
                    //System.out.println("kanal=" + klas.getAttribute("kanal"));
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
         //System.out.println("KOREKTA VAT: różnica " + diff + " dodana do ostatniej pozycji.");
     }

        return listOut;
    }

    // ------------------------------
    // VAT (typ 06)
    // ------------------------------
    private void extractVat(Document doc, DmsParsedDocument out) {
        if (doc == null || out == null) return;
        NodeList list = doc.getElementsByTagName("document");

        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("06".equals(el.getAttribute("typ"))) {

                Element dane = firstElementByTag(el, "dane");
                if (dane == null) continue;
                Element wart = firstElementByTag(dane, "wartosci");

                String stawka = safeAttr(dane, "stawka");
                String podstawa = wart != null ? safeAttr(wart, "podstawa") : "";
                String vat = wart != null ? safeAttr(wart, "vat") : "";

                out.setVatRate(stawka);
                out.setVatBase(podstawa);
                out.setVatAmount(vat);
            }
        }
    }

    // ------------------------------
    // PŁATNOŚCI (typ 40 + 43)
    // ------------------------------
    private List<DmsPayment> extractPayments(Document doc, DmsParsedDocument out) {

        List<DmsPayment> listOut = new ArrayList<>();
        if (doc == null || out == null) return listOut;
        NodeList list = doc.getElementsByTagName("document");
        String terminPlatn = "";
        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);
            String typ = safeAttr(el, "typ");
            // Płatności do dokumentu
            if ("40".equals(typ)) {
                Element dane = firstElementByTag(el, "dane");
                if (dane == null) continue;
                Element wart = firstElementByTag(dane, "wartosci");
                
                DmsPayment p = new DmsPayment();
                String kwota = wart != null ? safeAttr(wart, "kwota") : "";
                p.setKwota(kwota);
                // VAT = VAT dokumentu (typ 06) — korzystamy z getterów/setterów DmsParsedDocument
                String vatAmount = out.getVatAmount() != null && !out.getVatAmount().isEmpty() ? out.getVatAmount() : "0.00";
                p.setVatZ(vatAmount);
                out.setVatZ(vatAmount);
                Element daty = firstElementByTag(dane, "daty");
                String termin = daty != null ? safeAttr(daty, "data") : "";
                p.setTermin(termin);
                terminPlatn = termin;

                Element klasyf = firstElementByTag(dane, "klasyfikatory");
                String forma = klasyf != null ? safeAttr(klasyf, "kod") : "";
                p.setForma(forma);

                Element rozs = firstElementByTag(dane, "rozszerzone");
                String nrRach = rozs != null ? safeAttr(rozs, "nr_rach") : "";
                p.setNrBank(nrRach);

                p.setKierunek("przychód");
                listOut.add(p);
            }
         // ------------------------------
         // ZALICZKI (typ 45)
         // ------------------------------
            if ("45".equals(typ)) {
                Element dane = firstElementByTag(el, "dane");
                if (dane == null) continue;
                Element wart = firstElementByTag(dane, "wartosci");

             DmsPayment p = new DmsPayment();
             String bruttoStr = wart != null ? safeAttr(wart, "brutto") : "";
             String nettoStr = wart != null ? safeAttr(wart, "netto") : "";
             p.setKwota(bruttoStr);
             // VAT = brutto - netto
             try {
                 double brutto = bruttoStr != null && !bruttoStr.isEmpty() ? Double.parseDouble(bruttoStr) : 0.0;
                 double netto  = nettoStr != null && !nettoStr.isEmpty() ? Double.parseDouble(nettoStr) : 0.0;
                 double vatZaliczki = brutto - netto;
                 String vatZ = String.format(Locale.US, "%.2f", vatZaliczki);
                 p.setVatZ(vatZ);
                 out.setVatZ(vatZ);
             } catch (Exception ex) {
                 p.setVatZ("0.00");
                 out.setVatZ("0.00");
             }
             // brak terminu w zaliczce — używamy ostatniego znanego terminu płatności
             p.setTermin(terminPlatn != null ? terminPlatn : "");
             p.setForma("przelew");
             p.setNrBank("");
             p.setKierunek("przychód");

             listOut.add(p);
         }

        }
        return listOut;
    }
    // ------------------------------
    // FISKALIZACJA (typ 94)
    // ------------------------------
    private void extractFiscal(Document doc, DmsParsedDocument out) {
        if (doc == null || out == null) return;
        NodeList list = doc.getElementsByTagName("document");

        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if (!"94".equals(safeAttr(el, "typ"))) continue;

            Element dane = firstElementByTag(el, "dane");
            if (dane == null) continue;
            // numer
            Element numerEl = firstElementByTag(dane, "numer");
            String fiscalNumber = numerEl != null ? trimOrEmpty(numerEl.getTextContent()) : "";

            // data
            Element datyEl = firstElementByTag(dane, "daty");
            String fiscalDate = datyEl != null ? safeAttr(datyEl, "data") : "";

            // urząd fiskalny / nr
            Element rozsEl = firstElementByTag(dane, "rozszerzone");
            String fiscalDevice = rozsEl != null ? safeAttr(rozsEl, "nr") : "";

            out.setFiscalNumber(fiscalNumber);
            out.setFiscalDate(fiscalDate);
            out.setFiscalDevice(fiscalDevice);
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
    // ------------------------------
    // Pomocnicze metody
    // ------------------------------
    private static Element firstElementByTag(Node parent, String tagName) {
        if (parent == null) return null;
        NodeList list;
        if (parent instanceof Document) list = ((Document) parent).getElementsByTagName(tagName);
        else list = ((Element) parent).getElementsByTagName(tagName);
        return (list == null || list.getLength() == 0) ? null : (Element) list.item(0);
    }

    private static String safeAttr(Element el, String name) {
        if (el == null) return "";
        String v = el.getAttribute(name);
        return v != null ? v : "";
    }
    private static String trimOrEmpty(String s) { return s == null ? "" : s.trim(); }

}
