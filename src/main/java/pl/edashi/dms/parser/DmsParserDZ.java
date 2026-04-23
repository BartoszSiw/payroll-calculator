package pl.edashi.dms.parser;
import pl.edashi.common.util.MappingIdDocs;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.common.util.MappingIdDocs;
import pl.edashi.dms.model.Contractor;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.model.DmsParsedDocument.DmsVatEntry;
import pl.edashi.dms.model.DmsPayment;
import pl.edashi.dms.model.DmsPosition;
import pl.edashi.dms.model.DocumentMetadata;
import pl.edashi.dms.model.MappingTarget;
import pl.edashi.dms.parser.util.DocumentNumberExtractor; // zakładam lokalizację helpera
import pl.edashi.converter.servlet.DocTypeConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class DmsParserDZ implements DmsParser{
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
        String bruttoUmowa = warto.getAttribute("brutto");
        String rejestrDZ = "";
        /*if (root == null) {
            log.error("ParserDZ: brak root w pliku: " + fileName);
            return out;
        }*/
        //log.info("DEBUG dane element = " + numer.getElementsByTagName("numer"));
        //log.info("DEBUG numer count = " + numer.getAttribute("nr"));
        //log.info("DEBUG numer count = " + numer.getAttribute("nr_ksef"));
        String nrKsef = numer.getAttribute("nr_ksef");
        out.setNrKsef(nrKsef);
        //log.info("DEBUG numer count = " + nrKsef);
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
        boolean found = DocumentNumberExtractor.extractFromGenInfo(root, out, fileName,hasNumberInDane);
        // gen_info bywa niekonsekwentne (np. potrafi zwrócić "DS" dla zakupów).
        // Ponieważ ten parser jest uruchamiany tylko dla dokumentów zakupowych (root id="DZ"),
        // akceptujemy tylko typy z grupy DZ; w pozostałych przypadkach wymuszamy DZ.
        String parsedType = out.getDocumentType() == null ? "" : out.getDocumentType().trim().toUpperCase();
        if (!parsedType.isBlank() && !DocTypeConstants.DZ_TYPES.contains(parsedType)) {
            out.setDocumentType("DZ");
        }
        
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
                	Element daneEl = firstElementByTag(docEl, "dane");
                	String oddzial = daneEl.getAttribute("oddzial").trim();
                    Element klas = (Element) docEl.getElementsByTagName("klasyfikatory").item(0); 
                    if (klas!= null && klas.hasAttribute("wyr")) {
                        String wyrDoc = klas != null ? klas.getAttribute("wyr") : "";
                        // wyrDoc = "EX"
                        rejestrDZ = wyrDoc;
                        out.setDaneRejestr(wyrDoc);} // upewnij się, że DmsParsedDocument ma setter
                    out.setOddzial(oddzial);
                    break; // zwykle tylko jeden rekord 02
                }
            }
        } catch (Exception ex) {
            log.warn("ParserDS: nie udało się odczytać rejestru zakupu: " + ex.getMessage());
        }
        String dataSprzed = daty.getAttribute("data_sprzed");
        String dataDok = daty.getAttribute("data_dok");

        // jeśli data_sprzed jest null, pusta lub "0" → użyj data_dok
        String finalDataSprzed = (dataSprzed == null || dataSprzed.isBlank()) 
                ? dataDok 
                : dataSprzed;
        out.setMetadata(new DocumentMetadata(
                genDocId,
                id,
                trans,
                fileName,
                daty.getAttribute("data"),
                finalDataSprzed,
                daty.getAttribute("data_zatw"),
                daty.getAttribute("data_operacji"),
                warto.getAttribute("waluta")
        ));
        Element dms = (Element) doc.getElementsByTagName("DMS").item(0);
        // ============================
        // 2. KONTRAHENT (typ 35)
        // ============================
        Contractor c = extractContractor(doc);
        out.setContractor(c);
        log.info(String.format("ParserDZ 1: END expKraj=%s ", out.getContractor().expKrajowy));
        // ============================
        // 3. DMS (typ dokumentu)
        // ============================
        //extractDocumentNumberFromGenInfo(dms, out); 
        out.setTypDocAnalizer("DZ");
        // 3. POZYCJE (typ 50)
        // ============================
        extractVat(doc, out);
        out.setPositions(extractPositionsDZ(doc, out,bruttoUmowa,rejestrDZ));
        List<DmsPosition> list75 = createPositionsFrom75(doc);
        out.setDodatkowyOpis(positionsToOpis(list75));
        out.setVin(firstNonBlankVin(list75));
        // 2) Wartości stawka / netto / vat (document typ="06")
        // ============================

        // ============================
        // 4. PŁATNOŚCI (typ 40 + 43)
        // ============================
        out.setPayments(extractPayments(doc, out));
        //String defaultVatRate = out.getVatRate();
        //if (defaultVatRate == null) defaultVatRate = "";
        String podmiot = "";

        if (c != null) {
            if (c.getNip() != null && !c.getNip().isBlank()) {
                podmiot = c.getNip();
            } else {
                podmiot = c.getFullName() == null ? "" : c.getFullName();
            }
            // jeśli masz setter, użyj go; jeśli nie, odkomentuj bezpośredni dostęp
            c.setPodmiot(podmiot);
            // c.podmiot = podmiot;
        } else {
            podmiot = "";
        }

        out.setContractor(c);
        out.setPodmiot(podmiot);
        log.info(String.format("ParserDZ: END expKraj=%s ", out.getContractor().expKrajowy));
        /*log.info(String.format(Locale.US,
                "ParserDZ: END expKraj=%s file=%s type=%s nr=%s positions=%d", out.getContractor().expKrajowy,
                fileName, out.getDocumentType(), out.getInvoiceShortNumber(), out.getPositions().size()));*/
        String numerFa = out.getInvoiceNumber();
        String nrIdPlat = MappingIdDocs.generateCandidate(podmiot, "D", numerFa, 36);
        String fullKey = MappingIdDocs.buildFullKey(podmiot, numerFa);
        String hash = MappingIdDocs.shortHashFromFullKey(fullKey, 6);
        String docKey = MappingIdDocs.generateDocId(podmiot, "Z" ,numerFa, 36);

        out.setFullKey(fullKey);
        out.setDocKey(docKey);
        out.setNrIdPlat(nrIdPlat);
        out.setHash(hash);
        List<DmsPayment> payId = out.getPayments();
        if (payId != null && !payId.isEmpty()) {
            payId.get(0).setIdPlatn(nrIdPlat); // setIdPlatn zwraca void — wykonaj to osobno
        }
        String code = "Z";
        out.setMappingTarget(MappingTarget.fromCode(code));
        return out;
    }

    private String firstNonBlankVin(List<DmsPosition> positions) {
        if (positions == null || positions.isEmpty()) return "";
        for (DmsPosition p : positions) {
            if (p == null) continue;
            String vin = p.getVin();
            if (vin != null && !vin.isBlank()) return vin.trim();
        }
        return "";
    }

    // ------------------------------
    // VAT (typ 06)
    // ------------------------------
    private void extractVat(Document doc, DmsParsedDocument out) {
        if (doc == null || out == null) return;
        NodeList list = doc.getElementsByTagName("document");
        boolean foundVat = false;
        String kodVat = "";
        String stawka = "";
        String statusVat="";
        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("06".equals(el.getAttribute("typ"))) {
            	NodeList daneList = el.getElementsByTagName("dane");
            	Map<String, String> vatRates = new HashMap<>();
                for (int j = 0; j < daneList.getLength(); j++) {
                	Element dane = (Element) daneList.item(j);
                    Element wart = firstElementByTag(dane, "wartosci");
                    kodVat = safeAttr(dane, "kod");
                    // 🔥 Zapisz mapę VAT dla całego dokumentu
                    if ("02".equals(kodVat)) {
                        statusVat = "opodatkowana";
                        stawka = "23";
                    } else if ("22".equals(kodVat)) {
                    	statusVat = "opodatkowana";
                    	stawka = "8";
                    } else if ("99".equals(kodVat)) {
                    	statusVat = "opodatkowana";
                    	stawka = "0";
                    } else if ("51".equals(kodVat)) {
                    	statusVat = "zwolniona";
                    	stawka = "0";
                    } else {
                    	statusVat = "nie podlega";
                    	stawka = "0";
                    }
                    DmsVatEntry entry = new DmsVatEntry();
                    entry.statusVat = statusVat;
                    entry.stawka = stawka;//safeAttr(dane, "stawka");
                    entry.podstawa = wart != null ? safeAttr(wart, "podstawa") : "";
                    entry.vat = wart != null ? safeAttr(wart, "vat") : "";
                    out.addVatEntry(entry);
                    kodVat = safeAttr(dane, "kod");      // 02, 22
                    //stawka = stawka;//safeAttr(dane, "stawka"); // 23.00, 8.00
                    vatRates.put(kodVat, stawka);
                    foundVat = true;
                    log.info(String.format("[PARSER][DZ][VAT] entry: kod=%s podstawa=%s vat=%s", kodVat, entry.podstawa, entry.vat));
                }   
                //out.setVatRate(normalizeVatRate(stawka));
                //out.setStatusVat(statusVat);
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
            String stawka = p.getStawkaVat();// String stawka = vatRates.get(p.getStawkaVat());
            //log.info(String.format("[DZ][POS]  stawka=%s p=%s ",  p.getStawkaVat(), p));
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
                //log.info(String.format("[DZ][POS] lp=%s kodVat=%s stawka=%s netto=%s vat=%s brutto=%s stawkaVal=%s", p.getLp(), p.getKodVat(), p.getStawkaVat(), p.getNetto(), p.getVat(), p.getBrutto(), stawkaVal 	));
            } else {
                // fallback — brak stawki VAT
                p.stawkaVat = vatRates.get(p.stawkaVat);//"0";
                p.vat = "0.00";
                p.brutto = p.netto != null ? p.netto : "0.00";
            }
        }
    }

