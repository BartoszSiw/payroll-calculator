package pl.edashi.dms.parser;

import pl.edashi.dms.model.*;
import pl.edashi.dms.parser.DmsParserRD;
import pl.edashi.dms.parser.util.DocumentNumberExtractor;
import pl.edashi.common.logging.AppLogger;
import org.w3c.dom.*;

import java.util.*;

/**
 * Parser dla dokumentów ZC (zmiana wartości pojazdu i powiązane dokumenty).
 * Wzorzec zgodny z DmsParserDZ/DM/PO/PZ/RD/RO.
 */
public class DmsParserZC {

    private final AppLogger log = new AppLogger("DmsParserZC");

    public DmsParsedDocument parse(Document doc, String fileName) {
        DmsParsedDocument out = new DmsParsedDocument();
        out.setSourceFileName(fileName);

        Element root = doc.getDocumentElement();
        if (root == null) {
            log.error("ParserZC: brak root w pliku: " + fileName);
            return out;
        }

        // meta z root
        String genDocId = root.getAttribute("gen_doc_id");
        String id = root.getAttribute("id");
        String trans = root.getAttribute("trans");

        // pierwszy dokument (typ="02")
        Element document = (Element) doc.getElementsByTagName("document").item(0);
        Element numerEl = null;
        Element daty = (Element) doc.getElementsByTagName("daty").item(0);
        Element wartosci = (Element) doc.getElementsByTagName("wartosci").item(0);

        if (document != null) {
            numerEl = (Element) document.getElementsByTagName("numer").item(0);
        }

        // 1) Numer dokumentu
        String nrFromDane = null;
        boolean hasNumberInDane = false;
        try {
            if (numerEl != null) {
                nrFromDane = DocumentNumberExtractor.extractNumberFromDane(numerEl);
                hasNumberInDane = nrFromDane != null && !nrFromDane.isBlank();
            }
        } catch (Exception e) {
            log.warn("ParserZC: błąd przy odczycie numeru z <numer>: " + e.getMessage());
        }

        if (hasNumberInDane) {
            out.setInvoiceNumber(nrFromDane);
            out.setInvoiceShortNumber(DocumentNumberExtractor.normalizeNumber(nrFromDane));
            if (out.getDocumentType() == null || out.getDocumentType().isBlank()) {
                out.setDocumentType("ZC");
            }
        }

        // 2) extractFromGenInfo
        boolean found = false;
        try {
            found = DocumentNumberExtractor.extractFromGenInfo(root, out, fileName, hasNumberInDane);
        } catch (Exception e) {
            log.warn("ParserZC: extractFromGenInfo rzucił wyjątek: " + e.getMessage());
        }

        // 3) fallback main number
        if (!found || (out.getInvoiceShortNumber() == null && out.getInvoiceNumber() == null)) {
            try {
                String main = DocumentNumberExtractor.extractMainNumberFromDmsElement(root);
                if (main != null && !main.isBlank()) {
                    out.setInvoiceNumber(main);
                    out.setInvoiceShortNumber(DocumentNumberExtractor.normalizeNumber(main));
                }
                if (out.getDocumentType() == null || out.getDocumentType().isBlank()) {
                    out.setDocumentType("ZC");
                }
            } catch (Exception e) {
                log.warn("ParserZC: nie udało się wyciągnąć main number: " + e.getMessage());
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
                log.warn("ParserZC: fallback extractMainNumberFromDmsElement nie powiódł się: " + e.getMessage());
            }
        }

        // 4) rejestr, oddział, dział, jedn_org
        try {
            NodeList docList = doc.getElementsByTagName("document");
            for (int i = 0; i < docList.getLength(); i++) {
                Element docEl = (Element) docList.item(i);
                if ("02".equals(docEl.getAttribute("typ"))) {
                    Element daneEl = firstElementByTag(docEl, "dane");
                    if (daneEl != null) {
                        String oddzial = daneEl.getAttribute("oddzial");
                        if (oddzial != null) out.setOddzial(oddzial.trim());
                        String dzial = daneEl.getAttribute("dzial");
                        if (dzial != null && !dzial.isBlank()) out.setDzial(dzial.trim());
                        String punktSprzed = daneEl.getAttribute("jedn_org");
                        if (punktSprzed != null && !punktSprzed.isBlank()) out.setDaneRejestr(punktSprzed.trim());
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
            log.warn("ParserZC: nie udało się odczytać rejestru/oddziału: " + ex.getMessage());
        }

        // 5) metadata
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

        // 6) kontrahent (typ 35) - jeśli występuje
        try {
            out.setContractor(extractContractor(doc));
        } catch (Exception e) {
            log.warn("ParserZC: nie udało się odczytać kontrahenta: " + e.getMessage());
        }

        // 7) VAT / wartości / płatności

        // 8) Pozycje: typ 22/23/21/61/80/93/98 — zbieramy pozycje i opisy tam, gdzie występują
        try {
            List<DmsPosition> positions = new ArrayList<>();
            positions.addAll(extractPositionsFromTyp22(doc)); // pozycje typ=22
            positions.addAll(extractPositionsFromTyp23(doc)); // pozycje typ=23
            positions.addAll(extractPositionsFromTyp49(doc)); // jeśli występują zagnieżdżone 49
            out.setPositions(positions.isEmpty() ? Collections.emptyList() : positions);
        } catch (Exception e) {
            log.warn("ParserZC: extractPositions rzucił wyjątek: " + e.getMessage());
            out.setPositions(Collections.emptyList());
        }

        // 9) typ analizy
        out.setTypDocAnalizer("ZC");

        // końcowy log
        try {
            log.info(String.format("ParserZC: END file=%s type=%s nr=%s positions=%d",
                    fileName,
                    out.getDocumentType(),
                    out.getInvoiceShortNumber(),
                    out.getPositions() == null ? 0 : out.getPositions().size()));
        } catch (Exception ignored) {
        }

        return out;
    }

    // ---------------------------
    // Wyciąganie pozycji typ=22 (pozycje dokumentów)
    // ---------------------------
    private List<DmsPosition> extractPositionsFromTyp22(Document doc) {
        List<DmsPosition> positions = new ArrayList<>();
        NodeList docList = doc.getElementsByTagName("document");
        for (int i = 0; i < docList.getLength(); i++) {
            Element docEl = (Element) docList.item(i);
            if ("22".equals(docEl.getAttribute("typ"))) {
                NodeList daneList = docEl.getElementsByTagName("dane");
                for (int j = 0; j < daneList.getLength(); j++) {
                    Element dane = (Element) daneList.item(j);
                    if (dane == null) continue;
                    if (dane.getParentNode() != docEl) continue;

                    DmsPosition pos = new DmsPosition();
                    pos.setLp(safeAttr(dane, "lp"));

                    Element klasyf = firstElementByTag(dane, "klasyfikatory");
                    if (klasyf != null) {
                        pos.setKlasyfikacja(safeAttr(klasyf, "klasyfikacja"));
                        pos.setKodKlasyfikatora(safeAttr(klasyf, "kod"));
                    }

                    Element wart = firstElementByTag(dane, "wartosci");
                    if (wart != null) {
                        String kw = safeAttr(wart, "kwota");
                        pos.setBrutto(String.format(Locale.US, "%.2f", parseDoubleSafe(kw)));
                        pos.setNetto(String.format(Locale.US, "%.2f", parseDoubleSafe(kw)));
                        pos.setVat(String.format(Locale.US, "%.2f", 0.0));
                    } else {
                        pos.setBrutto("0.00");
                        pos.setNetto("0.00");
                        pos.setVat("0.00");
                    }

                    positions.add(pos);
                }
            }
        }
        return positions;
    }

    // ---------------------------
    // Wyciąganie pozycji typ=23 (doposażenia) — podobne do typ=22, ale z numerem i dodatkowymi atrybutami
    // ---------------------------
    private List<DmsPosition> extractPositionsFromTyp23(Document doc) {
        List<DmsPosition> positions = new ArrayList<>();
        NodeList docList = doc.getElementsByTagName("document");
        for (int i = 0; i < docList.getLength(); i++) {
            Element docEl = (Element) docList.item(i);
            if ("23".equals(docEl.getAttribute("typ"))) {
                NodeList daneList = docEl.getElementsByTagName("dane");
                for (int j = 0; j < daneList.getLength(); j++) {
                    Element dane = (Element) daneList.item(j);
                    if (dane == null) continue;
                    if (dane.getParentNode() != docEl) continue;

                    DmsPosition pos = new DmsPosition();
                    pos.setLp(safeAttr(dane, "lp"));

                    Element numerEl = firstElementByTag(dane, "numer");
                    if (numerEl != null) {
                        String numText = numerEl.getTextContent();
                        pos.setNumer(numText != null ? numText.trim() : "");
                    }

                    Element klasyf = firstElementByTag(dane, "klasyfikatory");
                    if (klasyf != null) {
                        pos.setKlasyfikacja(safeAttr(klasyf, "wyr"));
                        pos.setKodKlasyfikatora(safeAttr(klasyf, "kod"));
                    }

                    Element wart = firstElementByTag(dane, "wartosci");
                    if (wart != null) {
                        String kw = safeAttr(wart, "kwota");
                        pos.setBrutto(String.format(Locale.US, "%.2f", parseDoubleSafe(kw)));
                        pos.setNetto(String.format(Locale.US, "%.2f", parseDoubleSafe(kw)));
                        pos.setVat(String.format(Locale.US, "%.2f", 0.0));
                    } else {
                        pos.setBrutto("0.00");
                        pos.setNetto("0.00");
                        pos.setVat("0.00");
                    }

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

    // ---------------------------
    // Reużywane metody pomocnicze i delegacje
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

    private Element firstElementByTag(Element parent, String tagName) {
        if (parent == null) return null;
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl == null || nl.getLength() == 0) return null;
        Node n = nl.item(0);
        return n instanceof Element ? (Element) n : null;
    }

    private Contractor extractContractor(Document doc) {
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
