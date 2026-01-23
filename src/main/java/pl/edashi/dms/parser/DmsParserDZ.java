package pl.edashi.dms.parser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.dms.model.Contractor;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.model.DmsParsedDocument.DmsVatEntry;
import pl.edashi.dms.model.DmsPayment;
import pl.edashi.dms.model.DmsPosition;
import pl.edashi.dms.model.DocumentMetadata;
import pl.edashi.dms.parser.util.DocumentNumberExtractor; // zakładam lokalizację helpera

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class DmsParserDZ {
    private final AppLogger log = new AppLogger("DmsParserDZ");
    public DmsParsedDocument parse(Document doc, String fileName) {
        DmsParsedDocument out = new DmsParsedDocument();
        out.setSourceFileName(fileName);
        Element root = doc.getDocumentElement();
        Element document = (Element) doc.getElementsByTagName("document").item(0);
        Element numer = (Element) document.getElementsByTagName("numer").item(0);

        String genDocId = root.getAttribute("gen_doc_id");
        String id = root.getAttribute("id");
        String trans = root.getAttribute("trans");
        Element daty = (Element) doc.getElementsByTagName("daty").item(0);
        Element warto = (Element) doc.getElementsByTagName("wartosci").item(0);
        /*if (root == null) {
            log.error("ParserDZ: brak root w pliku: " + fileName);
            return out;
        }*/
        log.info("DEBUG dane element = " + numer);
        log.info("DEBUG numer count = " + numer.getElementsByTagName("numer").getLength());

     // 1) Najpierw numer z <numer nr="...">
        String nrFromDane = DocumentNumberExtractor.extractNumberFromDane(numer);
        boolean hasNumberInDane = nrFromDane != null && !nrFromDane.isBlank();
        if (hasNumberInDane) {
            out.setInvoiceNumber(nrFromDane);
            out.setInvoiceShortNumber(DocumentNumberExtractor.normalizeNumber(nrFromDane));
            if (out.getDocumentType() == null || out.getDocumentType().isBlank()) {
                out.setDocumentType("DZ");
            }
        }
        /*if (nrFromDane != null && !nrFromDane.isBlank()) {
            out.setInvoiceNumber(nrFromDane);
            out.setInvoiceShortNumber(DocumentNumberExtractor.normalizeNumber(nrFromDane));
            // jeśli nadal brak typu – ustaw DZ jako domyślny
            if (out.getDocumentType() == null || out.getDocumentType().isBlank()) {
                out.setDocumentType("DZ");
            }
        }*/
     // 2) Jeśli nadal brak → gen_info

        boolean found = DocumentNumberExtractor.extractFromGenInfo(root, out, fileName,hasNumberInDane);
        
        log.info("out.getInvoiceNumber() = " + out.getInvoiceNumber() + "out.getInvoiceShortNumber()"+ out.getInvoiceShortNumber());
        if (!found || (out.getInvoiceShortNumber() == null && out.getInvoiceNumber() == null)) {
            String main = DocumentNumberExtractor.extractMainNumberFromDmsElement(root);
            if (main != null && !main.isBlank()) {
            	log.info("out.getInvoiceNumber() = " + out.getInvoiceNumber() + "out.getInvoiceShortNumber()"+ out.getInvoiceShortNumber());
            	
                out.setInvoiceNumber(main);
                out.setInvoiceShortNumber(DocumentNumberExtractor.normalizeNumber(main));
            }
            // jeśli nadal brak typu – ustaw DZ jako domyślny
            if (out.getDocumentType() == null || out.getDocumentType().isBlank()) {
                out.setDocumentType("DZ");
            }
        }
        
     // 3) Jeśli nadal brak → fallback
        if (out.getInvoiceNumber() == null || out.getInvoiceNumber().isBlank()) {
            String main = DocumentNumberExtractor.extractMainNumberFromDmsElement(root);
            if (main != null && !main.isBlank()) {
                out.setInvoiceNumber(main);
                out.setInvoiceShortNumber(DocumentNumberExtractor.normalizeNumber(main));
            }
        }
        try {
            NodeList docList = doc.getElementsByTagName("document");
            for (int i = 0; i < docList.getLength(); i++) {
                Element docEl = (Element) docList.item(i);
                if ("02".equals(docEl.getAttribute("typ"))) {
                    // Pobierz klasyfikatory niezależnie od tego, gdzie DOM je umieścił
                    Element klas = (Element) docEl.getElementsByTagName("klasyfikatory").item(0); 
                    if (klas!= null && klas.hasAttribute("wyr")) {
                        String wyrDoc = klas != null ? klas.getAttribute("wyr") : "";
                        // wyrDoc = "EX"
                        out.setDaneRejestr(wyrDoc);} // upewnij się, że DmsParsedDocument ma setter
                    break; // zwykle tylko jeden rekord 02
                }
            }
        } catch (Exception ex) {
            log.warn("ParserDS: nie udało się odczytać rejestru zakupu: " + ex.getMessage());
        }
        out.setMetadata(new DocumentMetadata(
                genDocId,
                id,
                trans,
                fileName,
                daty.getAttribute("data"),
                daty.getAttribute("data_sprzed"),
                daty.getAttribute("data_zatw"),
                daty.getAttribute("data_operacji"),
                warto.getAttribute("waluta")
        ));
        Element dms = (Element) doc.getElementsByTagName("DMS").item(0);
        // ============================
        // 2. KONTRAHENT (typ 35)
        // ============================
        out.setContractor(extractContractor(doc));
        // ============================
        // 3. DMS (typ dokumentu)
        // ============================
        //extractDocumentNumberFromGenInfo(dms, out); 
        out.setTypDocAnalizer("DZ");
        // 2) Wartości stawka / netto / vat (document typ="06")
        extractVat(doc, out);
        // ============================
        // 4. PŁATNOŚCI (typ 40 + 43)
        // ============================
        out.setPayments(extractPayments(doc, out));
        // ============================
        // 3. POZYCJE (typ 50)
        // ============================
        String defaultVatRate = out.getVatRate();
        if (defaultVatRate == null) defaultVatRate = "";
        out.setPositions(extractPositionsDZ(doc, out));

        /*log.info(String.format(Locale.US,
                "ParserDZ: END file=%s type=%s nr=%s positions=%d",
                fileName, out.getDocumentType(), out.getInvoiceShortNumber(), out.getPositions().size()));*/

        return out;
    }

    // ------------------------------
    // VAT (typ 06)
    // ------------------------------
    private void extractVat(Document doc, DmsParsedDocument out) {
        if (doc == null || out == null) return;
        NodeList list = doc.getElementsByTagName("document");
        boolean foundVat = false;
        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("06".equals(el.getAttribute("typ"))) {
            	NodeList daneList = el.getElementsByTagName("dane");
            	Map<String, String> vatRates = new HashMap<>();
                for (int j = 0; j < daneList.getLength(); j++) {
                	Element dane = (Element) daneList.item(j);
                    Element wart = firstElementByTag(dane, "wartosci");

                    DmsVatEntry entry = new DmsVatEntry();
                    entry.stawka = safeAttr(dane, "stawka");
                    entry.podstawa = wart != null ? safeAttr(wart, "podstawa") : "";
                    entry.vat = wart != null ? safeAttr(wart, "vat") : "";
                    out.addVatEntry(entry);
                    String kod = safeAttr(dane, "kod");      // 02, 22
                    String stawka = safeAttr(dane, "stawka"); // 23.00, 8.00
                    vatRates.put(kod, stawka);
                    foundVat = true;
                }    
                // 🔥 Zapisz mapę VAT dla całego dokumentu
                out.setVatRates(vatRates);

                break; // typ 06 jest tylko jeden
                /*String stawka = safeAttr(dane, "stawka");
                String podstawa = wart != null ? safeAttr(wart, "podstawa") : "";
                String vat = wart != null ? safeAttr(wart, "vat") : "";

                out.setVatRate(normalizeVatRate(stawka));
                out.setVatBase(podstawa);
                out.setVatAmount(vat);
                foundVat = true;
                break;*/
            }
        }
        out.setHasVatDocument(foundVat);
        //return; // typ 06 jest tylko jeden
    }

    private List<DmsPosition> extractPositionsDZ(Document doc, DmsParsedDocument out) {
        List<DmsPosition> listOut = new ArrayList<>();
        NodeList docs = doc.getElementsByTagName("document");
     // 1) Najpierw sprawdzamy, czy w ogóle istnieje 03 lub 08
        boolean has03or08 = false;
        for (int i = 0; i < docs.getLength(); i++) {
            Element docEl = (Element) docs.item(i);
            String typ = docEl.getAttribute("typ");
            if ("03".equals(typ)) { //|| "08".equals(typ)
                has03or08 = true;
                break;
            }
        }

        // 2) Teraz właściwa pętla po dokumentach DZ
        for (int i = 0; i < docs.getLength(); i++) {
            Element document = (Element) docs.item(i);
            String docTyp = document.getAttribute("typ");

            if (has03or08) {
                // Jeśli są 03/08 → przetwarzamy tylko 03 i 08, pomijamy 50
                if (!"03".equals(docTyp)) continue; //|| "08".equals(typ)
            } else {
                // Jeśli NIE ma 03/08 → przetwarzamy tylko 50
                if (!"50".equals(docTyp)) continue;
            }/*
         // DZ: przetwarzamy 03 i 50
            if (!"03".equals(docTyp) && !"50".equals(docTyp)) {
                continue;
            }*/

            NodeList daneList = document.getElementsByTagName("dane");
            for (int j = 0; j < daneList.getLength(); j++) {
                Element dane = (Element) daneList.item(j);
                Element wart = firstElementByTag(dane, "wartosci");
                Element rozs = firstElementByTag(dane, "rozszerzone");
                Element klas = (Element) dane.getElementsByTagNameNS("*","klasyfikatory").item(0);

                // atrybuty z <dane>
                String kodVatAttr = safeAttr(dane, "kod_vat");      // np. "02"
                String typAttr = safeAttr(dane, "typ");             // np. "C"
                String jm = safeAttr(dane, "jm");
                String jmSymb = safeAttr(dane, "jm_symb");

                // numer pozycji (opcjonalny)
                String numer = "";
                Element numEl = firstElementByTag(dane, "numer");
                if (numEl != null) numer = numEl.getTextContent().trim();

                // jeśli klasyfikacja PR (rzadko w DZ) — dodaj jako referencję
                String klasyf = klas != null ? klas.getAttribute("klasyfikacja") : "";
                if ("PR".equalsIgnoreCase(klasyf) && numer != null && !numer.isBlank()) {
                    DmsPosition ref = new DmsPosition();
                    ref.setKlasyfikacja("PR");
                    ref.setNumer(numer);
                    listOut.add(ref);
                    //log.info("ParserDZ: ADDED POSITION PR=" + numer);
                }
                log.info("DOC TYP 1 = '" + docTyp + "'");
                // budujemy pozycję DZ
                DmsPosition p = new DmsPosition();
                p.kategoria2 = klas != null ? klas.getAttribute("kod") : "";
                p.type = docTyp;
                p.kodVat = kodVatAttr;
                p.typDZ = typAttr;
                p.jm = jm;
                p.jmSymb = jmSymb;
                p.opis1 = rozs != null ? safeAttr(rozs, "opis1") : "";
                p.vin = rozs != null ? safeAttr(rozs, "vin") : "";

                // ceny i netto: preferuj netto_prup, potem netto, potem cena_netto
                String nettoStr = "";
                String nettoRodzaj = "netto";
                if (wart != null) {
                	switch (docTyp) {
                	case "03": nettoRodzaj = "netto_zakup" ; break;
                	case "50": nettoRodzaj = "netto_prup"; break;
                	default: nettoRodzaj = "netto";
                	}
                    nettoStr = safeAttr(wart, nettoRodzaj);
                    if (nettoStr.isBlank()) nettoStr = safeAttr(wart, "netto");
                    if (nettoStr.isBlank()) nettoStr = safeAttr(wart, "cena_netto");
                	
                }
                p.netto = nettoStr;
                p.cenaNetto = wart != null ? safeAttr(wart, "cena_netto") : "";

                boolean hasVat = out.isHasVatDocument();
                //String vatRate = out.getVatRate();
                String kodVat = p.getKodVat(); // np. "02"
                String stawka = out.getVatRates().get(kodVat);

                if (stawka != null) {
                    p.setStawkaVat(stawka);
                }

                //log.info(LogUtils.safeFormat("hasVat=%s, vatRate=%s", hasVat, vatRate));
                if (!hasVat) {
                	double nettoVal = 0.0;
                    p.stawkaVat = "0";
                    p.vat = "0.00";
                    p.statusVat = "opodatkowana";
                    if (p.netto != null && !p.netto.isBlank()) {
                        nettoVal = Double.parseDouble(p.netto.replace(",", "."));
                    }
                    String bruttoStr = String.format(Locale.US, "%.2f", nettoVal);
                    p.brutto = bruttoStr;
                    //p.brutto = String.format(Locale.US, "%.2f", p.netto);
                    //log.info(String.format("1 hasVat p.statusVat='%s': ", p.statusVat));
                } else {
                    p.stawkaVat = stawka != null ? stawka : "";
                    p.statusVat = "opodatkowana";
                    //log.info(String.format("2 hasVat p.statusVat='%s': ", p.statusVat));
                    if (p.netto != null && !p.netto.isBlank()) {
                        try {
                            double netto = Double.parseDouble(p.netto);
                            double vat = netto * (Double.parseDouble(stawka) / 100.0);
                            double brutto = netto + vat;
                            p.brutto = String.format(Locale.US, "%.2f", brutto);
                            p.vat = String.format(Locale.US, "%.2f", vat);
                            //log.info(String.format("3 hasVat p.statusVat='%s': ", p.statusVat));
                        } catch (Exception ex) {
                            p.vat = "0.00";
                        }
                    } else {
                        p.vat = "0.00";
                    }
                }
                switch (docTyp) {
                case "03": p.rodzajKoszty = "Towary" ; p.kategoria = "MATERIAŁY"; break;
                case "50": p.rodzajKoszty = "Inne";  break;
                case "08": p.rodzajKoszty = "Inne"; break;// Opłaty: transportowa, ewidencyjna, kaucje, opakowania

            }
                listOut.add(p);
            }
        }

        // ===============================
        // KOREKTA ZAOKROGLENIA
        // ===============================
        double baseFromDms = parseDoubleSafe(out.getVatBase()); //vat podstawa
        double vatFromDms = parseDoubleSafe(out.getVatAmount());
        double nettoFromPositions = listOut.stream()
                .mapToDouble(p -> parseDoubleSafe(p.netto)).sum();

        double bruttoFromPositions = listOut.stream()
                .mapToDouble(p -> parseDoubleSafe(p.brutto)).sum();


        // Suma VAT z pozycji
        double vatFromPositions = listOut.stream()
                .mapToDouble(p -> Double.parseDouble(p.vat))
                .sum();
        //log.info(String.format("1 extractPositions: baseFromDms='%s':, vatFromDms='%s':, nettoFromPositions='%s': ,vatFromPositions='%s':,bruttoFromPositions='%s': ",baseFromDms, vatFromDms, nettoFromPositions, vatFromPositions, bruttoFromPositions));

        double advanceNet = parseDoubleSafe(out.getAdvanceNet());
        double advanceVat = parseDoubleSafe(out.getAdvanceVat());
        double advanceBrutto = advanceNet; //+ advanceVat
        double diffBruttoPosAdv = bruttoFromPositions - (baseFromDms + vatFromDms + advanceBrutto);
        //log.info(String.format("2 extractPositions: advanceNet='%s': ,advanceVat='%s': ,advanceBrutto='%s':  ,diffBruttoPosAdv='%s': ", advanceNet, advanceVat, advanceBrutto, diffBruttoPosAdv));
        if (Math.abs(advanceNet) > 0) {//<= 0.10) {
            DmsPosition last = listOut.get(listOut.size() - 1);

            double netto = parseDoubleSafe(last.netto) - advanceNet;
            double vat   = parseDoubleSafe(last.vat) - advanceVat ;//
            //log.info(String.format("2a extractPositions: netto='%s': ,vat='%s': ", netto, vat));
            last.netto = String.format(Locale.US, "%.2f", netto);
            last.vat   = String.format(Locale.US, "%.2f", vat);
        }

        double nettoAfterAdvance = listOut.stream()
                .mapToDouble(p -> parseDoubleSafe(p.netto))
                .sum();

        double vatAfterAdvance = listOut.stream()
                .mapToDouble(p -> parseDoubleSafe(p.vat))
                .sum();
        //log.info(String.format("3 extractPositions: nettoAfterAdvance='%s': ,vatAfterAdvance='%s': ", nettoAfterAdvance, vatAfterAdvance));
        // Różnica
        double diffVat = vatFromDms - vatAfterAdvance;//
        double diffNetto = baseFromDms - nettoAfterAdvance;//
        //log.info(String.format("4 extractPositions: diffVat='%s': ,diffNetto='%s': ", diffVat, diffNetto));
        // Jeśli różnica jest minimalna (0.01 lub -0.01)
        boolean hasSmallDiff =
                (Math.abs(diffVat) > 0.0001 && Math.abs(diffVat) <= 0.10) ||
                (Math.abs(diffNetto) > 0.0001 && Math.abs(diffNetto) <= 0.10);

        //if (!listOut.isEmpty() && (Math.abs(diffVat) <= 0.10 || Math.abs(diffNetto) <= 0.10)) {
        if (!listOut.isEmpty() && hasSmallDiff) {
            DmsPosition last = listOut.get(listOut.size() - 1);

            double vat = Double.parseDouble(last.vat);
            double net = Double.parseDouble(last.netto);

            vat += diffVat;
            net += diffNetto;

            last.vat = String.format(Locale.US, "%.2f", vat);
            last.netto = String.format(Locale.US, "%.2f", net);
}

        return listOut;
    }
    
    // ------------------------------
    // PŁATNOŚCI (typ 40 + 43)
    // ------------------------------
    private List<DmsPayment> extractPayments(Document doc, DmsParsedDocument out) {

        List<DmsPayment> listOut = new ArrayList<>();
        if (doc == null || out == null) return listOut;
        NodeList list = doc.getElementsByTagName("document");
        //NodeList list = doc.getDocumentElement().getChildNodes();

        String terminPlatn = "";
        for (int i = 0; i < list.getLength(); i++) {
        	Element el = (Element) list.item(i);
            String typ = safeAttr(el, "typ");
            // Płatności do dokumentu
            String opis = safeAttr(el, "opis");
            if ("40".equals(typ) && "Płatności do dokumentu".equalsIgnoreCase(opis)) {
            	NodeList daneList = el.getElementsByTagName("dane");
                for (int j = 0; j < daneList.getLength(); j++) {
                	Element dane = (Element) daneList.item(j);
                if (dane == null) continue;
                // bierzemy TYLKO <dane>, których bezpośrednim rodzicem jest <document typ="40">
                if (dane.getParentNode() != el) continue;

                Element wart = firstElementByTag(dane, "wartosci");
                
                DmsPayment p = new DmsPayment();
                p.setIdPlatn(UUID.randomUUID().toString());
                p.setAdvance(false);
                String kwota = wart != null ? safeAttr(wart, "kwota") : "";
                double kw = parseDoubleSafe(kwota);
                if (kw < 0) { out.setKierunek("przychód"); } else { out.setKierunek("rozchód"); }
                p.setKierunek(out.getKierunek());
                // zawsze dodatnia kwota płatności
                kwota = String.format(Locale.US, "%.2f", Math.abs(kw));
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
                
                //log.info("1 extractPayment Kierunek: " + p.getKierunek());
                //p.setKierunek("przychód");
                listOut.add(p);
                }
            }
         // ------------------------------
         // ZALICZKI (typ 45)
         // ------------------------------
            if ("45".equals(typ)) {
            	double dSumAdvanceVat = 0;
            	double dSumAdvanceNet = 0;
            	String sSumAdvanceVat = "";
            	String sSumAdvanceNet = "";
                NodeList daneList45 = el.getElementsByTagName("dane");
                for (int j = 0; j < daneList45.getLength(); j++) {
                	Element dane = (Element) daneList45.item(j);
                if (dane == null) continue;
                if (dane.getParentNode() != el) continue;
                Element wart = firstElementByTag(dane, "wartosci");
                if (wart == null) continue;
             DmsPayment p = new DmsPayment();
             p.setIdPlatn(UUID.randomUUID().toString());
             p.setAdvance(true);
             String lp = safeAttr(dane, "lp");
             String bruttoStr = safeAttr(wart, "brutto");
             String nettoStr = safeAttr(wart, "netto");
             //log.info(String.format("1 extractPayments: nettoStr='%s': ,lp='%s': ", nettoStr, lp));
             double kw = parseDoubleSafe(bruttoStr);
             if (kw < 0) { out.setKierunek("rozchód"); } else { out.setKierunek("przychód"); }
             // zawsze dodatnia kwota płatności
             bruttoStr = String.format(Locale.US, "%.2f", Math.abs(kw));
             //log.info(String.format("2 extractPayments: bruttoStr='%s': ,kw='%s': ", bruttoStr, kw));
             p.setKwota(bruttoStr);
             double kwn = parseDoubleSafe(nettoStr);
             dSumAdvanceNet = dSumAdvanceNet + kwn;
             log.info(String.format("3 extractPayments: dSumAdvanceNet='%s': ,kwn='%s': ", dSumAdvanceNet, kwn));
             nettoStr = String.format(Locale.US, "%.2f", Math.abs(kwn));
             sSumAdvanceNet = String.format(Locale.US, "%.2f", Math.abs(dSumAdvanceNet));
             String advanceNet = sSumAdvanceNet;
             out.setAdvanceNet(advanceNet);

             // VAT = brutto - netto
             try {
                 double brutto = bruttoStr != null && !bruttoStr.isEmpty() ? Double.parseDouble(bruttoStr) : 0.0;
                 double netto  = nettoStr != null && !nettoStr.isEmpty() ? Double.parseDouble(nettoStr) : 0.0;
                 double vatZaliczki = brutto - netto;
                 dSumAdvanceVat = dSumAdvanceVat + vatZaliczki;
                 //log.info(String.format("3 extractPayments: dSumAdvanceVat='%s': ,vatZaliczki='%s': ", dSumAdvanceVat, vatZaliczki));
                 String vatZ = String.format(Locale.US, "%.2f", vatZaliczki);
                 p.setVatZ(vatZ);
                 out.setVatZ(vatZ);
                 sSumAdvanceVat = String.format(Locale.US, "%.2f", Math.abs(dSumAdvanceVat));
                 String advanceVat = sSumAdvanceVat;
                 out.setAdvanceVat(advanceVat);
             } catch (Exception ex) {
                 p.setVatZ("0.00");
                 out.setVatZ("0.00");
             }
             // brak terminu w zaliczce — używamy ostatniego znanego terminu płatności
             p.setTermin(terminPlatn != null ? terminPlatn : "");
             //String forma = klasyf != null ? safeAttr(klasyf, "kod") : ""; 
             //p.setForma(forma);
             p.setForma("przelew"); //nie ma kod jak wyżej, może inny dokument np. KZ da info o formie?
             p.setNrBank("");
             p.setKierunek(out.getKierunek());
             log.info("2 extractPayment Kierunek: " + p.getKierunek());
             listOut.add(p);
         }
            }
            
        }
        return listOut;
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

    // -----------------------
    // Helper methods
    // -----------------------

    private Element firstElementByTag(Element parent, String tag) {
        if (parent == null) return null;
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl == null || nl.getLength() == 0) return null;
        Node n = nl.item(0);
        return n instanceof Element ? (Element) n : null;
    }

    private Element firstElementByTag(Document doc, String tag) {
        NodeList nl = doc.getElementsByTagName(tag);
        if (nl == null || nl.getLength() == 0) return null;
        Node n = nl.item(0);
        return n instanceof Element ? (Element) n : null;
    }

    private String safeAttr(Element el, String attr) {
        if (el == null) return "";
        String v = el.getAttribute(attr);
        return v == null ? "" : v.trim();
    }

    private String getRozszerzoneOpis(Element dane) {
        Element rozs = firstElementByTag(dane, "rozszerzone");
        if (rozs == null) return "";
        // często opis jest w atrybucie opis1
        String opis = safeAttr(rozs, "opis1");
        if (!opis.isBlank()) return opis;
        // albo tekst wewnątrz
        String text = rozs.getTextContent();
        return text == null ? "" : text.trim();
    }
    private String normalizeVatRate(String raw) {
        if (raw == null) return "";
        raw = raw.trim();
        if (raw.isBlank()) return "";
        // "23.00" -> "23", "23" -> "23"
        if (raw.contains(".")) raw = raw.substring(0, raw.indexOf('.'));
        return raw;
    }

    private double parseDoubleSafe(String s) {
        try { return s != null && !s.isBlank() ? Double.parseDouble(s) : 0.0; }
        catch (Exception e) { return 0.0; }
    }
    
}
