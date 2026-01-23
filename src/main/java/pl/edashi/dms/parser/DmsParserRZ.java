package pl.edashi.dms.parser;
import pl.edashi.common.logging.AppLogger;

import org.apache.logging.log4j.util.BiConsumer;
import org.w3c.dom.*;
import pl.edashi.dms.model.*;
import pl.edashi.dms.parser.util.DocumentNumberExtractor;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
//import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

public class DmsParserRZ {
	private final AppLogger log = new AppLogger("DmsParserDS");
    public DmsParsedDocument parse(Document doc, String fileName) {
        DmsParsedDocument out = new DmsParsedDocument();
        out.setSourceFileName(fileName);
        // ============================
        // 1. METADATA
        // ============================
        Element root = doc.getDocumentElement();
        String genDocId = root.getAttribute("gen_doc_id");
        String id = root.getAttribute("id");
        String trans = root.getAttribute("trans");
        Element daty = (Element) doc.getElementsByTagName("daty").item(0);
        Element warto = (Element) doc.getElementsByTagName("wartosci").item(0);
     // preferowana, centralna metoda ekstrakcji dla parsera DS
        
        boolean found = DocumentNumberExtractor.extractFromGenInfo(root, out, fileName, false);
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
        	if ("RWS".equals(out.getDocumentType())) {

        	out.setDocumentWewne("Tak");
        	
        	out.setUwzglProp("Nie");
        } else {
        	out.setDocumentWewne("Nie");
        }
        try {
            NodeList docList = doc.getElementsByTagName("document");
            for (int i = 0; i < docList.getLength(); i++) {
                Element docEl = (Element) docList.item(i);
                if ("02".equals(docEl.getAttribute("typ"))) {
                    Element daneEl = firstElementByTag(docEl, "dane");
                    if (daneEl != null && daneEl.hasAttribute("punkt_sprzed")) {
                        String punktSprzed = daneEl.getAttribute("punkt_sprzed").trim();
                        String oddzial = daneEl.getAttribute("oddzial").trim();
                        out.setDaneRejestr(punktSprzed); // upewnij się, że DmsParsedDocument ma setter
                    	out.setOddzial(oddzial);}
                    break; // zwykle tylko jeden rekord 02
                }
            }
        } catch (Exception ex) {
            log.warn("ParserDS: nie udało się odczytać punkt_sprzed: " + ex.getMessage());
        }
        // debug (tymczasowo) — pokaże co mamy po ekstrakcji
        //log.info(String.format("Parser DS: extracted documentType='%s', invoiceShort='%s', file=%s",
        out.setMetadata(new DocumentMetadata(
                genDocId,
                id,
                trans,
                fileName,
                daty.getAttribute("data"),
                daty.getAttribute("data_sprzed"),
                daty.getAttribute("data_zatw"),
                daty.getAttribute("data"),
                warto.getAttribute("waluta")
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
        // 3. VAT (typ 06)
        // ============================
        //extractVat(doc, out);
        // ============================
        // 4. PŁATNOŚCI (typ 40 + 43)
        // ============================
        out.setPayments(extractPayments(doc, out));
        // ============================
        // 5. POZYCJE (typ 03)
        // ============================
        String defaultVatRate = out.getVatRate();
        if (defaultVatRate == null) defaultVatRate = "";
        out.setPositions(extractPositionsRZ(doc, out));

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
    private List<DmsPosition> extractPositionsRZ(Document doc, DmsParsedDocument out) {
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
                String kwota = wart != null ? safeAttr(wart, "kwota") : "";
             // zawsze dodatnia kwota płatności
                double kw = parseDoubleSafe(kwota);
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
                p.setKierunek(out.getKierunek());

                //p.setKierunek("przychód");
                listOut.add(p);
                }
            }
         // ------------------------------
         // ZALICZKI (typ 45) - iteruj po wszystkich <dane>
         // ------------------------------
         // ------------------------------
         // ZALICZKI (typ 45) - iteruj po wszystkich <dane> i sumuj zaliczki
         // ------------------------------
         if ("45".equals(typ)) {
             NodeList daneList45 = el.getElementsByTagName("dane");
             BigDecimal totalAdvanceNet = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
             BigDecimal totalAdvanceVat = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

             for (int j = 0; j < daneList45.getLength(); j++) {
                 Element dane = (Element) daneList45.item(j);
                 if (dane == null) continue;
                 // bierzemy TYLKO <dane>, których bezpośrednim rodzicem jest <document typ="45">
                 if (dane.getParentNode() != el) continue;

                 Element wart = firstElementByTag(dane, "wartosci");
                 if (wart == null) continue;

                 String lp = safeAttr(dane, "lp");
                 String bruttoStr = safeAttr(wart, "brutto");
                 String nettoStr = safeAttr(wart, "netto");

                 // defensywne parsowanie
                 BigDecimal bruttoBd = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                 BigDecimal nettoBd = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                 try {
                     bruttoBd = new BigDecimal(bruttoStr == null || bruttoStr.isBlank() ? "0.00" : bruttoStr.replace(",", "."))
                             .setScale(2, RoundingMode.HALF_UP).abs();
                 } catch (Exception ex) { /* zostaw 0.00 */ }
                 try {
                     nettoBd = new BigDecimal(nettoStr == null || nettoStr.isBlank() ? "0.00" : nettoStr.replace(",", "."))
                             .setScale(2, RoundingMode.HALF_UP).abs();
                 } catch (Exception ex) { /* zostaw 0.00 */ }

                 BigDecimal vatBd = bruttoBd.subtract(nettoBd).setScale(2, RoundingMode.HALF_UP);

                 // utwórz wpis płatności dla tej zaliczki (oddzielny rekord)
                 DmsPayment p = new DmsPayment();
                 p.setIdPlatn(UUID.randomUUID().toString());
                 p.setKwota(String.format(Locale.US, "%.2f", bruttoBd.doubleValue()));
                 p.setVatZ(String.format(Locale.US, "%.2f", vatBd.doubleValue()));
                 p.setTermin(terminPlatn != null ? terminPlatn : "");
                 p.setForma("przelew");
                 p.setNrBank("");
                 p.setKierunek(out.getKierunek());
                 listOut.add(p);

                 // sumuj zaliczki (jeśli chcesz, żeby out zawierał sumę wszystkich zaliczek)
                 totalAdvanceNet = totalAdvanceNet.add(nettoBd);
                 totalAdvanceVat = totalAdvanceVat.add(vatBd);

                 log.info(String.format("extractPayments: found advance lp=%s brutto=%s netto=%s vat=%s",
                         lp, bruttoBd.toPlainString(), nettoBd.toPlainString(), vatBd.toPlainString()));
             }

             // ustaw łączną zaliczkę w out (nie nadpisujemy pojedynczo — ustawiamy sumę)
             if (totalAdvanceNet.compareTo(BigDecimal.ZERO) > 0 || totalAdvanceVat.compareTo(BigDecimal.ZERO) > 0) {
                 out.setAdvanceNet(String.format(Locale.US, "%.2f", totalAdvanceNet.doubleValue()));
                 out.setAdvanceVat(String.format(Locale.US, "%.2f", totalAdvanceVat.doubleValue()));
                 // opcjonalnie ustaw też out.setVatZ(...) jeśli używasz tego pola globalnie
                 out.setVatZ(String.format(Locale.US, "%.2f", totalAdvanceVat.doubleValue()));
                 log.info(String.format("extractPayments: total advances summed: advanceNet=%s advanceVat=%s",
                         totalAdvanceNet.toPlainString(), totalAdvanceVat.toPlainString()));
             }
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
   
////////////////////
 // zbiory typów - DZ usuwamy z DS_TYPES i obsługujemy osobno
 
}