package pl.edashi.dms.parser;

import pl.edashi.dms.model.Contractor;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.model.DmsPayment;
import pl.edashi.dms.model.DocumentMetadata;
import pl.edashi.dms.parser.util.DocumentNumberExtractor;
import pl.edashi.common.logging.AppLogger;
import org.w3c.dom.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Lekki parser dla dokumentów typu DM (rekordy minimalne).
 * Zachowuje kluczowe zachowania i konwencje z DmsParserDZ:
 * - ustawia sourceFileName, metadata, numer dokumentu (jeśli dostępny),
 * - ustawia typ dokumentu na "DM" jako domyślny,
 * - wyciąga oddział i rejestr (jeśli dostępne w rekordzie typ="02"),
 * - wywołuje extractContractor, extractVat, extractPayments (tam gdzie sensowne),
 * - zwraca DmsParsedDocument z pustą listą pozycji, jeśli brak pozycji.
 */
public class DmsParserDM {

    private final AppLogger log = new AppLogger("DmsParserDM");

    public DmsParsedDocument parse(Document doc, String fileName) {
        DmsParsedDocument out = new DmsParsedDocument();
        out.setSourceFileName(fileName);

        Element root = doc.getDocumentElement();
        if (root == null) {
            log.error("ParserDM: brak root w pliku: " + fileName);
            return out;
        }

        // podstawowe atrybuty z root
        String genDocId = root.getAttribute("gen_doc_id");
        String id = root.getAttribute("id");
        String trans = root.getAttribute("trans");

        // element <document> (pierwszy)
        Element document = (Element) doc.getElementsByTagName("document").item(0);
        Element numerEl = null;
        Element daty = (Element) doc.getElementsByTagName("daty").item(0);
        Element wartosci = (Element) doc.getElementsByTagName("wartosci").item(0);

        if (document != null) {
            numerEl = (Element) document.getElementsByTagName("numer").item(0);
        }

        // 1) Numer dokumentu: najpierw z <numer> wewnątrz dokumentu/dane
        String nrFromDane = null;
        boolean hasNumberInDane = false;
        try {
            if (numerEl != null) {
                nrFromDane = DocumentNumberExtractor.extractNumberFromDane(numerEl);
                hasNumberInDane = nrFromDane != null && !nrFromDane.isBlank();
            }
        } catch (Exception e) {
            log.warn("ParserDM: błąd przy odczycie numeru z <numer>: " + e.getMessage());
        }

        if (hasNumberInDane) {
            out.setInvoiceNumber(nrFromDane);
            out.setInvoiceShortNumber(DocumentNumberExtractor.normalizeNumber(nrFromDane));
            if (out.getDocumentType() == null || out.getDocumentType().isBlank()) {
                out.setDocumentType("DM");
            }
        }

        // 2) Próba wyciągnięcia numeru z gen_info (jeśli istnieje) - zachowanie jak w DZ
        boolean found = false;
        try {
            found = DocumentNumberExtractor.extractFromGenInfo(root, out, fileName, hasNumberInDane);
        } catch (Exception e) {
            log.warn("ParserDM: extractFromGenInfo rzucił wyjątek: " + e.getMessage());
        }

        // 3) Fallback: główny numer z elementu DMS (jeśli nie znaleziono wcześniej)
        if (!found || (out.getInvoiceShortNumber() == null && out.getInvoiceNumber() == null)) {
            try {
                String main = DocumentNumberExtractor.extractMainNumberFromDmsElement(root);
                if (main != null && !main.isBlank()) {
                    out.setInvoiceNumber(main);
                    out.setInvoiceShortNumber(DocumentNumberExtractor.normalizeNumber(main));
                }
                if (out.getDocumentType() == null || out.getDocumentType().isBlank()) {
                    out.setDocumentType("DM");
                }
            } catch (Exception e) {
                log.warn("ParserDM: nie udało się wyciągnąć main number: " + e.getMessage());
            }
        }

        // dodatkowy fallback: jeśli nadal brak numeru, spróbuj jeszcze raz extractMainNumberFromDmsElement
        if (out.getInvoiceNumber() == null || out.getInvoiceNumber().isBlank()) {
            try {
                String main = DocumentNumberExtractor.extractMainNumberFromDmsElement(root);
                if (main != null && !main.isBlank()) {
                    out.setInvoiceNumber(main);
                    out.setInvoiceShortNumber(DocumentNumberExtractor.normalizeNumber(main));
                }
            } catch (Exception e) {
                log.warn("ParserDM: fallback extractMainNumberFromDmsElement nie powiódł się: " + e.getMessage());
            }
        }

        // 4) Odczyt rejestru i oddziału (jeśli w dokumencie są rekordy typ="02" jak w DZ)
        try {
            NodeList docList = doc.getElementsByTagName("document");
            for (int i = 0; i < docList.getLength(); i++) {
                Element docEl = (Element) docList.item(i);
                // w DM może być dokument typ="02" z danymi ogólnymi
                if ("02".equals(docEl.getAttribute("typ"))) {
                    Element daneEl = firstElementByTag(docEl, "dane");
                    if (daneEl != null) {
                        String oddzial = daneEl.getAttribute("oddzial");
                        if (oddzial != null) out.setOddzial(oddzial.trim());
                    }
                    Element klas = (Element) docEl.getElementsByTagName("klasyfikatory").item(0);
                    if (klas != null && klas.hasAttribute("wyr")) {
                        String wyrDoc = klas.getAttribute("wyr");
                        if (wyrDoc != null) out.setDaneRejestr(wyrDoc.trim());
                    }
                    break; // zwykle tylko jeden rekord 02
                }
            }
        } catch (Exception ex) {
            log.warn("ParserDM: nie udało się odczytać rejestru/oddziału: " + ex.getMessage());
        }

        // 5) Metadata (bezpiecznie - sprawdzamy czy elementy istnieją)
        String data = daty != null ? daty.getAttribute("data") : null;
        String dataSprzed = daty != null ? daty.getAttribute("data_sprzed") : null;
        String dataZatw = daty != null ? daty.getAttribute("data_zatw") : null;
        String dataOper = daty != null ? daty.getAttribute("data_operacji") : null;
        String waluta = wartosci != null ? wartosci.getAttribute("waluta") : null;

        out.setMetadata(new DocumentMetadata(
                genDocId,
                id,
                trans,
                fileName,
                data,
                dataSprzed,
                dataZatw,
                dataOper,
                waluta
        ));

        /* 6) KONTRAHENT (typ 35) - używamy tej samej metody co w DZ
        try {
            out.setContractor(extractContractor(doc));
        } catch (Exception e) {
            log.warn("ParserDM: nie udało się odczytać kontrahenta: " + e.getMessage());
        }*/

        // 7) Typ analizy i pozycje
        out.setTypDocAnalizer("DM");

        // DM zwykle nie ma pozycji typu 50; zachowujemy spójność z aplikacją i ustawiamy pustą listę
        out.setPositions(Collections.emptyList());

        // Debug log końcowy (zgodnie z konwencją DmsParserDZ)
        try {
            log.info(String.format("ParserDM: END file=%s type=%s nr=%s positions=%d",
                    fileName,
                    out.getDocumentType(),
                    out.getInvoiceShortNumber(),
                    out.getPositions() == null ? 0 : out.getPositions().size()));
        } catch (Exception ignored) {
        }

        return out;
    }

    // ---------------------------
    // Metody pomocnicze (zakładamy, że istnieją w projekcie tak jak w DmsParserDZ)
    // ---------------------------
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
    /**
     * Zwraca pierwszy element potomny o danym tagu wewnątrz parent, lub null.
     * (Analogiczna do firstElementByTag z DmsParserDZ)
     */
    private Element firstElementByTag(Element parent, String tagName) {
        if (parent == null) return null;
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl == null || nl.getLength() == 0) return null;
        Node n = nl.item(0);
        return n instanceof Element ? (Element) n : null;
    }

    private String safeAttr(Element el, String attr) {
        if (el == null) return "";
        String v = el.getAttribute(attr);
        return v == null ? "" : v.trim();
    }
    private double parseDoubleSafe(String s) {
        try { return s != null && !s.isBlank() ? Double.parseDouble(s) : 0.0; }
        catch (Exception e) { return 0.0; }
    }
}

