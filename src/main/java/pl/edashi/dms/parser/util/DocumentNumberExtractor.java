package pl.edashi.dms.parser.util;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pl.edashi.common.logging.AppLogger;
import pl.edashi.dms.model.DmsParsedDocument;

public final class DocumentNumberExtractor {
	private static final org.slf4j.Logger LOG =
	        org.slf4j.LoggerFactory.getLogger(DocumentNumberExtractor.class);
    private DocumentNumberExtractor() { /* util */ }
    // --- istniejące metody (extractFromGenInfo, extractForParserType, looksLikeDocumentNumber, extractMainNumberFromDmsElement)
    // (pozostawiasz je bez zmian) ...

    /**
     * Przeszukuje zagnieżdżone <document> o podanych typach (np. "48","49") i próbuje wyciągnąć numer.
     * targetTypes może być varargs (np. "48","49") lub tablicą.
     * Jeśli znajdzie numer, ustawia invoiceNumber, invoiceShortNumber i documentType w out i zwraca true.
     */
    public static boolean extractFromNestedDocuments(Element dms, DmsParsedDocument out, String... targetTypes) {
        if (dms == null || out == null || targetTypes == null || targetTypes.length == 0) return false;

        NodeList docs = dms.getElementsByTagName("document");
        for (int i = 0; i < docs.getLength(); i++) {
            Element docEl = (Element) docs.item(i);
            if (docEl == null) continue;
            String typ = docEl.getAttribute("typ");
            if (!matchesAny(typ, targetTypes)) continue;

            // przeszukaj <dane> wewnątrz tego document
            NodeList daneNodes = docEl.getElementsByTagName("dane");
            for (int j = 0; j < daneNodes.getLength(); j++) {
                Element dane = (Element) daneNodes.item(j);
                if (dane == null) continue;

                String candidate = extractNumberFromDane(dane);
                if (candidate != null && !candidate.isBlank()) {
                    String normalized = normalizeNumber(candidate);
                    // ustaw pola w out
                    if (out.getDocumentType() == null || out.getDocumentType().isBlank()) {
                        out.setDocumentType(typ);
                    }
                    out.setInvoiceNumber(candidate);
                    out.setInvoiceShortNumber(normalized);
                    return true;
                }
            }
        }
        return false;
    }
    /**
     * Próbuje wyciągnąć numer dokumentu z atrybutu gen_info elementu DMS.
     * Jeśli znajdzie, ustawia documentType, invoiceNumber i invoiceShortNumber w out.
     * Jeśli nie znajdzie, nie modyfikuje out (zwraca false).
     */
    public static boolean extractFromGenInfo(Element dms, DmsParsedDocument out, String sourceFileName, boolean hasNumberInDane) {
        if (dms == null || out == null) return false;

        String info = dms.getAttribute("gen_info");
        LOG.info(info);        
        //LOG.info("[Extractor][gen_info] raw gen_info='" + info + "'");
        if (info == null || info.isBlank()) {
            // fallback: spróbuj ustawić typ z atrybutu DMS id lub z nazwy pliku
            String dmsId = dms.getAttribute("id");
          LOG.info("[NrExtractor][gen_info] 1 dmsId='" + dmsId + "'");
            if (dmsId != null && !dmsId.isBlank()) out.setDocumentType(dmsId.trim().toUpperCase());
            else if (sourceFileName != null) {
            	LOG.info("[NrExtractor][gen_info] 2 dmsId='" + dmsId + "'");
                if (sourceFileName.startsWith("DS")) out.setDocumentType("DS");
                else if (sourceFileName.startsWith("DK")) out.setDocumentType("DK");
            }
            return false;
        }

        String cleaned = info.replace("(", "").replace(")", "").trim();
//LOG.info("[Extractor][gen_info] cleaned='" + cleaned + "'");
        String[] parts = Arrays.stream(cleaned.split(","))
                               .map(String::trim)
                               .toArray(String[]::new);

        // jeśli gen_info ma strukturę z typem i numerem w expected positions
        if (parts.length >= 6) {
            String maybeType = parts[4];
            String maybeNumber = parts[5];
            maybeNumber = stripLeadingIndex(maybeNumber);
            LOG.info("[Extractor][gen_info] maybeType='" + maybeType + "'");
            LOG.info("[Extractor][gen_info] maybeNumber='" + maybeNumber + "'");
            if (maybeType != null && !maybeType.isBlank()) {
            	LOG.info("[Extractor][gen_info] SET documentType='" + maybeType + "'");
                out.setDocumentType(maybeType.trim().toUpperCase());
            }

         // 1) Główna ścieżka: numer w expected position
            if (!hasNumberInDane && maybeNumber != null && !maybeNumber.isBlank()) {
                LOG.info("[Extractor][gen_info] FOUND number='" + maybeNumber + "'");
                String normalized = normalizeNumber(maybeNumber);
                out.setInvoiceNumber(maybeNumber.trim());
                out.setInvoiceShortNumber(normalized);
                return true;
            }

            // 2) Alternatywna ścieżka: regex / looksLikeDocumentNumber
            if (!hasNumberInDane && maybeNumber != null && !maybeNumber.isBlank() && looksLikeDocumentNumber(maybeNumber)) {
                LOG.info("[Extractor][gen_info] FOUND number='" + maybeNumber + "'");
                String normalized = normalizeNumber(maybeNumber);
                out.setInvoiceNumber(maybeNumber.trim());
                out.setInvoiceShortNumber(normalized);
                return true;
            }
        }

        // dodatkowa próba: znajdź pierwszy fragment wyglądający jak numer
        Matcher m = Pattern.compile("(\\b[0-9A-Za-z]+(?: [\\/\\-_][0-9A-Za-z]+)+\\b)").matcher(cleaned);
        if (m.find()) {
            String found = m.group(1).trim();
            //LOG.info("[Extractor][gen_info] REGEX FOUND number='" + found + "'");
            out.setInvoiceNumber(found);
            out.setInvoiceShortNumber(normalizeNumber(found));

            // jeśli typ nadal pusty, spróbuj wywnioskować z prefiksu (FV, Pr itp.) lub z DMS id
            if (out.getDocumentType() == null || out.getDocumentType().isBlank()) {
            	//LOG.info("[Extractor][gen_info] TYPE STILL EMPTY — trying to infer from number");
                String up = cleaned.toUpperCase();
                if (up.contains("FV") || found.toUpperCase().startsWith("FV")) out.setDocumentType("DS");
                else if (up.contains("DK") || sourceFileName != null && sourceFileName.startsWith("DK")) out.setDocumentType("DK");
                else {
                    String dmsId = dms.getAttribute("id");
                    if (dmsId != null && !dmsId.isBlank()) out.setDocumentType(dmsId.trim().toUpperCase());
                }
            }
            return true;
        }
//LOG.info("[Extractor][gen_info] NO MATCH — returning false");
        return false;
    }
    /**
     * Wersja extractForParserType z dodatkowym parametrem sourceFileName (może być null).
     * Zwraca true jeśli udało się ustawić numer (i najlepiej typ) w out.
     */
    public static boolean extractForParserType(Element dms, DmsParsedDocument out, String parserType, String sourceFileName) {
        if (dms == null || out == null || parserType == null) return false;

        String type = parserType.trim().toUpperCase();
        boolean hasNumberInDane = false;
        // 1) dla DS: najpierw gen_info (z możliwością użycia nazwy pliku jako fallback)
        if ("DS".equals(type)) {
            if (extractFromGenInfo(dms, out, sourceFileName,hasNumberInDane)) return true;
            // fallback: spróbuj nested (np. 48/49) lub główny numer
            if (extractFromNestedDocuments(dms, out, "48", "49")) return true;
            String main = extractMainNumberFromDmsElement(dms);
            if (main != null && !main.isBlank()) {
                out.setInvoiceNumber(main);
                out.setInvoiceShortNumber(normalizeNumber(main));
                if (out.getDocumentType() == null || out.getDocumentType().isBlank()) out.setDocumentType("DS");
                return true;
            }
            return false;
        }

        // 2) dla DK: preferujemy numer z zagnieżdżonych dokumentów (48,49)
        if ("DK".equals(type)) {
            if (extractFromNestedDocuments(dms, out, "48", "49")) return true;
            // jeśli nie ma nested, spróbuj gen_info (przekazujemy sourceFileName jeśli mamy)
            if (extractFromGenInfo(dms, out, sourceFileName,hasNumberInDane)) return true;
            String main = extractMainNumberFromDmsElement(dms);
            if (main != null && !main.isBlank()) {
                out.setInvoiceNumber(main);
                out.setInvoiceShortNumber(normalizeNumber(main));
                if (out.getDocumentType() == null || out.getDocumentType().isBlank()) out.setDocumentType("DK");
                return true;
            }
            return false;
        }

        // 3) inne typy: domyślna kolejność — gen_info, nested (48/49), main
        if (extractFromGenInfo(dms, out, sourceFileName,hasNumberInDane)) return true;
        if (extractFromNestedDocuments(dms, out, "48", "49")) return true;

        String main = extractMainNumberFromDmsElement(dms);
        if (main != null && !main.isBlank()) {
            out.setInvoiceNumber(main);
            out.setInvoiceShortNumber(normalizeNumber(main));
            if (out.getDocumentType() == null || out.getDocumentType().isBlank()) out.setDocumentType(type);
            return true;
        }
        return false;
    }
    /**
     * Heurystyka: czy tekst wygląda jak numer dokumentu (zawiera slash i cyfrę).
     */
    public static boolean looksLikeDocumentNumber(String s) {
        if (s == null) return false;
        s = s.trim();
        // proste reguły: zawiera slash i cyfrę, lub format z kilkoma slashami
        return s.matches(".*\\d+\\s*/\\s*\\d+.*") || s.matches(".*\\d{1,4}/\\d{1,4}/.*") || s.matches(".*\\d+/\\d+/\\d+.*");
    }