private void applyCorrectionsDZ(DmsParsedDocument out, List<DmsPosition> list) {
    double baseFromDms = out.getVatEntries().stream()
            .mapToDouble(c -> parseDoubleSafe(c.podstawa)).sum();
    
    double vatFromDms = out.getVatEntries().stream()
            .mapToDouble(e -> parseDoubleSafe(e.vat))
            .sum();
    
    double bruttoFromPositions = list.stream()
            .mapToDouble(b -> parseDoubleSafe(b.brutto)).sum();
    
    double nettoFromPositions = list.stream()
            .mapToDouble(n -> parseDoubleSafe(n.netto)).sum();

    double vatFromPositions = list.stream()
            .mapToDouble(p -> parseDoubleSafe(p.getVat()))
            .sum();
    log.info(String.format("1 extract DZ: baseFromDms='%s':, vatFromDms='%s':, nettoFromPositions='%s': ,vatFromPositions='%s':,bruttoFromPositions='%s': ",baseFromDms, vatFromDms, nettoFromPositions, vatFromPositions, bruttoFromPositions));
    double advanceNet = parseDoubleSafe(out.getAdvanceNet());
    double advanceVat = parseDoubleSafe(out.getAdvanceVat());
    double advanceBrutto = advanceNet; //+ advanceVat
    double diffBruttoPosAdv = bruttoFromPositions - (baseFromDms + vatFromDms + advanceBrutto);
    log.info(String.format("2 extract DZ: advanceNet='%s': ,advanceVat='%s': ,advanceBrutto='%s':  ,diffBruttoPosAdv='%s': ", advanceNet, advanceVat, advanceBrutto, diffBruttoPosAdv));
    if (Math.abs(advanceNet) > 0) {//<= 0.10) {
        DmsPosition last = list.get(list.size() - 1);

        double netto = parseDoubleSafe(last.netto) - advanceNet;
        double vat   = parseDoubleSafe(last.vat) - advanceVat ;//
        //log.info(String.format("2a extractPositions: netto='%s': ,vat='%s': ", netto, vat));
        last.netto = String.format(Locale.US, "%.2f", netto);
        last.vat   = String.format(Locale.US, "%.2f", vat);
        log.info(String.format("2a extract DZ: netto='%s': ,vat='%s': ", netto, vat));
    }
    double nettoAfterAdvance = list.stream()
            .mapToDouble(p -> parseDoubleSafe(p.netto))
            .sum();

    double vatAfterAdvance = list.stream()
            .mapToDouble(p -> parseDoubleSafe(p.vat))
            .sum();
    log.info(String.format("3 extract DZ: nettoAfterAdvance='%s': ,vatAfterAdvance='%s': ", nettoAfterAdvance, vatAfterAdvance));
    double diffVat = vatFromDms - vatAfterAdvance;
    double diffNetto = baseFromDms - nettoAfterAdvance;
    log.info(String.format("4 extract DZ: diffVat='%s': ,diffNetto='%s': ", diffVat, diffNetto));
    boolean hasSmallDiff =
            (Math.abs(diffVat) > 0.0001 && Math.abs(diffVat) <= 0.10) ||
            (Math.abs(diffNetto) > 0.0001 && Math.abs(diffNetto) <= 0.10);
    // tylko groszowe różnice
    //if (Math.abs(diffVat) > 0.0001 && Math.abs(diffVat) <= 0.10) {
    	if (!list.isEmpty() && hasSmallDiff) {
        DmsPosition last = list.get(list.size() - 1);

        double lastVat = parseDoubleSafe(last.getVat());
        double lastNet = parseDoubleSafe(last.netto);
        double lastBrutto = parseDoubleSafe(last.getBrutto());

        double newVat = lastVat + diffVat;
        double newNet = lastNet + diffNetto;
        double newBrutto = newNet + newVat; // bezpieczne: brutto = net + vat

        last.setVat(String.format(Locale.US, "%.2f", newVat));
        last.netto = String.format(Locale.US, "%.2f", newNet);
        last.setBrutto(String.format(Locale.US, "%.2f", newBrutto));

        log.info(String.format("5 extract DZ: correctedVat='%s', correctedNet='%s', correctedBru='%s'",
                String.format(Locale.US, "%.2f", newVat),
                String.format(Locale.US, "%.2f", newNet),
                String.format(Locale.US, "%.2f", newBrutto)));
    }

}


    private List<DmsPosition> extractPositionsDZ(Document doc, DmsParsedDocument out, String bruttoUmowa,String rejestrDZ) {

    	    // 1) Pozycje z typ="50"
    	    List<DmsPosition> list = extractPositions50(doc,rejestrDZ,out.getOddzial());
    	 // sprawdź, czy istnieje document typ=03
    	    boolean has03 = false;
    	    NodeList allDocs = doc.getElementsByTagName("document");
    	    for (int i = 0; i < allDocs.getLength(); i++) {
    	        Element el = (Element) allDocs.item(i);
    	        if ("03".equals(el.getAttribute("typ"))) {
    	            has03 = true;
    	            break;
    	        }
    	    }

    	    // jeśli brak 03 i brak 50 → twórz pozycje z VAT 06
    	    if (!has03 && list.isEmpty()) {
    	        list = createPositionsFromVat06(doc);
    	        if(list.isEmpty()) {
    	        	list = createPositionsFrom76(doc,bruttoUmowa);
    	        }
    	    }
    	    
    	    // 2) Uzupełnienie z typ="03"
    	    apply03ToPositions(doc, list);

    	    // 3) VAT z typ="06"
    	    applyVatToPositions(out, list);

    	    // 4) Korekty
    	    applyCorrectionsDZ(out, list);

    	    return list;
    	}

  
    private List<DmsPosition> extractPositions50(Document doc, String rejestrDZ, String oddzial) {
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
                String kodVat = "";
                String statusVat = "";
                String stawka = "";
                DmsPosition p = new DmsPosition();
                p.lp = safeAttr(dane, "lp");
                kodVat = safeAttr(dane, "kod_vat");
                p.kodVat = kodVat;
                p.typDZ = safeAttr(dane, "typ");
                p.jm = safeAttr(dane, "jm");
                p.jmSymb = safeAttr(dane, "jm_symb");
                if ("02".equals(oddzial)) {
                    p.netto = wart != null ? safeAttr(wart, "netto") : "";
                } else {
                p.netto = wart != null ? safeAttr(wart, "netto_prup") : "";
                }
                p.cenaNetto = wart != null ? safeAttr(wart, "cena_netto") : "";
                p.opis = rozs != null ? safeAttr(rozs, "opis1") : "";
                p.nrKonta = rozs != null ? safeAttr(rozs, "nr_konta") : "";
                if ("02".equals(kodVat)) {
                    statusVat = "opodatkowana";
                    stawka = "23";
                } else if ("22".equals(kodVat)) {
                	statusVat = "opodatkowana";
                	stawka = "8";
                } else if ("99".equals(kodVat)) {
                	statusVat = "opodatkowana";
                	stawka = "0";
                } else if ("51".equals(kodVat)) {
                	statusVat = "zwolniona";
                	stawka = "0";
                } else {
                	statusVat = "nie podlega";
                	stawka = "0";
                }
                p.statusVat = statusVat;
                p.stawkaVat = stawka;
                Element klas = null;
                NodeList kl = dane.getElementsByTagName("klasyfikatory");
                if (kl.getLength() > 0) klas = (Element) kl.item(0);
                // klasyfikacja
                String rawK = klas != null ? klas.getAttribute("klasyfikacja") : "";
                p.kategoria2 = rawK;
                String category = p.kategoria2;
                if(p.kategoria2.isBlank()) {category="TOWARY";}
                switch (category) {
                case "KAWA/HERBATA": p.rodzajKoszty = "Inne";break; 
                case "TOWARY": p.rodzajKoszty = "Towary";p.kategoria2="TOWARY";break;
                case "INNE USŁUGI 550": p.rodzajKoszty = "Usługi";p.kategoria2="INNE USŁUGI 550";break;}
                //log.info(String.format("[DZ][POS_50] wyrDoc=%s lp=%s kodVat=%s stawka=%s netto=%s vat=%s brutto=%s statusVat=%s",rejestrDZ, p.getLp(), p.getKodVat(), p.getStawkaVat(), p.getNetto(), p.getVat(), p.getBrutto(), p.getStatusVat()));
                list.add(p);
            }
        }

        return list;
    }
    private List<DmsPosition> createPositionsFromVat06(Document doc) {
        List<DmsPosition> list = new ArrayList<>();
log.info("1 doc in positions From VAT doc='%s '"+ doc);
        // znajdź document typ="06"
        Element doc06 = null;
        
        NodeList allDocs = doc.getElementsByTagName("document");
        for (int i = 0; i < allDocs.getLength(); i++) {
            Element el = (Element) allDocs.item(i);
            if ("06".equals(el.getAttribute("typ"))) {
                doc06 = el;
                break;
            }
        }
        log.info("2 doc in positions From VAT doc='%s '"+ doc);
        if (doc06 == null) {
            return list; // brak rejestru VAT
        }
        String statusVat = "";
        String stawka = "";
        // każdy <dane> w typ=06 = jedna pozycja
        NodeList daneList = doc06.getElementsByTagName("dane");
        for (int i = 0; i < daneList.getLength(); i++) {
            Element dane = (Element) daneList.item(i);

            DmsPosition p = new DmsPosition();
            p.lp = dane.getAttribute("lp");
            if (p.lp == null || p.lp.isBlank()) {
                p.lp = String.valueOf(i + 1);
            }

            // kod VAT i stawka z <dane>
            String kodVat = safeAttr(dane, "kod");
            p.kodVat = kodVat;    // np. 41
            p.stawkaVat = dane.getAttribute("stawka"); // np. 0.00, 23.00, 8.00
            if ("02".equals(kodVat)) {
                statusVat = "opodatkowana";
                stawka = "23";
            } else if ("22".equals(kodVat)) {
            	statusVat = "opodatkowana";
            	stawka = "8";
            } else if ("99".equals(kodVat)) {
            	statusVat = "opodatkowana";
            	stawka = "0";
            } else if ("51".equals(kodVat)) {
            	statusVat = "zwolniona";
            	stawka = "0";
            } else {
            	statusVat = "nie podlega";
            	stawka = "0";
            }
            p.statusVat = statusVat;
            p.stawkaVat = stawka;
            // <wartosci podstawa="..." vat="..."/>
            Element wart = null;
            NodeList wartList = dane.getElementsByTagName("wartosci");
            if (wartList.getLength() > 0) {
                wart = (Element) wartList.item(0);
            }

            String podstawa = wart != null ? wart.getAttribute("podstawa") : "0.00";
            String vat = wart != null ? wart.getAttribute("vat") : "0.00";

            // netto = podstawa, brutto = podstawa + vat
            double nettoVal = parseDoubleSafe(podstawa);
            double vatVal = parseDoubleSafe(vat);
            double bruttoVal = nettoVal + vatVal;

            p.netto = String.format(Locale.US, "%.2f", nettoVal);
            p.vat = String.format(Locale.US, "%.2f", vatVal);
            p.brutto = String.format(Locale.US, "%.2f", bruttoVal);

            p.podstawaVat = p.netto; // jeśli masz takie pole
            p.opis = "Pozycja z rejestru VAT (typ 06)";
            Element klas = null;
            NodeList kl = dane.getElementsByTagName("klasyfikatory");
            if (kl.getLength() > 0) klas = (Element) kl.item(0);
            // klasyfikacja
            String rawK = klas != null ? klas.getAttribute("klasyfikacja") : "";
            p.kategoria2 = rawK;
            String category = p.kategoria2;
            if(p.kategoria2.isBlank()) {category="MATERIAŁY";}
            switch (category) {
            case "KAWA/HERBATA": p.rodzajKoszty = "Inne";break; 
            case "MATERIAŁY": p.rodzajKoszty = "Towary";p.kategoria2="MATERIAŁY";break;
        }
            //log.info("[DZ][POS] idx=" + i + " rawKategoria2=" + rawK);
            list.add(p);
        }

        return list;
    }
    private List<DmsPosition> createPositionsFrom75(Document doc) {
        List<DmsPosition> list75 = new ArrayList<>();
//log.info("75 doc in positions From VAT list='%s' doc='%s'"+list75+ doc);
        // znajdź document typ="06"
        Element doc75 = null;
        
        NodeList allDocs = doc.getElementsByTagName("document");
        for (int i = 0; i < allDocs.getLength(); i++) {
            Element el = (Element) allDocs.item(i);
            if ("75".equals(el.getAttribute("typ"))) {
                doc75 = el;
                break;
            }
        }
        //log.info("75 doc in positions From VAT doc='%s '"+ doc);
        if (doc75 == null) {
            return list75; // brak typu doc
        }
        // każdy <dane> w typ=06 = jedna pozycja
        NodeList daneList = doc75.getElementsByTagName("dane");
        for (int i = 0; i < daneList.getLength(); i++) {
            Element dane = (Element) daneList.item(i);

            DmsPosition p = new DmsPosition();
            p.lp = dane.getAttribute("lp");
            if (p.lp == null || p.lp.isBlank()) {
                p.lp = String.valueOf(i + 1);
            }

            // VIN (header classifier)
            Element klas = firstElementByTag(dane, "klasyfikatory");
            if (klas != null) {
                String vin = safeAttr(klas, "vin");
                if (vin != null && !vin.isBlank()) {
                    p.setVin(vin.trim());
                }
            }
            //p.statusVat = "nie podlega";
            //p.stawkaVat = "0";
            //p.odliczenia = "Nie";
            // <wartosci podstawa="..." vat="..."/>
            Element numm = null;
            NodeList numerList = dane.getElementsByTagName("numer");
            Element wart = null;
            NodeList wartList = dane.getElementsByTagName("wartosci");
            if (numerList != null && numerList.getLength() > 0) {
                numm = (Element) numerList.item(0);
            }
            if (wartList.getLength() > 0) {
                wart = (Element) wartList.item(0);
            }

            String kwotaPz = "0.00";
            if (wart != null) {
                String raw = wart.getAttribute("kwota");
                if (raw != null && !raw.isBlank()) {
                    kwotaPz = raw.trim();
                }
            }

            String numerPez = "";
            if (numm != null) {
                numerPez = numm.getTextContent();
                if (numerPez != null) numerPez = numerPez.trim();
            }
            // netto = podstawa, brutto = podstawa + vat
            double nettoVal = parseDoubleSafe(kwotaPz);
            double vatVal = 0.00;
            //wyjątkowo przypisanie brutto z nagłówka dokumentu do netto i brutto 
            double bruttoVal = Double.parseDouble(kwotaPz.replace(",", "."));//bruttoUmowa or nettoVal + vatVal;
            p.netto = String.format(Locale.US, "%.2f", bruttoVal);
            p.vat = String.format(Locale.US, "%.2f", vatVal);
            p.brutto = String.format(Locale.US, "%.2f", bruttoVal);
            p.numer = numerPez;
            p.podstawaVat = p.netto; // jeśli masz takie pole
            //p.opis = "TOWARY";
            //p.rodzajKoszty = "Towary";
            //p.kategoria2="TOWARY";
            log.info(String.format("[75][POS] p.odliczenia='%s'", p.getNumer()));
            list75.add(p);
        }

        return list75;
    }
    private List<DmsPosition> createPositionsFrom76(Document doc, String bruttoUmowa) {
        List<DmsPosition> list76 = new ArrayList<>();
log.info("76 doc in positions From VAT list='%s' doc='%s'"+list76+ doc);
        // znajdź document typ="06"
        Element doc76 = null;
        
        NodeList allDocs = doc.getElementsByTagName("document");
        for (int i = 0; i < allDocs.getLength(); i++) {
            Element el = (Element) allDocs.item(i);
            if ("76".equals(el.getAttribute("typ"))) {
                doc76 = el;
                break;
            }
        }
        log.info("76 doc in positions From VAT doc='%s '"+ doc);
        if (doc76 == null) {
            return list76; // brak typu doc
        }
        // każdy <dane> w typ=06 = jedna pozycja
        NodeList daneList = doc76.getElementsByTagName("dane");
        for (int i = 0; i < daneList.getLength(); i++) {
            Element dane = (Element) daneList.item(i);

            DmsPosition p = new DmsPosition();
            p.lp = dane.getAttribute("lp");
            if (p.lp == null || p.lp.isBlank()) {
                p.lp = String.valueOf(i + 1);
            }
            p.statusVat = "nie podlega";
            p.stawkaVat = "0";
            p.odliczenia = "Nie";
            // <wartosci podstawa="..." vat="..."/>
            Element wart = null;
            NodeList wartList = dane.getElementsByTagName("wartosci");
            if (wartList.getLength() > 0) {
                wart = (Element) wartList.item(0);
            }

            String kwotaRozl = wart != null ? wart.getAttribute("kwota") : "0.00";
            String kwotaRozl2 = wart != null ? wart.getAttribute("kwota2") : "0.00";
            String nettoZakup = wart != null ? wart.getAttribute("netto_zakup") : "0.00";

            // netto = podstawa, brutto = podstawa + vat
            double nettoVal = parseDoubleSafe(nettoZakup);
            double vatVal = 0.00;
            //wyjątkowo przypisanie brutto z nagłówka dokumentu do netto i brutto 
            double bruttoVal = Double.parseDouble(bruttoUmowa.replace(",", "."));//nettoVal + vatVal;
            p.netto = String.format(Locale.US, "%.2f", bruttoVal);
            p.vat = String.format(Locale.US, "%.2f", vatVal);
            p.brutto = String.format(Locale.US, "%.2f", bruttoVal);

            p.podstawaVat = p.netto; // jeśli masz takie pole
            p.opis = "SAMOCHODY UŻYWANE";
            p.rodzajKoszty = "Towary";
            p.kategoria2="SAMOCHODY UŻYWANE";
            log.info(String.format("[76][POS] p.odliczenia='%s'", p.getOdliczenia()));
            list76.add(p);
        }

        return list76;
    }


    private void apply03ToPositions(Document doc, List<DmsPosition> list) {
        // 1) Znajdź <document typ="02"> gdziekolwiek w drzewie
        Element doc02 = null;
        NodeList allDocs = doc.getElementsByTagName("document");
        for (int i = 0; i < allDocs.getLength(); i++) {
            Element el = (Element) allDocs.item(i);
            if ("02".equals(el.getAttribute("typ"))) {
                doc02 = el;
                break;
            }
        }
        if (doc02 == null) {
            //log.info("[DZ][POS] document typ=02 NOT FOUND");
            return;
        }
     // 2) Pobierz PIERWSZE <dane> pod document typ=02 (to jest nagłówek)
        NodeList daneList = doc02.getElementsByTagName("dane");
        if (daneList.getLength() == 0) {
            //log.info("[DZ][POS] no <dane> under document typ=02");
            return;
        }
        Element daneRoot = (Element) daneList.item(0);
        // 3) Pobierz PIERWSZE <rozszerzone> pod tym <dane>
        NodeList rozsList = daneRoot.getElementsByTagName("rozszerzone");
        if (rozsList.getLength() == 0) {
            //log.info("[DZ][POS] no <rozszerzone> under dane[0]");
            return;
        }
        Element rozszerzone = (Element) rozsList.item(0);

        //log.info("[DZ][POS] rozszerzone FOUND");
        // 4) Wewnątrz rozszerzone znajdź document typ="03"
        Element doc03 = null;
        NodeList docsInside = rozszerzone.getElementsByTagName("document");
        for (int i = 0; i < docsInside.getLength(); i++) {
            Element el = (Element) docsInside.item(i);
            if ("03".equals(el.getAttribute("typ"))) {
                doc03 = el;
                break;
            }
        }
        if (doc03 == null) {
            //log.info("[DZ][POS] document typ=03 NOT FOUND inside rozszerzone");
            return;
        }

        // 5) Pobierz dane z typ=03
        NodeList dane03 = doc03.getElementsByTagName("dane");
     // 6) Mapowanie indeksowe: dane03[j] → list[j]
        for (int j = 0; j < dane03.getLength(); j++) {

            if (j >= list.size()) continue;

            DmsPosition p = list.get(j);
            Element dane = (Element) dane03.item(j);

            Element klas = null;
            NodeList kl = dane.getElementsByTagName("klasyfikatory");
            if (kl.getLength() > 0) klas = (Element) kl.item(0);

            Element wart = null;
            NodeList wa = dane.getElementsByTagName("wartosci");
            if (wa.getLength() > 0) wart = (Element) wa.item(0);

            // klasyfikacja
            String rawK = klas != null ? klas.getAttribute("klasyfikacja") : "";
            p.kategoria2 = rawK;
            String category = p.kategoria2;
            if(p.kategoria2.isBlank()) {category="INNE USŁUGI 550";}
            switch (category) {
            case "1  ": p.rodzajKoszty = "Inne";   p.kategoria2 = "AMORTYZACJA"; break;
            case "2  ": p.rodzajKoszty = "Inne";   p.kategoria2 = "KOSZTY FINANSOWE"; break;
            case "3  ": p.rodzajKoszty = "Inne";   p.kategoria2 = "ODSETKI IN MINUS"; break;
            case "4  ": p.rodzajKoszty = "Inne";   p.kategoria2 = "KOSZTY EKSPLOATACJI SAMOCHODÓW"; break;
            case "5  ": p.rodzajKoszty = "Inne";   p.kategoria2 = "ENERGIA"; break;
            case "6  ": p.rodzajKoszty = "Inne";   p.kategoria2 = "GAZ"; break;
            case "7  ": p.rodzajKoszty = "Inne";   p.kategoria2 = "KARY/ODSZKODOWANIA"; break;
            case "8  ": p.rodzajKoszty = "Inne";   p.kategoria2 = "KOSZTY EKSPLOATACJI SAMOCHODÓW 75%"; break;
            case "9  ": p.rodzajKoszty = "Paliwo";   p.kategoria2 = "PALIWO 100%"; break;
            case "10 ": p.rodzajKoszty = "Paliwo";   p.kategoria2 = "PALIWO 50%"; break;
            case "11 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "POCZĘSTUNEK DLA KLIENTA"; break;
            case "12 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "MATERIAŁY BIUROWE"; break;
            case "13 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "MATERIAŁY GOSPODARCZE"; break;
            case "14 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "MATERIAŁY NA SALON"; break;
            case "15 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "MATERIAŁY PODSTAWOWE"; break;
            case "16 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "MATERIAŁY SERWIS"; break;
            case "17 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "ŚRODKI CZYSTOŚCI"; break;
            case "18 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "OPŁATY OD NIERUCHOMOŚCI"; break;
            case "19 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "OPŁATY I PODATKI LOKALNE"; break;
            case "20 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "POZOSTAŁE OPŁATY"; break;
            case "21 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "ŚRODEK TRWAŁY W BUDOWIE"; break;
            case "22 ": p.rodzajKoszty = "Towary"; p.kategoria2 = "TOWARY-CZĘŚCI"; break;
            case "23 ": p.rodzajKoszty = "Towary"; p.kategoria2 = "TOWARY SAMOCHODY"; break;
            case "24 ": p.rodzajKoszty = "Towary"; p.kategoria2 = "SAMOCHODY UŻYWANE"; break;
            case "25 ": p.rodzajKoszty = "Inne"; p.kategoria2 = "ALLEGRO MAGAZYN"; break;
            case "26 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "AUDATEX"; break;
            case "27 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "GWARANCJA"; break;
            case "28 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "INNE SALON"; break;
            case "29 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "INNE SERWIS"; break;
            case "30 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "INNE USŁUG 550"; break;
            case "31 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "KRD"; break;
            case "32 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "NAJEM TERMINALA"; break;
            case "33 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "NAJEM/DZIERŻAWA"; break;
            case "34 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "OCHRONA MIENIA"; break;
            case "35 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "ODPADY"; break;
            case "36 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "OGŁOSZENIA"; break;
            case "37 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "OPŁATY ZA TRANSAKCJE KARTĄ"; break;
            case "38 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "OPŁATY BANKOWE"; break;
            case "39 ": p.rodzajKoszty = "Towary"; p.kategoria2 = "PODWYKONAWCA BLACH/LAK"; break;
            case "40 ": p.rodzajKoszty = "Towary"; p.kategoria2 = "PODWYKONAWCA SERWIS"; break;
            case "41 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "PRANIE UBRAŃ"; break;
            case "42 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "PROWIZJA BLACH-LAK"; break;
            case "43 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "PRZEGLĄDY I LEGALIZACJA"; break;
            case "44 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "REKLAMA"; break;
            case "45 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "RTV-TELEWIZJA"; break;
            case "46 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "SPRZĄTANIE"; break;
            case "47 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "STELLANTIS"; break;
            case "48 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "STOWARZYSZENIE"; break;
            case "49 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "TELEKOMUNIKACJA"; break;
            case "50 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "TRANSPORT OBCY"; break;
            case "51 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "UBEZPIECZENIA"; break;
            case "52 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "UMOWY"; break;
            case "53 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "USŁUGI INFORMATYCZNE"; break;
            case "54 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "USŁUGI MARKETINGOWE"; break;
            case "55 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "USŁUGI OBCE NKUP"; break;
            case "56 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "USŁUGI PRAWNE"; break;
            case "57 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "USŁUGI WINDYKACYJNE"; break;
            case "58 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "BHP"; break;
            case "59 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "DELEGACJE SALON"; break;
            case "60 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "DELEGACJE SERWIS"; break;
            case "61 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "ŚWIADCZENIA BHP"; break;
            case "62 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "ZAKUPY W KRAJACH UE"; break;
            case "63 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "USŁUGI W UE"; break;
            case "64 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "ZALICZKI OD KONTRAHENTÓW"; break;
            case "65 ": p.rodzajKoszty = "Towary"; p.kategoria2 = "WYPOSAŻENIE"; break;
            case "66 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "WODA I ŚCIEKI"; break;
            case "67 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "INNE MAGAZYN"; break;
            case "68 ": p.rodzajKoszty = "Inne";   p.kategoria2 = "POCZTA"; break;
            default:
                p.rodzajKoszty = "Inne";
                p.kategoria2 = "NIEZNANA KATEGORIA";
                break;
            //case "13 " : p.rodzajKoszty = "Inne";p.kategoria2="PALIWO 100%";break; 
            //case "17 " : p.rodzajKoszty = "Inne";p.kategoria2="PALIWO 50%";break;
            case "KAWA/HERBATA": p.rodzajKoszty = "Inne";break; 
            case "MATERIAŁY": p.rodzajKoszty = "Towary";p.kategoria2="MATERIAŁY";break;//Wartości: Materiały handlowe tutaj w typ 03
        }
            //log.info("[DZ][POS] idx=" + j + " rawKategoria2=" + rawK);

            /*if (!rawK.isBlank()) {
                p.kategoria2 = "MATERIAŁY";
                p.rodzajKoszty = "Towary";
            }*/

            // netto_zakup
            if (wart != null) {
                p.nettoZakup = wart.getAttribute("netto_zakup");
            }
        }
        //--------------------------------------------------------------------------
        // 1) Pobierz <dane> bezpośrednio pod <DMS>
        //Element rootDane = null;
        /*NodeList rootChildren = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < rootChildren.getLength(); i++) {
            Node n = rootChildren.item(i);
            if (n instanceof Element && "dane".equals(n.getNodeName())) {
                rootDane = (Element) n;
                break;
            }
        }
        if (rootDane == null) {
            log.info("[DZ][POS] rootDane not found");
            return;
        }
        
        // 2) W tym <dane> znajdź <document typ="02">
        Element rootDoc = null;
        NodeList docs = rootDane.getElementsByTagName("document");
        for (int i = 0; i < docs.getLength(); i++) {
            Element el = (Element) docs.item(i);
            if ("02".equals(el.getAttribute("typ"))) {
                rootDoc = el;
                break;
            }
        }
        if (rootDoc == null) {
            log.info("[DZ][POS] document typ=02 not found under rootDane");
            return;
        }
        // 3) Pobierz <dane> pod document typ=02
        //Element daneRoot = null;
        NodeList children = rootDoc.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element && "dane".equals(n.getNodeName())) {
                daneRoot = (Element) n;
                break;
            }
        }
        if (daneRoot == null) {
            log.info("[DZ][POS] dane under typ=02 not found");
            return;
        }

        // 4) Pobierz <rozszerzone> pod tym <dane>
        //Element rozszerzone = null;
        NodeList rz = daneRoot.getChildNodes();
        for (int i = 0; i < rz.getLength(); i++) {
            Node n = rz.item(i);
            if (n instanceof Element && "rozszerzone".equals(n.getNodeName())) {
                rozszerzone = (Element) n;
                break;
            }
        }
        log.info("[DZ][POS] rozszerzone=" + rozszerzone);
        if (rozszerzone == null) return;
        //NodeList docs = rootDane.getElementsByTagName("document");
        for (int i = 0; i < docs.getLength(); i++) {
            Element el = (Element) docs.item(i);
            if (!"03".equals(el.getAttribute("typ"))) continue;

            //NodeList daneList = el.getElementsByTagName("dane");
            for (int j = 0; j < daneList.getLength(); j++) {
                // Powiązanie po indeksie, nie po LP
                if (j >= list.size()) continue;
                DmsPosition p = list.get(j);
                Element dane = (Element) daneList.item(j);
                Element wart = firstElementByTag(dane, "wartosci");
                Element klas = firstElementByTag(dane, "klasyfikatory");

                //String lp = safeAttr(dane, "lp");
                //DmsPosition p = findByLp(list, lp);
                //if (p == null) continue;
                //p.rodzajKoszty = "towary"; 
                p.kategoria2 = klas != null ? safeAttr(klas, "klasyfikacja") : "";
                String category = p.kategoria2;
                log.info(String.format(
                	    "[DZ][POS] kategoria2=%s ",
                	    p.kategoria2
                	));
                if(!p.kategoria2.isBlank()) {category="MATERIAŁY";}
                switch (category) {
                case "KAWA/HERBATA": p.rodzajKoszty = "Inne";break; 
                case "MATERIAŁY": p.rodzajKoszty = "Towary";p.kategoria2="MATERIAŁY";break;//Wartości: Materiały handlowe tutaj w typ 03
            }
                log.info(String.format(
                	    "[DZ][POS] kategoria2=%s rodzajKoszty=%s",
                	    p.kategoria2, p.rodzajKoszty
                	));
                p.nettoZakup = wart != null ? safeAttr(wart, "netto_zakup") : "";
            }
        }*/
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
                out.setNrBank(nrRach);
                
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
             //log.info(String.format("3 extractPayments: dSumAdvanceNet='%s': ,kwn='%s': ", dSumAdvanceNet, kwn));
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
        boolean hasContractor = false;
        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("35".equals(el.getAttribute("typ"))) {
            	hasContractor = true;
                Contractor c = new Contractor();
                Element dane = (Element) el.getElementsByTagName("dane").item(0);
                Element rozs = (Element) dane.getElementsByTagName("rozszerzone").item(0);
                String wyr = rozs.getAttribute("wyr");
                c.isCompany = "F".equalsIgnoreCase(wyr);
                c.id = rozs.getAttribute("kod_klienta");
                c.setNip(rozs.getAttribute("nip"));
                c.name1 = rozs.getAttribute("nazwa1");
                c.name2 = rozs.getAttribute("nazwa2");
                c.name3 = rozs.getAttribute("nazwa3");
                c.country = rozs.getAttribute("kod_kraju");
                c.city = rozs.getAttribute("miejscowosc");
                c.zip = rozs.getAttribute("kod_poczta");
                c.street = rozs.getAttribute("ulica");
                //log.info(String.format("kod_kraju=%s", c.country));
                if(!"PL".equals(c.country)) {
                	c.setExpKrajowy("wewnątrzunijny");
                } else {
                	c.setExpKrajowy("nie");
                }
             // pełna nazwa
                c.fullName = buildFullName(c);
                if (c.isCompany) {
                    // Firma
                    c.czynny = "Tak";// jeśli firma to tak, inaczej nie
                } else {
                    // Osoba fizyczna
                	c.czynny = "Nie";// jeśli firma to tak, inaczej nie
                }
                return c;

            }
        }
     // 🔥 Fallback — brak typ 35 → sprzedaż detaliczna 
        Contractor c = new Contractor(); 
        c.isCompany = false; 
        c.czynny = "Nie"; 
        //c.nip = ""; 
        //c.name1 = ""; 
        //c.name2 = ""; 
        //c.name3 = ""; 
        //c.fullName = ""; 
        return c;
    }

    // -----------------------
    // Helper methods
    // -----------------------
    private Element firstDirectChildByTag(Element parent, String tag) {
        if (parent == null) return null;
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element && tag.equals(n.getNodeName())) {
                return (Element) n;
            }
        }
        return null;
    }

    private Element findDocumentByType(Document doc, String typ) {
        NodeList allDocs = doc.getElementsByTagName("document");
        for (int i = 0; i < allDocs.getLength(); i++) {
            Element el = (Element) allDocs.item(i);
            if (typ.equals(el.getAttribute("typ"))) return el;
        }
        return null;
    }

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
    private String buildFullName(Contractor c) {

        // Osoba fizyczna: nazwisko + imię
        if (!c.isCompany) {
            StringBuilder sb = new StringBuilder();
            if (c.name1 != null && !c.name1.isBlank()) sb.append(c.name1.trim());
            if (c.name2 != null && !c.name2.isBlank()) sb.append("_").append(c.name2.trim());
            return sb.toString().trim();
        }

        // Firma: nazwa1 + nazwa2 + nazwa3
        StringBuilder sb = new StringBuilder();
        if (c.name1 != null && !c.name1.isBlank()) sb.append(c.name1.trim());
        if (c.name2 != null && !c.name2.isBlank()) sb.append(" ").append(c.name2.trim());
        if (c.name3 != null && !c.name3.isBlank()) sb.append(" ").append(c.name3.trim());
        return sb.toString().trim();
    }
    private String positionsToOpis(List<DmsPosition> positions) {
        if (positions == null || positions.isEmpty()) return "";

        // przykładowy format: "1) Nazwa - ilość x cena; 2) ..."
        return positions.stream()
            .map(this::positionToLine)
            .collect(Collectors.joining("; "));
    }
    private String positionToLine(DmsPosition p) {
        String name = safe(p.getNumer());
        String brt = safe(p.getBrutto()); // lub format liczbowy

        // dopasuj format do potrzeb, np. "Nazwa  — 2 szt. — 12.50"
        StringBuilder sb = new StringBuilder();
        if (!name.isEmpty()) sb.append("Nr PZ: ");
        sb.append(name);
        //if (!name.isEmpty()) sb.append(" — ").append(name);
        if (!brt.isEmpty()) sb.append(" kwota: ").append(brt);
        return sb.toString();
    }
    private String safe(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
