package pl.edashi.dms.parser;

import pl.edashi.dms.model.*;
import pl.edashi.dms.parser.util.DocumentNumberExtractor;
import pl.edashi.common.logging.AppLogger;
import org.w3c.dom.*;

import java.util.*;

/**
 * Parser dla dokumentów RD (dowód kasowy / wartościowy).
 * Wzorowany na DmsParserDZ/DM/PO/PZ — bezpieczne odczyty, fallbacky, logi.
 */
public class DmsParserRD {

    private final AppLogger log = new AppLogger("DmsParserRD");

    public DmsParsedDocument parse(Document doc, String fileName) {
        DmsParsedDocument out = new DmsParsedDocument();
        out.setSourceFileName(fileName);

        Element root = doc.getDocumentElement();
        if (root == null) {
            log.error("ParserRD: brak root w pliku: " + fileName);
            return out;
        }

        // podstawowe atrybuty z root
        String genDocId = root.getAttribute("gen_doc_id");
        String id = root.getAttribute("id");
        String trans = root.getAttribute("trans");

        // pierwszy element document (typ="02" - rekord ogólny)
        Element document = (Element) doc.getElementsByTagName("document").item(0);
        Element numerEl = null;
        Element daty = (Element) doc.getElementsByTagName("daty").item(0);
        Element wartosci = (Element) doc.getElementsByTagName("wartosci").item(0);

        if (document != null) {
            numerEl = (Element) document.getElementsByTagName("numer").item(0);
        }

        // 1) Numer dokumentu: najpierw z <numer>
        String nrFromDane = null;
        boolean hasNumberInDane = false;
        try {
            if (numerEl != null) {
                nrFromDane = DocumentNumberExtractor.extractNumberFromDane(numerEl);
                hasNumberInDane = nrFromDane != null && !nrFromDane.isBlank();
            }
        } catch (Exception e) {
            log.warn("ParserRD: błąd przy odczycie numeru z <numer>: " + e.getMessage());
        }

        if (hasNumberInDane) {
            out.setInvoiceNumber(nrFromDane);
            out.setInvoiceShortNumber(DocumentNumberExtractor.normalizeNumber(nrFromDane));
            if (out.getDocumentType() == null || out.getDocumentType().isBlank()) {
                out.setDocumentType("RD");
            }
        }

        // 2) Próba wyciągnięcia numeru z gen_info
        boolean found = false;
        try {
            found = DocumentNumberExtractor.extractFromGenInfo(root, out, fileName, hasNumberInDane);
        } catch (Exception e) {
            log.warn("ParserRD: extractFromGenInfo rzucił wyjątek: " + e.getMessage());
        }

        // 3) Fallback: główny numer z elementu DMS
        if (!found || (out.getInvoiceShortNumber() == null && out.getInvoiceNumber() == null)) {
            try {
                String main = DocumentNumberExtractor.extractMainNumberFromDmsElement(root);
                if (main != null && !main.isBlank()) {
                    out.setInvoiceNumber(main);
                    out.setInvoiceShortNumber(DocumentNumberExtractor.normalizeNumber(main));
                }
                if (out.getDocumentType() == null || out.getDocumentType().isBlank()) {
                    out.setDocumentType("RD");
                }
            } catch (Exception e) {
                log.warn("ParserRD: nie udało się wyciągnąć main number: " + e.getMessage());
            }
        }

        // dodatkowy fallback
        if (out.getInvoiceNumber() == null || out.getInvoiceNumber().isBlank()) {
            try {
                String main = DocumentNumberExtractor.extractMainNumberFromDmsElement(root);
                if (main != null && !main.isBlank()) {
                    out.setInvoiceNumber(main);
                    out.setInvoiceShortNumber(DocumentNumberExtractor.normalizeNumber(main));
                }
            } catch (Exception e) {
                log.warn("ParserRD: fallback extractMainNumberFromDmsElement nie powiódł się: " + e.getMessage());
            }
        }

        // 4) Odczyt rejestru i oddziału (szukamy dokumentów typ="02")
        try {
            NodeList docList = doc.getElementsByTagName("document");
            for (int i = 0; i < docList.getLength(); i++) {
                Element docEl = (Element) docList.item(i);
                if ("02".equals(docEl.getAttribute("typ"))) {
                    Element daneEl = firstElementByTag(docEl, "dane");
                    if (daneEl != null) {
                        String oddzial = daneEl.getAttribute("oddzial");
                        if (oddzial != null) out.setOddzial(oddzial.trim());
                        String kasa = daneEl.getAttribute("kasa");
                        if (kasa != null && !kasa.isBlank()) out.setKasa(kasa.trim());
                    }
                    Element klas = (Element) docEl.getElementsByTagName("klasyfikatory").item(0);
                    if (klas != null && klas.hasAttribute("wyr")) {
                        String wyrDoc = klas.getAttribute("wyr");
                        if (wyrDoc != null) out.setDaneRejestr(wyrDoc.trim());
                    }
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("ParserRD: nie udało się odczytać rejestru/oddziału: " + ex.getMessage());
        }

        // 5) Metadata
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

        // 6) KONTRAHENT (jeśli jest typ 35) - delegacja
        try {
            out.setContractor(extractContractor(doc));
        } catch (Exception e) {
            log.warn("ParserRD: nie udało się odczytać kontrahenta: " + e.getMessage());
        }

        // 7) VAT / wartości / płatności

        // 8) Pozycje: w RD pozycje mogą być w zagnieżdżonym dokumencie typ="49"
        try {
            List<DmsPosition> positions = extractPositionsFromTyp49(doc);
            out.setPositions(positions == null ? Collections.emptyList() : positions);
        } catch (Exception e) {
            log.warn("ParserRD: extractPositionsFromTyp49 rzucił wyjątek: " + e.getMessage());
            out.setPositions(Collections.emptyList());
        }

        // 9) Typ analizy
        out.setTypDocAnalizer("RD");

        // Końcowy log
        try {
            log.info(String.format("ParserRD: END file=%s type=%s nr=%s positions=%d",
                    fileName,
                    out.getDocumentType(),
                    out.getInvoiceShortNumber(),
                    out.getPositions() == null ? 0 : out.getPositions().size()));
        } catch (Exception ignored) {
        }

        return out;
    }

    // ---------------------------
    // Wyciąganie pozycji z dokumentów typ="49" (zagnieżdżone w rozszerzone)
    // ---------------------------
    private List<DmsPosition> extractPositionsFromTyp49(Document doc) {
        List<DmsPosition> positions = new ArrayList<>();
        if (doc == null) return positions;

        NodeList docList = doc.getElementsByTagName("document");
        for (int i = 0; i < docList.getLength(); i++) {
            Element docEl = (Element) docList.item(i);
            // szukamy dokumentów typ="49" (pozycje dowodu)
            if ("49".equals(docEl.getAttribute("typ"))) {
                NodeList daneList = docEl.getElementsByTagName("dane");
                for (int j = 0; j < daneList.getLength(); j++) {
                    Element dane = (Element) daneList.item(j);
                    if (dane == null) continue;
                    // upewnij się, że to bezpośredni child dokumentu typ=49
                    if (dane.getParentNode() != docEl) continue;

                    DmsPosition pos = new DmsPosition();
                    pos.setLp(safeAttr(dane, "lp"));

                    // numer wewnętrzny (może być w <numer>)
                    Element numerEl = firstElementByTag(dane, "numer");
                    if (numerEl != null) {
                        String numText = numerEl.getTextContent();
                        pos.setNumer(numText != null ? numText.trim() : "");
                    }

                    // klasyfikatory
                    Element klasyf = firstElementByTag(dane, "klasyfikatory");
                    if (klasyf != null) {
                        pos.setKlasyfikacja(safeAttr(klasyf, "klasyfikacja"));
                        pos.setKodKlasyfikatora(safeAttr(klasyf, "kod"));
                    }

                    // wartosci (kwota)
                    Element wart = firstElementByTag(dane, "wartosci");
                    if (wart != null) {
                        String kw = safeAttr(wart, "kwota");
                        pos.setBrutto(String.format(Locale.US, "%.2f", parseDoubleSafe(kw)));
                        // w RD zwykle brak netto/vat — ustaw netto=brutto, vat=0
                        pos.setNetto(String.format(Locale.US, "%.2f", parseDoubleSafe(kw)));
                        pos.setVat(String.format(Locale.US, "%.2f", 0.0));
                    } else {
                        pos.setBrutto("0.00");
                        pos.setNetto("0.00");
                        pos.setVat("0.00");
                    }

                    // rozszerzone (opis)
                    Element rozs = firstElementByTag(dane, "rozszerzone");
                    if (rozs != null) {
                        pos.setOpis(safeAttr(rozs, "opis1"));
                    }

                    positions.add(pos);
                }
            }
        }
        return positions;
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
    // ---------------------------
    // Delegacje i pomocnicze
    // ---------------------------
    private Element firstElementByTag(Element parent, String tagName) {
        if (parent == null) return null;
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl == null || nl.getLength() == 0) return null;
        Node n = nl.item(0);
        return n instanceof Element ? (Element) n : null;
    }

    private Contractor extractContractor(Document doc) {
        // analogicznie do innych parserów: szukamy document typ="35"
        NodeList list = doc.getElementsByTagName("document");
        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);
            if ("35".equals(el.getAttribute("typ"))) {
                Contractor c = new Contractor();
                Element dane = (Element) el.getElementsByTagName("dane").item(0);
                if (dane == null) return null;
                Element rozs = (Element) dane.getElementsByTagName("rozszerzone").item(0);
                if (rozs == null) return null;
                c.id = safeAttr(rozs, "kod_klienta");
                c.nip = safeAttr(rozs, "nip");
                c.name1 = safeAttr(rozs, "nazwa1");
                c.name2 = safeAttr(rozs, "nazwa2");
                c.name3 = safeAttr(rozs, "nazwa3");
                c.country = safeAttr(rozs, "kod_kraju");
                c.city = safeAttr(rozs, "miejscowosc");
                c.zip = safeAttr(rozs, "kod_poczta");
                c.street = safeAttr(rozs, "ulica");
                return c;
            }
        }
        return null;
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