    /**
     * Fallback: wyciąga <dane>/<numer> z elementu DMS (jeśli parser DK/DS ma dostęp do Elementu).
     * Zwraca pusty string jeśli nie znaleziono.
     */
    public static String extractMainNumberFromDmsElement(Element dms) {
        if (dms == null) return "";
        Element document = firstElementByTag(dms, "document");
        if (document == null) document = dms;
        Element dane = firstElementByTag(document, "dane");
        if (dane == null) return "";
        Element numer = firstElementByTag(dane, "numer");
        if (numer == null) return "";
        String txt = numer.getTextContent();
        return txt != null ? txt.trim() : "";
    }
    // --- pomocnicze metody używane powyżej ---

    private static boolean matchesAny(String value, String[] candidates) {
        if (value == null) return false;
        for (String c : candidates) {
            if (c != null && c.equals(value)) return true;
        }
        return false;
    }

    // próbuje pobrać numer z rozszerzone@opis1, z elementu <numer> (tekst) lub atrybutu nr
    public static String extractNumberFromDane(Element dane) {
        if (dane == null) return "";

        // 1) rozszerzone@opis1
        Element rozs = firstElementByTag(dane, "rozszerzone");
        if (rozs != null) {
            String opis1 = safeAttr(rozs, "opis1");
            if (opis1 != null && !opis1.isBlank()) {
                String extracted = extractNumberFromText(opis1);
                LOG.info("extractNumber extracted: " + extracted);
                if (extracted != null && !extracted.isBlank()) return extracted;
            }
        }

        // 2) bezpośredni <numer> wewnątrz tego <dane>
        Element numer = firstElementByTag(dane, "numer");
        if (numer != null) {
            String attrNr = safeAttr(numer, "nr");
            LOG.info("extractNumber attrNr: " + attrNr);
            if (attrNr != null && !attrNr.isBlank()) return attrNr.trim();
            String txt = numer.getTextContent();
            LOG.info("extractNumber txt: " + txt);
            if (txt != null && !txt.isBlank()) return txt.trim();
        }

        // 3) szukaj dowolnego <numer> głębiej w tym <dane>
        NodeList allNumer = dane.getElementsByTagName("numer");
        for (int n = 0; n < allNumer.getLength(); n++) {
            Element anyNum = (Element) allNumer.item(n);
            if (anyNum == null) continue;

            // 🔥 najpierw atrybut nr=
            String attrNr = safeAttr(anyNum, "nr");
            LOG.info("extractNumber AnyAttrNr: " + attrNr);
            if (attrNr != null && !attrNr.isBlank()) return attrNr.trim();

            // dopiero potem tekst
            String txt = anyNum.getTextContent();
            LOG.info("extractNumber AnyTxt: " + txt);
            if (txt != null && !txt.isBlank()) return txt.trim();
        }

        return "";
    }

