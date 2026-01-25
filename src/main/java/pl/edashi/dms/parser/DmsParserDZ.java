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
        // 3. POZYCJE (typ 50)
        // ============================
        extractVat(doc, out);
        out.setPositions(extractPositionsDZ(doc, out));
        // 2) Wartości stawka / netto / vat (document typ="06")
        // ============================

        // ============================
        // 4. PŁATNOŚCI (typ 40 + 43)
        // ============================
        out.setPayments(extractPayments(doc, out));
        //String defaultVatRate = out.getVatRate();
        //if (defaultVatRate == null) defaultVatRate = "";
      

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
                    log.info(String.format(
                    	    "[PARSER][DZ][VAT] entry: kod=%s podstawa=%s vat=%s",
                    	    kod, entry.podstawa, entry.vat
                    	));

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
    private void applyVatToPositions(DmsParsedDocument out, List<DmsPosition> list) {
        Map<String, String> vatRates = out.getVatRates();

        for (DmsPosition p : list) {

            String stawka = vatRates.get(p.kodVat);

            if (stawka != null && !stawka.isBlank()) {
                // ustaw stawkę VAT
                p.stawkaVat = stawka;

                // policz VAT i brutto
                double netto = parseDoubleSafe(p.netto);
                double stawkaVal = parseDoubleSafe(stawka);

                double vat = netto * stawkaVal / 100.0;
                double brutto = netto + vat;

                p.vat = String.format(Locale.US, "%.2f", vat);
                p.brutto = String.format(Locale.US, "%.2f", brutto);
                log.info(String.format(
                	    "[DZ][POS] lp=%s kodVat=%s stawka=%s netto=%s vat=%s brutto=%s stawkaVal=%s",
                	    p.getLp(), p.getKodVat(), p.getStawkaVat(), p.getNetto(), p.getVat(), p.getBrutto(), stawkaVal
                	));

            } else {
                // fallback — brak stawki VAT
                p.stawkaVat = "0";
                p.vat = "0.00";
                p.brutto = p.netto != null ? p.netto : "0.00";
            }
        }
    }

  private void applyCorrections(DmsParsedDocument out, List<DmsPosition> list) {
    	double baseFromDms = parseDoubleSafe(out.getVatBase()); //vat podstawa
        double vatFromDms = parseDoubleSafe(out.getVatAmount());
        double nettoFromPositions = list.stream()
                .mapToDouble(p -> parseDoubleSafe(p.netto)).sum();

        double bruttoFromPositions = list.stream()
                .mapToDouble(p -> parseDoubleSafe(p.brutto)).sum();


        // Suma VAT z pozycji
        double vatFromPositions = list.stream()
                .mapToDouble(p -> parseDoubleSafe(p.vat))
                .sum();
        //log.info(String.format("1 extractPositions: baseFromDms='%s':, vatFromDms='%s':, nettoFromPositions='%s': ,vatFromPositions='%s':,bruttoFromPositions='%s': ",baseFromDms, vatFromDms, nettoFromPositions, vatFromPositions, bruttoFromPositions));

        double advanceNet = parseDoubleSafe(out.getAdvanceNet());
        double advanceVat = parseDoubleSafe(out.getAdvanceVat());
        double advanceBrutto = advanceNet; //+ advanceVat
        double diffBruttoPosAdv = bruttoFromPositions - (baseFromDms + vatFromDms + advanceBrutto);
        log.info(String.format("2 extractPositions: advanceNet='%s': ,advanceVat='%s': ,advanceBrutto='%s':  ,diffBruttoPosAdv='%s': ", advanceNet, advanceVat, advanceBrutto, diffBruttoPosAdv));
        if (Math.abs(advanceNet) > 0) {//<= 0.10) {
            DmsPosition last = list.get(list.size() - 1);

            double netto = parseDoubleSafe(last.netto) - advanceNet;
            double vat   = parseDoubleSafe(last.vat) - advanceVat ;//
            log.info(String.format("2a extractPositions: netto='%s': ,vat='%s': ", netto, vat));
            last.netto = String.format(Locale.US, "%.2f", netto);
            last.vat   = String.format(Locale.US, "%.2f", vat);
        }

        double nettoAfterAdvance = list.stream()
                .mapToDouble(p -> parseDoubleSafe(p.netto))
                .sum();

        double vatAfterAdvance = list.stream()
                .mapToDouble(p -> parseDoubleSafe(p.vat))
                .sum();
        log.info(String.format("3 extractPositions: nettoAfterAdvance='%s': ,vatAfterAdvance='%s': ", nettoAfterAdvance, vatAfterAdvance));
        // Różnica
        double diffVat = vatFromDms - vatAfterAdvance;//
        double diffNetto = baseFromDms - nettoAfterAdvance;//
        log.info(String.format("4 extractPositions: diffVat='%s': ,diffNetto='%s': ", diffVat, diffNetto));
        // Jeśli różnica jest minimalna (0.01 lub -0.01)
        boolean hasSmallDiff =
                (Math.abs(diffVat) > 0.0001 && Math.abs(diffVat) <= 0.10) ||
                (Math.abs(diffNetto) > 0.0001 && Math.abs(diffNetto) <= 0.10);

        //if (!listOut.isEmpty() && (Math.abs(diffVat) <= 0.10 || Math.abs(diffNetto) <= 0.10)) {
        if (!list.isEmpty() && hasSmallDiff) {
            DmsPosition last = list.get(list.size() - 1);

            double vat = parseDoubleSafe(last.vat);
            double net = parseDoubleSafe(last.netto);

            vat += diffVat;
            net += diffNetto;

            last.vat = String.format(Locale.US, "%.2f", vat);
            last.netto = String.format(Locale.US, "%.2f", net);
}
    }

    private List<DmsPosition> extractPositionsDZ(Document doc, DmsParsedDocument out) {

    	    // 1) Pozycje z typ="50"
    	    List<DmsPosition> list = extractPositions50(doc);

    	    // 2) Uzupełnienie z typ="03"
    	    apply03ToPositions(doc, list);

    	    // 3) VAT z typ="06"
    	    applyVatToPositions(out, list);

    	    // 4) Korekty
    	    applyCorrections(out, list);

    	    return list;
    	}

  
    private List<DmsPosition> extractPositions50(Document doc) {
        List<DmsPosition> list = new ArrayList<>();

        NodeList docs = doc.getElementsByTagName("document");
        for (int i = 0; i < docs.getLength(); i++) {
            Element el = (Element) docs.item(i);
            if (!"50".equals(el.getAttribute("typ"))) continue;

            NodeList daneList = el.getElementsByTagName("dane");
            for (int j = 0; j < daneList.getLength(); j++) {
                Element dane = (Element) daneList.item(j);
                Element wart = firstElementByTag(dane, "wartosci");
                Element rozs = firstElementByTag(dane, "rozszerzone");

                DmsPosition p = new DmsPosition();
                p.lp = safeAttr(dane, "lp");
                p.kodVat = safeAttr(dane, "kod_vat");
                p.typDZ = safeAttr(dane, "typ");
                p.jm = safeAttr(dane, "jm");
                p.jmSymb = safeAttr(dane, "jm_symb");
                p.netto = wart != null ? safeAttr(wart, "netto_prup") : "";
                p.cenaNetto = wart != null ? safeAttr(wart, "cena_netto") : "";
                p.opis1 = rozs != null ? safeAttr(rozs, "opis1") : "";

                list.add(p);
            }
        }

        return list;
    }
    private void apply03ToPositions(Document doc, List<DmsPosition> list) {

        NodeList docs = doc.getElementsByTagName("document");
        for (int i = 0; i < docs.getLength(); i++) {
            Element el = (Element) docs.item(i);
            if (!"03".equals(el.getAttribute("typ"))) continue;

            NodeList daneList = el.getElementsByTagName("dane");
            for (int j = 0; j < daneList.getLength(); j++) {
                Element dane = (Element) daneList.item(j);
                Element wart = firstElementByTag(dane, "wartosci");
                Element klas = firstElementByTag(dane, "klasyfikatory");

                String lp = safeAttr(dane, "lp");
                DmsPosition p = findByLp(list, lp);
                if (p == null) continue;

                p.kategoria2 = klas != null ? safeAttr(klas, "kod") : "";
                p.nettoZakup = wart != null ? safeAttr(wart, "netto_zakup") : "";
            }
        }
    }
    private DmsPosition findByLp(List<DmsPosition> list, String lp) {
        if (lp == null || lp.isBlank()) return null;

        for (DmsPosition p : list) {
            if (lp.equals(p.lp)) {
                return p;
            }
        }
        return null;
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
                 double brutto = bruttoStr != null && !bruttoStr.isEmpty() ? parseDoubleSafe(bruttoStr) : 0.0;
                 double netto  = nettoStr != null && !nettoStr.isEmpty() ? parseDoubleSafe(nettoStr) : 0.0;
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

    /*private Element firstElementByTag(Document doc, String tag) {
        NodeList nl = doc.getElementsByTagName(tag);
        if (nl == null || nl.getLength() == 0) return null;
        Node n = nl.item(0);
        return n instanceof Element ? (Element) n : null;
    }*/

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
