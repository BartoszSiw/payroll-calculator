package pl.edashi.dms.parser;

import pl.edashi.dms.model.*;
import pl.edashi.dms.parser.util.DocumentNumberExtractor;
import pl.edashi.common.logging.AppLogger;
import org.w3c.dom.*;

import java.util.*;
import java.util.Locale;

/**
 * Parser dla dokumentów PO (rekordy zamówień / dokumenty PO).
 * Wzorowany na DmsParserDZ / DmsParserDM — zachowuje konwencje aplikacji.
 */
public class DmsParserPO {

    private final AppLogger log = new AppLogger("DmsParserPO");

    public DmsParsedDocument parse(Document doc, String fileName) {
        DmsParsedDocument out = new DmsParsedDocument();
        out.setSourceFileName(fileName);

        Element root = doc.getDocumentElement();
        if (root == null) {
            log.error("ParserPO: brak root w pliku: " + fileName);
            return out;
        }

        // podstawowe atrybuty z root
        String genDocId = root.getAttribute("gen_doc_id");
        String id = root.getAttribute("id");
        String trans = root.getAttribute("trans");

        // element <document> (pierwszy rekord typ="02")
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
            log.warn("ParserPO: błąd przy odczycie numeru z <numer>: " + e.getMessage());
        }

        if (hasNumberInDane) {
            out.setInvoiceNumber(nrFromDane);
            out.setInvoiceShortNumber(DocumentNumberExtractor.normalizeNumber(nrFromDane));
            if (out.getDocumentType() == null || out.getDocumentType().isBlank()) {
                out.setDocumentType("PO");
            }
        }