    // wyciąga pierwszy pasujący wzorzec numeru z tekstu (np. "Zapłata za: 2/100/01/00119/2025")
    private static String extractNumberFromText(String text) {
        if (text == null) return "";
        // dopuszczamy cyfry, slashe, myślniki i litery (czasem są prefiksy)
        Pattern p = Pattern.compile("(\\d+[\\d/\\-A-Za-z]*\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return text.trim();
    }
    // Zakładamy, że parser ma podobną metodę; jeśli nie, możesz skopiować prosty helper:
    private static Element firstElementByTag(Element parent, String tag) {
        if (parent == null) return null;
        var nl = parent.getElementsByTagName(tag);
        return (nl != null && nl.getLength() > 0) ? (Element) nl.item(0) : null;
    }

    private static String safeAttr(Element el, String name) {
        if (el == null) return "";
        String v = el.getAttribute(name);
        return v != null ? v : "";
    }
    private static String formatWithTypePrefix(String docType, String rawNumber) {
        if (rawNumber == null) return docType != null ? docType : "";
        String n = rawNumber.replaceAll("[()\\,]", "").trim();
        n = n.replaceAll("\\s*/\\s*", "/").replaceAll("\\s+", " ");
        if (docType != null && !docType.isBlank()) {
            return docType + " " + n;
        }
        return n;
    }
    // normalizacja: usuwa prefiksy typu "Zapłata za:", "FV ", "Pr " i zwraca sam numer
    public static String normalizeNumber(String raw) {
        if (raw == null) return "";
        String r = raw.trim();
        r = r.replaceAll("(?i)zapłata za:\\s*", "");
        r = r.replaceAll("(?i)FV\\s*", "");
        r = r.replaceAll("(?i)FZl\\s*", "");
        r = r.replaceAll("(?i)FVk\\s*", "");
        r = r.replaceAll("(?i)Pr\\s*", "");
        r = r.replaceAll("\\s+", " ");
        return r.trim();
    }
    private static String stripLeadingIndex(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        // jeśli zaczyna się od "1 " lub "2 "
        if (trimmed.matches("^[12]\\s+.*")) {
            return trimmed.substring(2).trim();
        }
        return trimmed;
    }
    public static String extractNumberFromNumerElement(Element numer) {
        if (numer == null) return "";

        String attrNr = numer.getAttribute("nr");
        if (attrNr != null && !attrNr.isBlank()) return attrNr.trim();

        String txt = numer.getTextContent();
        if (txt != null && !txt.isBlank()) return txt.trim();

        return "";
    }


}