        // 2) Próba wyciągnięcia numeru z gen_info (jak w DZ/DM)
        boolean found = false;
        try {
            found = DocumentNumberExtractor.extractFromGenInfo(root, out, fileName, hasNumberInDane);
        } catch (Exception e) {
            log.warn("ParserPO: extractFromGenInfo rzucił wyjątek: " + e.getMessage());
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
                    out.setDocumentType("PO");
                }
            } catch (Exception e) {
                log.warn("ParserPO: nie udało się wyciągnąć main number: " + e.getMessage());
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
                log.warn("ParserPO: fallback extractMainNumberFromDmsElement nie powiódł się: " + e.getMessage());
            }
        }

        // 4) Odczyt rejestru i oddziału (szukamy dokumentów typ="02" jak w DZ/DM)
        try {
            NodeList docList = doc.getElementsByTagName("document");
            for (int i = 0; i < docList.getLength(); i++) {
                Element docEl = (Element) docList.item(i);
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
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("ParserPO: nie udało się odczytać rejestru/oddziału: " + ex.getMessage());
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

        // 6) KONTRAHENTY (typ 35) - w PO często są w rozszerzone -> document typ="35"
        try {
            out.setContractor(extractContractor(doc));
        } catch (Exception e) {
            log.warn("ParserPO: nie udało się odczytać kontrahenta: " + e.getMessage());
        }

        // 7) PŁATNOŚCI (typ 40/42) - delegujemy do extractPayments (jak w DZ)
        try {
            out.setPayments(extractPayments(doc, out));
        } catch (Exception e) {
            log.warn("ParserPO: extractPayments rzucił wyjątek: " + e.getMessage());
        }

        // 8) VAT / wartości - PO może mieć <wartosci kwota="..."> w rozszerzone

        // 9) Pozycje: PO często nie ma typowych pozycji 50; jeśli są, można je wyciągnąć analogicznie do DZ.
        // Na razie ustawiamy pustą listę, zachowując spójność
        out.setPositions(Collections.emptyList());

        // 10) Typ analizy
        out.setTypDocAnalizer("PO");

        // Końcowy log
        try {
            log.info(String.format("ParserPO: END file=%s type=%s nr=%s positions=%d",
                    fileName,
                    out.getDocumentType(),
                    out.getInvoiceShortNumber(),
                    out.getPositions() == null ? 0 : out.getPositions().size()));
        } catch (Exception ignored) {
        }

        return out;
    }

    // ---------------------------
    // Metody pomocnicze (delegacje / kopiuj z DmsParserDZ/DM)
    // ---------------------------
    private Element firstElementByTag(Element parent, String tagName) {
        if (parent == null) return null;
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl == null || nl.getLength() == 0) return null;
        Node n = nl.item(0);
        return n instanceof Element ? (Element) n : null;
    }

    private Contractor extractContractor(Document doc) {
        // analogicznie do DmsParserDM/DZ: szukamy document typ="35" w rozszerzone
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

    private List<DmsPayment> extractPayments(Document doc, DmsParsedDocument out) {
        // skopiowane / dostosowane z DmsParserDZ: obsługa typów 40 i 45 oraz zagnieżdżonych 42
        List<DmsPayment> listOut = new ArrayList<>();
        if (doc == null || out == null) return listOut;
        NodeList list = doc.getElementsByTagName("document");

        String terminPlatn = "";
        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);
            String typ = safeAttr(el, "typ");
            String opis = safeAttr(el, "opis");

            if ("40".equals(typ) && opis.toLowerCase(Locale.ROOT).contains("płatności")) {
                NodeList daneList = el.getElementsByTagName("dane");
                for (int j = 0; j < daneList.getLength(); j++) {
                    Element dane = (Element) daneList.item(j);
                    if (dane == null) continue;
                    if (dane.getParentNode() != el) continue;
                    Element wart = firstElementByTag(dane, "wartosci");

                    DmsPayment p = new DmsPayment();
                    p.setIdPlatn(UUID.randomUUID().toString());
                    p.setAdvance(false);
                    String kwota = wart != null ? safeAttr(wart, "kwota") : "";
                    double kw = parseDoubleSafe(kwota);
                    if (kw < 0) { out.setKierunek("przychód"); } else { out.setKierunek("rozchód"); }
                    p.setKierunek(out.getKierunek());
                    kwota = String.format(Locale.US, "%.2f", Math.abs(kw));
                    p.setKwota(kwota);
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

                    listOut.add(p);
                }
            }

            // zaliczki (45) - analogicznie do DZ
            if ("45".equals(typ)) {
                double dSumAdvanceVat = 0;
                double dSumAdvanceNet = 0;
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
                    String bruttoStr = safeAttr(wart, "kwota");
                    String nettoStr = safeAttr(wart, "kwota2"); // w PO może być kwota/kwota2 - dostosuj jeśli trzeba
                    double kw = parseDoubleSafe(bruttoStr);
                    if (kw < 0) { out.setKierunek("rozchód"); } else { out.setKierunek("przychód"); }
                    bruttoStr = String.format(Locale.US, "%.2f", Math.abs(kw));
                    p.setKwota(bruttoStr);
                    double kwn = parseDoubleSafe(nettoStr);
                    dSumAdvanceNet += kwn;
                    nettoStr = String.format(Locale.US, "%.2f", Math.abs(kwn));
                    out.setAdvanceNet(String.format(Locale.US, "%.2f", Math.abs(dSumAdvanceNet)));

                    try {
                        double brutto = parseDoubleSafe(bruttoStr);
                        double netto = parseDoubleSafe(nettoStr);
                        double vatZaliczki = brutto - netto;
                        dSumAdvanceVat += vatZaliczki;
                        String vatZ = String.format(Locale.US, "%.2f", vatZaliczki);
                        p.setVatZ(vatZ);
                        out.setVatZ(vatZ);
                        out.setAdvanceVat(String.format(Locale.US, "%.2f", Math.abs(dSumAdvanceVat)));
                    } catch (Exception ex) {
                        p.setVatZ("0.00");
                        out.setVatZ("0.00");
                    }

                    p.setTermin(terminPlatn != null ? terminPlatn : "");
                    p.setForma("przelew");
                    p.setNrBank("");
                    p.setKierunek(out.getKierunek());
                    listOut.add(p);
                }
            }
        }
        return listOut;
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

