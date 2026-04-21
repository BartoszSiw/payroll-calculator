package pl.edashi.dms.parser;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.common.logging.AppLogger.LogUtils;
import pl.edashi.common.util.MappingIdDocs;
import org.apache.logging.log4j.util.BiConsumer;
import org.w3c.dom.*;
import pl.edashi.dms.model.*;
import pl.edashi.dms.model.DmsParsedDocument.DmsVatEntry;
import pl.edashi.dms.parser.util.DocumentNumberExtractor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
//import javax.xml.parsers.DocumentBuilderFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

public class DmsParserDS implements DmsParser{
	private final AppLogger log = new AppLogger("DmsParserDS");
	private static final BigDecimal PERCENT_TOL = new BigDecimal("0.5"); // tolerance in percent
    public DmsParsedDocument parse(Document doc, String fileName) {
        DmsParsedDocument out = new DmsParsedDocument();
        out.setSourceFileName(fileName);
        // ============================
        // 1. METADATA
        // ============================
        Element root = doc.getDocumentElement();
        Element document = (Element) doc.getElementsByTagName("document").item(0);
        Element numerEl = (Element) document.getElementsByTagName("numer").item(0);
        String genDocId = root.getAttribute("gen_doc_id");
        String id = root.getAttribute("id");
        String trans = root.getAttribute("trans");
        Element daty = (Element) doc.getElementsByTagName("daty").item(0);
        Element warto = (Element) doc.getElementsByTagName("wartosci").item(0);
     // preferowana, centralna metoda ekstrakcji dla parsera DS
        boolean hasNumberInDane = false;
        boolean found = DocumentNumberExtractor.extractFromGenInfo(root, out, fileName,hasNumberInDane);

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
        		 //log.info("RWS");
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
        //log.info(String.format("Gen_Info: extracted documentType='%s', ",out.getDocumentType()));
        String dataSprzed = daty.getAttribute("data_sprzed");
        String dataDok = daty.getAttribute("data_dok");
        String nrKsef = numerEl.getAttribute("nr_ksef");
        out.setNrKsef(nrKsef);
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
                daty.getAttribute("data"),
                warto.getAttribute("waluta")
        ));
        Element dms = (Element) doc.getElementsByTagName("DMS").item(0);
        // ============================
        // 2. KONTRAHENT (typ 35)
        // ============================
        String podmiot = "";
        Contractor c = extractContractor(doc);
        out.setContractor(c);

        if (c == null || c.getNip() == null || c.getNip().isBlank()) {
            out.setDokumentDetaliczny("Tak");
        } else {
            out.setDokumentDetaliczny("Nie");
        }

        // ============================
        // 2. DMS (typ dokumentu)
        // ============================
        //extractDocumentNumberFromGenInfo(dms, out); 
        out.setTypDocAnalizer("DS");
        // ============================
        // 3. VAT (typ 06)
        // ============================
        extractVat(doc, out);
        for (DmsParsedDocument.DmsVatEntry e : out.getVatEntries()) {
            e.initRemaining();
        }

        // ============================
        // 4. PŁATNOŚCI (typ 40 + 43)
        // ============================
        out.setPayments(extractPayments(doc, out));
        // ============================
        // 5. POZYCJE (typ 03)
        // ============================
        //String defaultVatRate = out.getVatRate();
        //if (defaultVatRate == null) defaultVatRate = "";
        List<DmsKwotaDodatkowa> kwoty66 = extractKwoty66(doc);
        List<DmsPosition> positions = extractPositionsDS(doc, out, kwoty66);
        out.setPositions(positions);
        // ============================
        // 5. PŁATNOŚCI (typ 40 + 43)
        // ============================
        //out.setPayments(extractPayments(doc, out));
        
        // ============================
        // 7. FISKALIZACJA (typ 94)
        // ============================
        extractFiscal(doc, out);
        extractCorrection(doc, out);
     // 🔥 TU: logika akronimu dla sprzedaży paragonowej
        

     // zabezpieczenia: c może być null
     if ("Tak".equalsIgnoreCase(out.getDokumentFiskalny())) {
    	 log.info(String.format("if 1 podmiot='%s' fullName='%s'", podmiot, c.fullName));
         // dokument fiskalny (paragon)
    	 if ((c.getNip() == null || c.getNip().isBlank()) 
    		        && (c.getFullName() == null || c.getFullName().isBlank())) {
             // brak NIP — traktujemy jako sprzedaż paragonowa
             String paragonName = "SPRZEDAZ_PARAGONOWA";
             if (c != null) {
                 // ustawiaj tylko jeśli nie nadpisujesz czegoś ważnego
                 c.setFullName(paragonName);
                 c.setName1("Sprzedaż Paragonowa");
                 c.setPodmiot(paragonName);
             }
             podmiot = paragonName;
         } else {
             // jest NIP lub full name 
        	 log.info(String.format("if 2 podmiot='%s' fullName='%s' nip='%s'", podmiot, c.fullName, c.getNip()));
        		 if (c.getNip() != null && !c.getNip().isBlank()) {
        		 podmiot = c.getNip().trim();
        		 log.info(String.format("if 3 podmiot='%s' fullName='%s' nip='%s'", podmiot, c.fullName, c.getNip()));
        		 c.setPodmiot(podmiot);
        	 } else {
                 podmiot = c.getFullName().trim();
                 log.info(String.format("if 4 podmiot='%s' fullName='%s'", podmiot, c.fullName));
                 c.setPodmiot(podmiot);
        	 }
             //if (c != null) c.setPodmiot(podmiot);
         }
     } else {
    	 log.info(String.format("else podmiot='%s'", podmiot));
         // nie jest dokumentem fiskalnym — preferuj NIP, potem fullName
         if (c != null && c.getNip() != null && !c.getNip().isBlank()) {
             podmiot = c.getNip().trim();
             c.setPodmiot(podmiot);
         } else if (c != null && c.getFullName() != null && !c.getFullName().isBlank()) {
             podmiot = c.getFullName().trim();
             c.setPodmiot(podmiot);
         } else {
             podmiot = "";
             c.setPodmiot(podmiot);
         }
         if (c != null) c.setPodmiot(podmiot);
     }

        out.setContractor(c);
        out.setPodmiot(podmiot);
        String code = "S";
        out.setMappingTarget(MappingTarget.fromCode(code));
        // ============================
        // 8. UWAGI (typ 98)
        // ============================
        out.setNotes(extractNotes(doc));

     // w metodzie parse
     
     String numer = out.getInvoiceNumber();
     String nrIdPlat = MappingIdDocs.generateCandidate(podmiot, "D" ,numer, 36);
     String fullKey = MappingIdDocs.buildFullKey(podmiot, numer);
     String hash = MappingIdDocs.shortHashFromFullKey(fullKey, 6);
     String docKey = MappingIdDocs.generateDocId(podmiot, "S" ,numer, 36);

     out.setFullKey(fullKey);
     out.setDocKey(docKey);
     out.setNrIdPlat(nrIdPlat);
     out.setHash(hash);
     List<DmsPayment> payId = out.getPayments();
     if (payId != null && !payId.isEmpty()) {
         payId.get(0).setIdPlatn(nrIdPlat); // setIdPlatn zwraca void — wykonaj to osobno
     }
     //log.info(String.format("nrIdPlat ='%s' Podmiot='%s' payId.get(0)='%s' payId='%s'", nrIdPlat, podmiot, payId.get(0), payId));
     return out;
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
    private List<DmsPosition> extractPositionsDS(Document doc, DmsParsedDocument out, List<DmsKwotaDodatkowa> kwoty66) {
        // 1) Zbuduj surową listę pozycji (istniejąca metoda)
        List<DmsPosition> list = extractPositions(doc, out, kwoty66);
     // append pending extras (created during assignVatToPosition)
        List<DmsPosition> extras = out.drainPendingExtras();
     // replace original extras loop with this robust version
        BigDecimal tol = new BigDecimal("0.01");
        for (DmsPosition extra : extras) {
            if (extra == null) continue;

            // skip zero-net extras
            BigDecimal extraNet = parseAmount(extra.netto);
            if (extraNet == null || extraNet.compareTo(BigDecimal.ZERO) == 0) {
                log.info(String.format("Skipping pending extra with zero netto: netto=%s stawka=%s", extra.netto, extra.stawkaVat));
                continue;
            }

            String extraRate = normRate(extra.stawkaVat);
            // normalize empty to empty string (we'll try to infer later)
            if (extraRate == null) extraRate = "";

            // compute duplicate by explicit loop (so we can log once per extra, not per comparison)
            boolean duplicate = false;
            for (DmsPosition p : list) {
                BigDecimal pNet = parseAmount(p.netto);
                if (pNet == null || extraNet == null) continue;
                // numeric match within tolerance
                if (pNet.subtract(extraNet).abs().compareTo(tol) <= 0) {
                    // compare rates robustly:
                    String pRate = normRate(p.stawkaVat);
                    if (pRate == null || pRate.isBlank()) {
                        // try to infer from VAT/netto if stawka missing
                        pRate = inferRateFromPosition(p);
                    }
                    // if still blank, treat as non-matching (conservative)
                    if (pRate == null) pRate = "";

                    // treat "0" and "" as different from "23" etc.
                    if (pRate.equals(extraRate)) {
                        duplicate = true;
                        log.info(String.format("Detected duplicate extra: extra.netto=%s extraRate=%s matches existing p.netto=%s pRate=%s",
                                extra.netto, extraRate, p.netto, pRate));
                        break;
                    }
                }
            }

            if (duplicate) {
                log.info(String.format("Skipped adding extra (duplicate): netto=%s, stawka=%s", extra.netto, extra.stawkaVat));
                continue;
            }

            // not duplicate -> add
            list.add(extra);
            log.info(String.format("Added extra position from pending: netto=%s stawka=%s status=%s", extra.netto, extra.stawkaVat, extra.statusVat));
        }

        return list;
    }
    // ------------------------------
    // POZYCJE 
    // ------------------------------
    private List<DmsKwotaDodatkowa> extractKwoty66(Document doc) {
        List<DmsKwotaDodatkowa> kwoty66 = new ArrayList<>();
        NodeList allDocs = doc.getElementsByTagName("document");
        for (int k = 0; k < allDocs.getLength(); k++) {
    	    Element el = (Element) allDocs.item(k);
    	    //log.info("DS document[" + k + "] typ=" + el.getAttribute("typ"));
    	    if ("66".equals(el.getAttribute("typ")) || "61".equals(el.getAttribute("typ"))) {
    	        NodeList daneList66 = el.getElementsByTagName("dane");
    	        //log.info("DS typ 66 61, daneList length = " + daneList66.getLength());
    	        for (int j = 0; j < daneList66.getLength(); j++) { 
    	        	Element dane = (Element) daneList66.item(j); 
    	        	Element wart = (Element) dane.getElementsByTagName("wartosci").item(0); 
    	        	Element rozs = (Element) dane.getElementsByTagName("rozszerzone").item(0);
    	        	//log.info("DS typ 66 61, wart node = " + (wart != null) + "rozs node="+(rozs != null));
	        	if (wart == null) {
	        		if(rozs == null) continue;
	        		//addIfPositive(kwoty66, "10", "CZĘŚCI-SERWIS-GWARAN", "", "","705-1-11",rozs.getAttribute("vin"),"");
	        	} else {
	        	//log.info(String.format("DS extKwoty66 66 61 -> size=%s",kwoty66.toString()));
	        	//log.info("DS: typ 66, netto_koszt='" + wart.getAttribute("netto_koszt_mat") + "'");
	            //addIfPositive(kwoty66, wart.getAttribute("netto_koszt"),"KOSZT","KOSZT", "", "737-01",rozs.getAttribute("vin"),"");
	            //addIfPositive(kwoty66, wart.getAttribute("netto_koszt_rob"), "KOSZT_ROB", "KOSZT_ROB", "","737-02",rozs.getAttribute("vin"),"");
	            //addIfPositive(kwoty66, wart.getAttribute("netto_koszt_usl"), "KOSZT_USL","KOSZT_USL","","737-03",rozs.getAttribute("vin"),"");
	            addIfPositive(kwoty66, wart.getAttribute("netto_koszt_mat"), "SPRZEDAŻ MATERIAŁÓW","","401-01","310-1",rozs.getAttribute("vin"),"");
	            //addIfPositive(kwoty66, wart.getAttribute("netto_dlr_rob"), "ROBOCIZNA-GWARANCJA","","","705-1-18",rozs.getAttribute("vin"),"");
	            addIfPositive(kwoty66, wart.getAttribute("netto_gwar_rob"), "ROBOCIZNA-GWARANCJA","","","705-1-18",rozs.getAttribute("vin"),"");
	            //addIfPositive(kwoty66, wart.getAttribute("netto_dlr_mat"), "DLR_MAT", "", "401-01","310-1",rozs.getAttribute("vin"),"");
	            addIfPositive(kwoty66, wart.getAttribute("netto_gwar_mat"), "CZĘŚCI-SERWIS-GWARAN", "", "","705-1-11",rozs.getAttribute("vin"),"");
	        	}
            }
        } }
        //log.info(String.format("DS extractKwoty66 -> size=%s",kwoty66.toString()));
        return kwoty66;
    }

    private List<DmsPosition> extractPositions(Document doc, DmsParsedDocument out, List<DmsKwotaDodatkowa> kwoty66){
    	boolean hasType66 = !kwoty66.isEmpty();
    	//log.info(String.format("1 kwoty66 p.getKwotyDodatkowe()='%s': ", kwoty66));
    	
        List<DmsPosition> listOut = new ArrayList<>();
        //if (doc == null || out == null) return listOut;
        NodeList list = doc.getElementsByTagName("document");
        // 1) Zbierz pozycje (03/04/05)
        for (int i = 0; i < list.getLength(); i++) {
            Element document = (Element) list.item(i);
            String typ = document.getAttribute("typ");
            if (!"03".equals(typ) && !"04".equals(typ) && !"05".equals(typ) ) continue;
            //log.info(String.format("1 kwoty66 p.getKwotyDodatkowe()='%s': typ='%s '", kwoty66, typ));
            NodeList daneList = document.getElementsByTagName("dane");
            for (int j = 0; j < daneList.getLength(); j++) {
                Element dane = (Element) daneList.item(j);
                if (dane == null) continue;

                Element wart = (Element) dane.getElementsByTagName("wartosci").item(0);
                Element klas = (Element) dane.getElementsByTagNameNS("*", "klasyfikatory").item(0);
                Element rozs = (Element) dane.getElementsByTagName("rozszerzone").item(0);

                String klasyf = klas != null ? klas.getAttribute("klasyfikacja") : "";
                Element numEl = (Element) dane.getElementsByTagName("numer").item(0);
                String numer = numEl != null ? numEl.getTextContent().trim() : "";

                if ("PR".equalsIgnoreCase(klasyf) && numer != null && !numer.isBlank()) {
                    DmsPosition pos = new DmsPosition();
                    pos.setKlasyfikacja("PR");
                    pos.setNumer(numer);
                    listOut.add(pos);
                    //log.info("ParserDS: ADDED POSITION PR=" + numer);
                    continue;
                }

                DmsPosition p = new DmsPosition();
             // każda pozycja dostaje własną kopię kwot dodatkowych
                List<DmsKwotaDodatkowa> localKwoty = new ArrayList<>(kwoty66);
                p.setKwotyDodatkowe(localKwoty);
                //log.info(String.format("1 kwoty66 p.getKwotyDodatkowe()='%s': typ='%s '", p.getKwotyDodatkowe(), typ));
                p.type = typ;
                p.kategoria2 = klas != null ? klas.getAttribute("kod") : "";
                p.kanal = klas != null ? klas.getAttribute("kanal") : "";
                p.kanalKategoria = (p.kategoria2 != null && !p.kategoria2.isBlank()) ? p.kanal + "-" + p.kategoria2 : "";            
                p.vin = rozs != null ? safeAttr(rozs, "vin") : "";
                p.netto = wart != null ? wart.getAttribute("netto_sprzed") : "";
                p.nettoZakup = wart != null ? wart.getAttribute("netto_zakup") : "";
                p.nettoRob = wart != null ? wart.getAttribute("netto_rob") : "";
                if (p.netto == null) p.netto = "";
                //log.info(String.format("extractPositions p.vin='%s ': p.netto='%s ' p.nettoKoMat='%s ' p.nettoGwMar='%s ' typ='%s '", p.vin, p.netto, p.nettoKoMat, p.nettoGwMat, typ));
                boolean hasVat = out.isHasVatDocument();
                String vatRate = out.getVatRate();
                String statVat = out.getStatusVat();
             // when building a position p, find matching vat entry (by kod or by nearest match)
                /*DmsParsedDocument.DmsVatEntry vatEntry = out.getVatEntries() != null && !out.getVatEntries().isEmpty()
                	    ? out.getVatEntries().get(0)
                	    : null;
                DmsParsedDocument.DmsVatEntry vatEntry = out.getVatEntries().stream()
                	    .filter(e -> approxEquals(parseAmount(e.podstawa), parseAmount(p.netto)))
                	    .findFirst().orElse(null);
                BigDecimal tol = new BigDecimal("0.01");
                DmsParsedDocument.DmsVatEntry vatEntry = out.getVatEntries().stream()
                    .filter(e -> {
                        BigDecimal eb = parseAmount(e.podstawa);
                        BigDecimal pn = parseAmount(p.netto);
                        return approxEqualsAbs(eb, pn, tol);
                    })
                    .findFirst()
                    .orElse(null)*/


                	//String stawka = (vatEntry != null && vatEntry.stawka != null) ? vatEntry.stawka.trim() : "0";
                	//String statusVat = (vatEntry != null && vatEntry.statusVat != null) ? vatEntry.statusVat.trim() : "nie podlega";
                // zastąp swój vatEntry = ... tym:
             // pomocnicze (raz w klasie)


                // --- tutaj przypisujemy VAT natychmiast dla tej pozycji ---
                //String stawka = (vatEntry != null && vatEntry.stawka != null) ? vatEntry.stawka.trim() : "0";
            	//String statusVat = (vatEntry != null && vatEntry.statusVat != null) ? vatEntry.statusVat.trim() : "nie podlega";
                try {
                    assignVatToPosition(p, out, hasVat); // mutuje p i zmniejsza remainingPodstawa w out.getVatEntries()
                    log.info(String.format("AFTER assign (extract loop): idx=%d p.netto=%s p.stawkaVat=%s p.statusVat=%s p.vat=%s",
                    	    i, p.netto, p.stawkaVat, p.statusVat, p.vat));

                    // --- SAFETY: ensure p has stawka/status and computed VAT immediately after assignment ---
                    if (p.stawkaVat == null || p.stawkaVat.isBlank()) {
                        // spróbuj dopasować stawkę z pierwszego sensownego wpisu VAT (deterministycznie)
                        DmsParsedDocument.DmsVatEntry guess = null;
                        for (DmsParsedDocument.DmsVatEntry ve : out.getVatEntries()) {
                            if (ve != null && ve.podstawaParsed != null && ve.podstawaParsed.abs().compareTo(BigDecimal.ZERO) > 0) {
                                guess = ve;
                                break;
                            }
                        }
                        if (guess != null && guess.stawka != null && !guess.stawka.isBlank()) p.stawkaVat = trimOrEmpty(guess.stawka);
                        else p.stawkaVat = "0";
                    }
                    //if (p.statusVat == null || p.statusVat.isBlank()) p.statusVat = "nie podlega";

                    // policz VAT/brutto jeśli jeszcze nie policzono
                    if (p.vat == null || p.vat.isBlank()) {
                        computeVatAndBruttoForPosition(p);
                    }

                    // debug snapshot (możesz usunąć po testach)
                    log.info(String.format("After assign (safety): p.netto=%s p.nettoZakup=%s stawka=%s status=%s vat=%s",
                        p.netto, p.nettoZakup, p.stawkaVat, p.statusVat, p.vat));

                } catch (Exception ex) {
                    log.error("Błąd przypisywania VAT dla pozycji: " + ex.getMessage(), ex);
                    // w razie błędu ustaw domyły, ale nie przerywamy całej metody
                    if (!hasVat) {
                        double nettoVal = p.netto != null && !p.netto.isBlank() ? parseDoubleSafe(p.netto.replace(",", ".")) : 0.0;
                        p.stawkaVat = "0";
                        p.statusVat = "nie podlega";
                        p.vat = "0.00";
                        p.brutto = String.format(Locale.US, "%.2f", nettoVal);
                    } else {
                        computeVatAndBruttoForPosition(p);
                    }
                }


                p.kategoria = "PRZYCHODY";
                String readRejestr = out.getDaneRejestr();
                //log.info(String.format("DS readRejestr='%s': ", readRejestr));
             // 1) Rodzaj sprzedaży wg typ
             switch (typ) {
                 case "03": p.rodzajSprzedazy = "Towary"; break;
                 case "04": p.rodzajSprzedazy = "Usługi"; break;
                 case "05": p.rodzajSprzedazy = "Usługi"; break;
             }

             // 2) Grupy rejestrów
             boolean isRejSerwis = readRejestr.equals("100") || readRejestr.equals("101") || readRejestr.equals("102");
             boolean isRejBlacharka = readRejestr.equals("110");
             boolean isRejCzesci = readRejestr.equals("120") || readRejestr.equals("121");
             boolean isRejCzesciSklep = readRejestr.equals("200");
             boolean isRejSamochUzyw = readRejestr.equals("070");
             boolean isRejSamochNowe = readRejestr.equals("001") || readRejestr.equals("002");
             boolean isRejSamochody = readRejestr.equals("003");
             // 3) Kategoria zależna od typ + rejestr
             if (isRejSerwis) {
                 switch (typ) {
                     case "03": addIfPositive(localKwoty, wart.getAttribute("netto_zakup"), "CZĘŚCI-SERWIS","","","",safeAttr(rozs, "vin"),""); 
                     p.kategoria = "CZĘŚCI-SERWIS"; break;
                     case "04": p.kategoria = "ROBOCIZNA-SERWIS"; break;
                     case "05": p.kategoria = "USŁUGI OBCE-SERWIS"; break;
                 }
             }

             if (isRejBlacharka) {
                 switch (typ) {
                 case "03": addIfPositive(localKwoty, wart.getAttribute("netto_zakup"), "CZĘSCI-BLACHARNIA","","","",safeAttr(rozs, "vin"),""); 
                 p.kategoria = "CZĘSCI-BLACHARNIA"; 
                 p.rodzajSprzedazy = "Towary"; break;
                 case "04": p.kategoria = "ROBOCIZNA-BLACHARSKA"; p.rodzajSprzedazy = "Usługi"; break;
                 case "05": p.kategoria = "USŁUGI OBCE-BLACHARN"; p.rodzajSprzedazy = "Usługi"; break;
             }
                 
             }
             if (isRejCzesciSklep) {
            	 //log.info(String.format("22 kwoty66 netto_zakup='%s': isRejCzesciSklep='%s '", wart.getAttribute("netto_zakup"), isRejCzesciSklep));
                 switch (typ) {
                 case "03": addIfPositive(localKwoty, wart.getAttribute("netto_zakup"), "CZĘSCI-SKLEP","","","",safeAttr(rozs, "vin"),""); 
                 p.kategoria = "CZĘSCI-SKLEP"; 
                 p.rodzajSprzedazy = "Towary"; break;
                 case "04": p.kategoria = "PRZYCHODY"; p.rodzajSprzedazy = "Usługi"; break;
                 case "05": p.kategoria = "PRZYCHODY"; p.rodzajSprzedazy = "Usługi"; break;
             }
                 
             }
             if (isRejSamochNowe) {
                 switch (typ) {
                 case "03": addIfPositive(localKwoty, wart.getAttribute("netto_zakup"), "TOWARY-SAM.NOWE","","","",safeAttr(rozs, "vin"),""); 
                 p.kategoria = "TOWARY-SAM.NOWE"; 
                 p.rodzajSprzedazy = "Towary"; break;
                 case "04": p.kategoria = "PRZYCHODY"; p.rodzajSprzedazy = "Usługi"; break;
                 case "05": p.kategoria = "PRZYCHODY"; p.rodzajSprzedazy = "Usługi"; break;
             }
                 
             }
             if (isRejSamochUzyw) {
                 switch (typ) {
                 case "03": addIfPositive(localKwoty, wart.getAttribute("netto_zakup"), "TOWARY-SAM.UŻYWANE","","","",safeAttr(rozs, "vin"),""); 
                 p.kategoria = "TOWARY-SAM.UŻYWANE"; 
                 p.rodzajSprzedazy = "Towary"; break;
                 case "04": p.kategoria = "PRZYCHODY"; p.rodzajSprzedazy = "Usługi"; break;
                 case "05": p.kategoria = "PRZYCHODY"; p.rodzajSprzedazy = "Usługi"; break;
             }
             }
                 if (isRejSamochody) {
                     switch (typ) {
                     case "03": addIfPositive(localKwoty, wart.getAttribute("netto_zakup"), "TOWARY-SAM.NOWE","","","",safeAttr(rozs, "vin"),""); 
                     p.kategoria = "TOWARY-SAM.NOWE"; 
                     p.rodzajSprzedazy = "Towary"; break;
                     case "04": p.kategoria = "PRZYCHODY"; p.rodzajSprzedazy = "Usługi"; break;
                     case "05": p.kategoria = "PRZYCHODY"; p.rodzajSprzedazy = "Usługi"; break;
                 }
                 }
             
             //log.info(String.format("4 kategoria='%s': ", p.kategoria));
             if(hasType66 && isRejCzesci) {
             //p.kategoria = "SPRZEDAŻ MATERIAŁÓW";
             } else if (isRejCzesci){
                 switch (typ) {
                 case "66": p.kategoria = "SPRZEDAŻ MATERIAŁÓW"; break;
                 case "04": p.kategoria = "ROBOCIZNA-SERWIS"; break;
                 case "05": p.kategoria = "USŁUGI OBCE-SERWIS"; break;
             }
             }
             //log.info(String.format("5kategoria='%s': ", p.kategoria));
            	/*SPRZEDAŻ MATERIAŁÓW netto_koszt_mat="334.81"
            	 //705-1-11 netto_gwar_mat="635.07"
            	 //705-1-18 netto_dlr_rob="425.00" netto_dlr_usl="0.00" netto_dlr_mat="495.48" netto_gwar="1060.07" netto_gwar_rob="425.00"
                 //p.kategoria = "CZĘŚCI-SERWIS";
             }

              //log.info("readRejestr=%s " + readRejestr);
                switch (typ) {
                    case "03": p.rodzajSprzedazy = "Towary"; break;  //Wartości: Materiały handlowe 
                    case "04": p.rodzajSprzedazy = "Usługi";  break;
                    case "05": p.rodzajSprzedazy = "Inne"; break;
                }
                switch (readRejestr) {
                case "100": p.kategoria = "ROBOCIZNA-SERWIS"; break;
                case "101": p.kategoria = "ROBOCIZNA-SERWIS"; break;
                case "102": p.kategoria = "ROBOCIZNA-SERWIS"; break;
                case "110": p.kategoria = "ROBOCIZNA-BLACHARSKA"; break;
                case "120": p.kategoria = "CZĘŚCI-SERWIS"; break;
                case "121": p.kategoria = "CZĘŚCI-SERWIS"; break;
                
                }*/
                
                listOut.add(p);
            }
        }
        // ===============================
        // KOREKTA VAT – zgodność z typem 06
        // ===============================
        
     // SUMY Z POZYCJI — uwzględniamy wszystkie pozycje, w tym PR
        //log.info("Po case");
        double baseFromDms = parseDoubleSafe(out.getVatBase()); //vat podstawa
        double vatFromDms = parseDoubleSafe(out.getVatAmount());
        double nettoFromPositions = listOut.stream()
                .mapToDouble(p -> parseDoubleSafe(p.netto)).sum();

        double bruttoFromPositions = listOut.stream()
                .mapToDouble(p -> parseDoubleSafe(p.brutto)).sum();


        // Suma VAT z pozycji
        double vatFromPositions = listOut.stream()
                .mapToDouble(p -> parseDoubleSafe(p.vat))
                .sum();
        log.info(String.format("1 extractPositions: baseFromDms='%s':, vatFromDms='%s':, nettoFromPositions='%s': ,vatFromPositions='%s':,bruttoFromPositions='%s': ",baseFromDms, vatFromDms, nettoFromPositions, vatFromPositions, bruttoFromPositions));
        double advanceNet = parseDoubleSafe(out.getAdvanceNet());
        double advanceVat = parseDoubleSafe(out.getAdvanceVat());
        log.info(String.format(Locale.US, "DEBUG advance parsed: advanceNet=%.6f, advanceVat=%.6f", advanceNet, advanceVat));
        //------------------------------------------------------------------
     // Jeśli jest zaliczka — dodajemy ją jako osobną pozycję (typ 45)
        if (Math.abs(advanceNet) > 0.0001 || Math.abs(advanceVat) > 0.0001) {
            DmsPosition advPos = new DmsPosition();
            advPos.setOpis("ZALICZKA");
            // ustawiamy wartości ujemne, żeby odjąć od dokumentu
            advPos.setNetto(String.format(Locale.US, "%.2f", -advanceNet));
            advPos.setVat(String.format(Locale.US, "%.2f", -advanceVat));
            advPos.setBrutto(String.format(Locale.US, "%.2f", -(advanceNet + advanceVat)));
            // uzupełnij pola, których mapper oczekuje (kategoria/kanal/stawka itp.)
            advPos.setKategoria("SPRZEDAŻ ZALICZKA"); // lub "" jeśli builder filtruje po kategoriach
            advPos.setKanalKategoria("");
            String stawka = detectVatRateFromNumbers(advanceNet, (advanceNet + advanceVat));
            advPos.stawkaVat = stawka;
            advPos.statusVat = "0".equals(stawka) ? "nie podlega" : "opodatkowana";
            try { advPos.setAdvance(true); } catch (Throwable ignored) {}
            // Dodajemy zaliczkę na koniec listy pozycji
            listOut.add(advPos);
            log.info("extractPositions Zaliczka: appended DmsPosition advance -> netto=" + advPos.getNetto() + " isAdvance="+advPos.isAdvance()+ " vat=" + advPos.getVat() + " brutto=" + advPos.getBrutto());
        }

        //------------------------------------------------------------------
        double advanceBrutto = advanceNet; //+ advanceVat
        double diffBruttoPosAdv = bruttoFromPositions - (baseFromDms + vatFromDms + advanceBrutto);
        log.info(String.format("2 extractPositions: advanceNet='%s': ,advanceVat='%s': ,advanceBrutto='%s':  ,diffBruttoPosAdv='%s': ", advanceNet, advanceVat, advanceBrutto, diffBruttoPosAdv));
        /*if (Math.abs(advanceNet) > 0) {//<= 0.10) {
            DmsPosition last = listOut.get(listOut.size() - 1);

            double netto = parseDoubleSafe(last.netto) - advanceNet;
            double vat   = parseDoubleSafe(last.vat) - advanceVat ;//
            log.info(String.format("2a extractPositions: netto='%s': ,vat='%s': ", netto, vat));
            last.netto = String.format(Locale.US, "%.2f", netto);
            last.vat   = String.format(Locale.US, "%.2f", vat);
        }*/

        double nettoAfterAdvance = listOut.stream()
                .mapToDouble(p -> parseDoubleSafe(p.netto))
                .sum();

        double vatAfterAdvance = listOut.stream()
                .mapToDouble(p -> parseDoubleSafe(p.vat))
                .sum();
        log.info(String.format("3 extractPositions: nettoAfterAdvance='%s': ,vatAfterAdvance='%s': ", nettoAfterAdvance, vatAfterAdvance));
        // Różnica
        double diffVat = vatFromDms - vatAfterAdvance;
        double diffNetto = baseFromDms - nettoAfterAdvance;;
        log.info(String.format("4 extractPositions: diffVat='%s': ,diffNetto='%s': ", diffVat, diffNetto));
        // Jeśli różnica jest minimalna (0.01 lub -0.01)
        boolean hasSmallDiff =
                (Math.abs(diffVat) > 0.0001 && Math.abs(diffVat) <= 0.10) ||
                (Math.abs(diffNetto) > 0.0001 && Math.abs(diffNetto) <= 0.10);

        //if (!listOut.isEmpty() && (Math.abs(diffVat) <= 0.10 || Math.abs(diffNetto) <= 0.10)) {
        if (!listOut.isEmpty() && hasSmallDiff) {
            DmsPosition last = listOut.get(listOut.size() - 1);

            double vat = parseDoubleSafe(last.vat);
            double net = parseDoubleSafe(last.netto);

            vat += diffVat;
            net += diffNetto;

            last.vat = String.format(Locale.US, "%.2f", vat);
            last.netto = String.format(Locale.US, "%.2f", net);

            log.info(String.format("5 extractPositions: correctedVat=%s, correctedNet=%s", vat, net));
        }

        /*if (Math.abs(diffNetto) <= 0.10 && !listOut.isEmpty()) {
            DmsPosition last = listOut.get(listOut.size() - 1);

            double correctedBase = Double.parseDouble(last.netto) + diffNetto;
            log.info(String.format("6 extractPositions: correctedBase='%s': ", correctedBase));
            last.netto = String.format(Locale.US, "%.2f", correctedBase);
        }*/

        // 7) Kierunek dokumentu i ustawienie kierunku na pozycjach
        //if (nettoFromPositions < 0) { out.setKierunek("rozchód"); } else { out.setKierunek("przychód"); }
        //for (DmsPosition p : listOut) { p.setKierunek(out.getKierunek()); } 
        //log.info("extractPositions Kierunek: " + out.getKierunek());
        return listOut;
    }
    private void assignVatToPosition(DmsPosition p, DmsParsedDocument out, boolean hasVat) {
        BigDecimal tol = new BigDecimal("0.01");
        List<DmsParsedDocument.DmsVatEntry> entries = out.getVatEntries();
        
        if (p == null) return;
        log.info(String.format("assignVat START: p.stawkaVat=%s p.netto=%s p.nettoZakup=%s hasVat=%s entries=%d",
        	    p.stawkaVat,p.netto, p.nettoZakup, hasVat, entries == null ? 0 : entries.size()));
        String firstEntryStawka = (entries != null && !entries.isEmpty() && entries.get(0) != null)
                ? String.valueOf(entries.get(0).stawka) : "n/a";
        log.info(String.format("assignVat ENTRIES snapshot (start)=%s, stawka=%s", snapshotVatEntries(entries), firstEntryStawka));
        if (entries == null || entries.isEmpty()) {
            // Brak typ 06 w XML, ale są zaliczki (typ 45) — ta sama stawka co przy pozycji „SPRZEDAŻ ZALICZKA” (wyłącznie z netto/VAT z XML)
            double advNet = parseDoubleSafe(out.getAdvanceNet());
            double advVat = parseDoubleSafe(out.getAdvanceVat());
            boolean zaliczkaZXml = Math.abs(advNet) > 0.0001 || Math.abs(advVat) > 0.0001;
            if (!out.isHasVatDocument() && zaliczkaZXml) {
                String stawka = detectVatRateFromNumbers(advNet, advNet + advVat);
                p.stawkaVat = stawka;
                p.statusVat = "0".equals(stawka) ? "nie podlega" : "opodatkowana";
                computeVatAndBruttoForPosition(p);
                log.info(String.format("1 assign VAT z zaliczek (bez typ 06): p.netto=%s stawka=%s status=%s vat=%s brutto=%s",
                        p.netto, p.stawkaVat, p.statusVat, p.vat, p.brutto));
                log.info(String.format("1 assign RETURN [%s]: p.netto=%s p.stawkaVat=%s p.statusVat=%s p.vat=%s; VAT snapshot=%s",
                        "ZALICZKA_NO_06", p.netto, p.stawkaVat, p.statusVat, p.vat, snapshotVatEntries(out.getVatEntries())));
                return;
            }

            log.info("nie podlega="+p.stawkaVat);
            p.stawkaVat = "0";
            p.statusVat = "nie podlega";
            if (!hasVat) {
            	log.info("!hasVat="+hasVat);
                double nettoVal = p.netto != null && !p.netto.isBlank() ? parseDoubleSafe(p.netto.replace(",", ".")) : 0.0;
                p.vat = "0.00";
                p.brutto = String.format(Locale.US, "%.2f", nettoVal);
            } else {
            	log.info("else compute status="+p.statusVat);
                computeVatAndBruttoForPosition(p);
            }
            log.info(String.format("1 assign RETURN [%s]: p.netto=%s p.stawkaVat=%s p.statusVat=%s p.vat=%s; VAT snapshot=%s",
            	    "PAIR_SUM", p.netto, p.stawkaVat, p.statusVat, p.vat, snapshotVatEntries(out.getVatEntries())));

            return;
        }

        // Ensure original parsed bases exist
        for (DmsParsedDocument.DmsVatEntry e : entries) {
            if (e == null) continue;
            if (e.podstawaParsed == null) e.podstawaParsed = parseAmount(e.podstawa) == null ? BigDecimal.ZERO : parseAmount(e.podstawa);
            if (e.remainingPodstawa == null) e.remainingPodstawa = e.podstawaParsed;
        }

        // Build ordered available list
     // Build ordered available list (single pass, deterministic order)
        List<DmsParsedDocument.DmsVatEntry> available = entries.stream()
            .filter(e -> e != null && e.remainingPodstawa != null && e.remainingPodstawa.abs().compareTo(BigDecimal.ZERO) > 0)
            .sorted(Comparator.comparing((DmsParsedDocument.DmsVatEntry e) -> e.podstawaParsed == null ? BigDecimal.ZERO : e.podstawaParsed).reversed())
            .collect(Collectors.toList());


        final BigDecimal pNet = parseAmount(p.netto) == null ? BigDecimal.ZERO : parseAmount(p.netto);
        final BigDecimal pZakup = parseAmount(p.nettoZakup) == null ? null : parseAmount(p.nettoZakup);

        // 1) If nettoZakup matches an original base -> assign that base
        if (pZakup != null) {
            for (DmsParsedDocument.DmsVatEntry e : available) {
            	if (approxEqualsAbs(e.podstawaParsed, pZakup, tol)) {
            	    BigDecimal assigned = e.podstawaParsed.min(e.remainingPodstawa);
            	    final BigDecimal originalPNet = pNet;
            	    BigDecimal leftover = originalPNet.subtract(assigned);

            	    // assign rate/status to current position (primary part)
            	    p.stawkaVat = trimOrEmpty(e.stawka);
            	    p.statusVat = trimOrEmpty(e.statusVat);

            	    // consume from entry
            	    e.remainingPodstawa = e.remainingPodstawa.subtract(assigned);
            	    if (e.remainingPodstawa.compareTo(BigDecimal.ZERO) <= 0) {
            	        e.remainingPodstawa = BigDecimal.ZERO;
            	        e.consumed = true;
            	    }

            	    // Set p.netto to the portion assigned to this VAT entry
            	    p.netto = assigned.toPlainString();
            	    computeVatAndBruttoForPosition(p);
            	    log.info("Assigned by nettoZakup match: assigned=" + assigned + " rate=" + e.stawka + " e.remainingPodstawa=" + e.remainingPodstawa);

            	    // If there is leftover in the original position, handle it
            	    if (leftover.compareTo(BigDecimal.ZERO) > 0) {
            	        // Decide whether to create extra: use your existing rule (positions < entries)
            	        boolean needExtraBecauseTooFewPositions = out.getPositions().size() < out.getVatEntries().size();

            	        if (needExtraBecauseTooFewPositions) {
            	            // Find a VAT entry to attach leftover to (prefer next available with remaining > 0)
            	            DmsParsedDocument.DmsVatEntry target = null;
            	            for (DmsParsedDocument.DmsVatEntry cand : available) {
            	                if (cand == e) continue; // skip the one we just consumed
            	                if (cand.remainingPodstawa != null && cand.remainingPodstawa.compareTo(BigDecimal.ZERO) > 0) {
            	                    target = cand;
            	                    break;
            	                }
            	            }
            	            // fallback: if none found, try any other entry (even if remaining==0) to get its rate
            	            if (target == null) {
            	                for (DmsParsedDocument.DmsVatEntry cand : entries) {
            	                    if (cand == e) continue;
            	                    if (cand != null) { target = cand; break; }
            	                }
            	            }

            	            // Create extra representing leftover and attach rate/status from target (or from e if none)
            	            DmsPosition extra = new DmsPosition();
            	            extra.netto = leftover.setScale(2, RoundingMode.HALF_UP).toPlainString();
            	            String extraRate = target != null ? trimOrEmpty(target.stawka) : trimOrEmpty(e.stawka);
            	            String extraStatus = target != null ? trimOrEmpty(target.statusVat) : trimOrEmpty(e.statusVat);
            	            extra.stawkaVat = extraRate == null || extraRate.isBlank() ? "0" : extraRate;
            	            extra.statusVat = extraStatus == null || extraStatus.isBlank() ? "nie podlega" : extraStatus;
            	            extra.type = "AUTO_FROM_VAT";
            	            extra.setKwotyDodatkowe(new ArrayList<>());

            	            // If target had remainingPodstawa, consume from it
            	            if (target != null && target.remainingPodstawa != null && target.remainingPodstawa.compareTo(BigDecimal.ZERO) > 0) {
            	                BigDecimal take = target.remainingPodstawa.min(leftover);
            	                target.remainingPodstawa = target.remainingPodstawa.subtract(take);
            	                if (target.remainingPodstawa.compareTo(BigDecimal.ZERO) <= 0) {
            	                    target.remainingPodstawa = BigDecimal.ZERO;
            	                    target.consumed = true;
            	                }
            	            }

            	            computeVatAndBruttoForPosition(extra);
            	            out.addPendingExtra(extra);
            	            log.info("Created EXTRA for leftover: leftover=" + leftover + " rate=" + extra.stawkaVat + " status=" + extra.statusVat);
            	        } else {
            	            // If we decided not to create extra (positions >= entries), attach leftover back to p or log it
            	            // Option A: keep original p.netto as full original (if you prefer)
            	            // p.netto = originalPNet.toPlainString();
            	            // computeVatAndBruttoForPosition(p);

            	            // Option B (conservative): log and leave leftover unassigned for later processing
            	            log.info("Leftover exists but positions >= entries; leftover=" + leftover + " will not create extra per rule");
            	        }
            	    }
                    log.info("Assigned by nettoZakup match: assigned=" + assigned + " rate=" + e.stawka + " e.remainingPodstawa="+e.remainingPodstawa);
                    log.info(String.format("2 assign RETURN [%s]: p.netto=%s p.stawkaVat=%s p.statusVat=%s p.vat=%s; VAT snapshot=%s",
                    	    "PAIR_SUM", p.netto, p.stawkaVat, p.statusVat, p.vat, snapshotVatEntries(out.getVatEntries())));

                    return;
                }
            }
        }
        // 2) Single exact match against original podstawaParsed (ordered)
        for (DmsParsedDocument.DmsVatEntry e : available) {
            if (approxEqualsAbs(e.podstawaParsed, pNet, tol)) {
                BigDecimal assigned = e.podstawaParsed.min(e.remainingPodstawa);
                p.stawkaVat = trimOrEmpty(e.stawka);
                p.statusVat = trimOrEmpty(e.statusVat);
                e.remainingPodstawa = e.remainingPodstawa.subtract(assigned);
                if (e.remainingPodstawa.compareTo(BigDecimal.ZERO) <= 0) { e.remainingPodstawa = BigDecimal.ZERO; e.consumed = true; }
                p.netto = assigned.toPlainString();
                computeVatAndBruttoForPosition(p);
                log.info("Assigned by single original base match: assigned=" + assigned + " rate=" + e.stawka);
                log.info(String.format("3 assign RETURN [%s]: p.netto=%s p.stawkaVat=%s p.statusVat=%s p.vat=%s; VAT snapshot=%s",
                	    "PAIR_SUM", p.netto, p.stawkaVat, p.statusVat, p.vat, snapshotVatEntries(out.getVatEntries())));

                return;
            }
        }

        // 3) Pair-sum detection: pNet == ai + aj
        if (available.size() >= 2) {
            outer:
            for (int i = 0; i < available.size(); i++) {
                for (int j = i + 1; j < available.size(); j++) {
                    DmsParsedDocument.DmsVatEntry ei = available.get(i);
                    DmsParsedDocument.DmsVatEntry ej = available.get(j);
                    BigDecimal ai = ei.podstawaParsed;
                    BigDecimal aj = ej.podstawaParsed;
                    if (ai == null || aj == null) continue;
                    if (approxEqualsAbs(ai.add(aj), pNet, tol)) {
                        // prefer nettoZakup if it matches one component
                        Integer pickIdx = null;
                        if (pZakup != null) {
                            if (approxEqualsAbs(pZakup, ai, tol)) pickIdx = i;
                            else if (approxEqualsAbs(pZakup, aj, tol)) pickIdx = j;
                        }
                        // if not decided, keep larger component in current position
                        if (pickIdx == null) pickIdx = (ai.compareTo(aj) >= 0) ? i : j;

                        DmsParsedDocument.DmsVatEntry primary = (pickIdx == i) ? ei : ej;
                        DmsParsedDocument.DmsVatEntry secondary = (pickIdx == i) ? ej : ei;

                        BigDecimal primaryBase = primary.podstawaParsed.min(primary.remainingPodstawa);
                        BigDecimal secondaryBase = secondary.podstawaParsed.min(secondary.remainingPodstawa);
                        // assign primary to current position
                     // przed compute: ustaw stawkę i status (jeśli p ma puste pola)
                        if (p.stawkaVat == null || p.stawkaVat.isBlank()) p.stawkaVat = trimOrEmpty(primary.stawka);
                        if (p.statusVat == null || p.statusVat.isBlank()) p.statusVat = trimOrEmpty(primary.statusVat);
                        if (p.stawkaVat == null || p.stawkaVat.isBlank()) p.stawkaVat = "0";
                        if (p.statusVat == null || p.statusVat.isBlank()) p.statusVat = "nie podlega";

                        computeVatAndBruttoForPosition(p);
                     // consume primaryBase
                        primary.remainingPodstawa = primary.remainingPodstawa.subtract(primaryBase);
                        if (primary.remainingPodstawa.compareTo(BigDecimal.ZERO) <= 0) {
                            primary.remainingPodstawa = BigDecimal.ZERO;
                            primary.consumed = true;
                        }
                        log.info(String.format("Pair-sum deterministic: kept p.netto=%s primaryBase=%s primaryRate=%s; secondary.remaining=%s",
                                p.netto, primaryBase, primary.stawka, secondary.remainingPodstawa));
                        log.info(String.format("4 assign RETURN [%s]: p.netto=%s p.stawkaVat=%s p.statusVat=%s p.vat=%s; VAT snapshot=%s",
                        	    "PAIR_SUM", p.netto, p.stawkaVat, p.statusVat, p.vat, snapshotVatEntries(out.getVatEntries())));

                        return;
                    }
                }
            }
        }

        // 4) Single match against remainingPodstawa (ordered)
        for (DmsParsedDocument.DmsVatEntry e : available) {
            if (approxEqualsAbs(e.remainingPodstawa, pNet, tol)) {
                BigDecimal assigned = e.remainingPodstawa.min(pNet);
                p.stawkaVat = trimOrEmpty(e.stawka);
                p.statusVat = trimOrEmpty(e.statusVat);
                e.remainingPodstawa = e.remainingPodstawa.subtract(assigned);
                if (e.remainingPodstawa.compareTo(BigDecimal.ZERO) <= 0) { e.remainingPodstawa = BigDecimal.ZERO; e.consumed = true; }
                p.netto = assigned.toPlainString();
                computeVatAndBruttoForPosition(p);
                log.info("Assigned by single remaining match: assigned=" + assigned + " rate=" + e.stawka);
                log.info(String.format("5 assign RETURN [%s]: p.netto=%s p.stawkaVat=%s p.statusVat=%s p.vat=%s; VAT snapshot=%s",
                	    "PAIR_SUM", p.netto, p.stawkaVat, p.statusVat, p.vat, snapshotVatEntries(out.getVatEntries())));

                return;
            }
        }

        // 5) Fallbacks: first with enough remaining, else partial from first available
        for (DmsParsedDocument.DmsVatEntry e : available) {
            if (e.remainingPodstawa.compareTo(pNet) >= 0) {
                BigDecimal assigned = pNet;
                p.stawkaVat = trimOrEmpty(e.stawka);
                p.statusVat = trimOrEmpty(e.statusVat);
                e.remainingPodstawa = e.remainingPodstawa.subtract(assigned);
                if (e.remainingPodstawa.compareTo(BigDecimal.ZERO) <= 0) { e.remainingPodstawa = BigDecimal.ZERO; e.consumed = true; }
                p.netto = assigned.toPlainString();
                computeVatAndBruttoForPosition(p);
                log.info("Fallback full assign: assigned=" + assigned + " rate=" + e.stawka);
                log.info(String.format("6 assign RETURN [%s]: p.netto=%s p.stawkaVat=%s p.statusVat=%s p.vat=%s; VAT snapshot=%s",
                	    "PAIR_SUM", p.netto, p.stawkaVat, p.statusVat, p.vat, snapshotVatEntries(out.getVatEntries())));

                return;
            }
        }
        log.info("available="+available.isEmpty());
        if (!available.isEmpty()) {
            DmsParsedDocument.DmsVatEntry e = available.get(0);
            BigDecimal assigned = e.remainingPodstawa.min(pNet);
            String assignedRate = trimOrEmpty(e.stawka);
            String assignedStatus = trimOrEmpty(e.statusVat);
         // consume the VAT entry
            e.remainingPodstawa = e.remainingPodstawa.subtract(assigned);
            if (e.remainingPodstawa.compareTo(BigDecimal.ZERO) <= 0) {
                e.remainingPodstawa = BigDecimal.ZERO;
                e.consumed = true;
            }
            log.info("AFTER CONSUME entry: stawka=" + e.stawka + " remaining=" + e.remainingPodstawa);
            // compute remainingForExtra zgodnie z Twoją logiką

            /*BigDecimal remainingForExtra = assigned == null ? BigDecimal.ZERO : assigned;
            if (remainingForExtra == null) remainingForExtra = BigDecimal.ZERO;

            if (remainingForExtra.compareTo(BigDecimal.ZERO) == 0) {
                log.info("No extra needed: remainingForExtra=" + remainingForExtra + " — skipping extra creation");
                // przypisz stawkę/status z użytego wpisu do p
                if (p.stawkaVat == null || p.stawkaVat.isBlank()) p.stawkaVat = trimOrEmpty(e.stawka);
                if (p.statusVat == null || p.statusVat.isBlank()) p.statusVat = trimOrEmpty(e.statusVat);
                computeVatAndBruttoForPosition(p);*/
            boolean needExtraBecauseTooFewPositions = out.getPositions().size() < out.getVatEntries().size();

            if (e.remainingPodstawa.compareTo(BigDecimal.ZERO) == 0 || !needExtraBecauseTooFewPositions) {
                log.info("No extra needed (remaining == 0) — skipping extra creation. assigned=" + assigned);
                // upewnij się, że p ma stawkę/status z użytego wpisu
                if (p.stawkaVat == null || p.stawkaVat.isBlank()) p.stawkaVat = trimOrEmpty(e.stawka);
                if (p.statusVat == null || p.statusVat.isBlank()) p.statusVat = trimOrEmpty(e.statusVat);
                computeVatAndBruttoForPosition(p);
            } else {
            // create an extra position representing the portion taken from this VAT entry
            DmsPosition extra = new DmsPosition();
            extra.netto = assigned.setScale(2, RoundingMode.HALF_UP).toPlainString(); // zachowuje znak
            extra.stawkaVat = assignedRate.isEmpty() ? "0" : assignedRate;
            p.stawkaVat = assignedRate.isEmpty() ? "0" : assignedRate;
            extra.statusVat = assignedStatus.isEmpty() ? "nie podlega" : assignedStatus;
            p.statusVat = assignedStatus.isEmpty() ? "nie podlega" : assignedStatus;
            extra.type = "AUTO_FROM_VAT";
            extra.setKwotyDodatkowe(new ArrayList<>());

            // compute VAT/brutto for the extra
            computeVatAndBruttoForPosition(extra);

            // add extra to pending extras in out (do not add directly to final list here)
            out.addPendingExtra(extra);
            log.info("Partial fallback created EXTRA: assigned=" + assigned + " rate=" + assignedRate + " (kept original p.netto=" + p.netto + ")" + "statusVat" + p.statusVat);
            log.info(String.format("7 assign RETURN [%s]: p.netto=%s p.stawkaVat=%s p.statusVat=%s p.vat=%s; VAT snapshot=%s",
            	    "PAIR_SUM", p.netto, p.stawkaVat, p.statusVat, p.vat, snapshotVatEntries(out.getVatEntries())));
            }
            // DO NOT change p.netto here; original position remains as in source
            
            return;
        }
        //log.info(String.format("assignVat END 1 snapshot stawka=%s, status=%s, p.stawkaVat=%s, p.statusVat=%s",entries.get(0).stawka,entries.get(0).statusVat,p.stawkaVat,p.statusVat));
        // 6) No available entries
        //p.stawkaVat = "0";
        //p.statusVat = "nie podlega";
        //log.info(String.format("assignVat END 2 snapshot stawka=%s, status=%s, p.stawkaVat=%s, p.statusVat=%s",entries.get(0).stawka,entries.get(0).statusVat,p.stawkaVat,p.statusVat));
     // 6) No available entries (improved fallback)
        DmsParsedDocument.DmsVatEntry fallbackEntry = null;
        // prefer any entry that still has a non-empty stawka/status
        if (entries != null) {
            for (DmsParsedDocument.DmsVatEntry ee : entries) {
                if (ee == null) continue;
                if (ee.stawka != null && !ee.stawka.isBlank()) { fallbackEntry = ee; break; }
            }
        }
        // if none found, try document-level vatRate/status set earlier (out.getVatRate/out.getStatusVat)
        if (fallbackEntry != null) {
            p.stawkaVat = trimOrEmpty(fallbackEntry.stawka);
            p.statusVat = trimOrEmpty(fallbackEntry.statusVat);
            log.info("NO_AVAILABLE_FALLBACK used entry: stawka=" + fallbackEntry.stawka + " status=" + fallbackEntry.statusVat);
        } else if (out != null && out.getVatRate() != null && !out.getVatRate().isBlank()) {
            p.stawkaVat = trimOrEmpty(out.getVatRate());
            p.statusVat = out.getStatusVat() == null || out.getStatusVat().isBlank() ? "nie podlega" : trimOrEmpty(out.getStatusVat());
            log.info("NO_AVAILABLE_FALLBACK used document-level vatRate: " + out.getVatRate());
        } else {
            // last resort: truly no info available
            p.stawkaVat = "0";
            p.statusVat = "nie podlega";
            log.info("NO_AVAILABLE_FALLBACK: no entry or document vat info, setting 0%");
        }

        // compute VAT/brutto according to hasVat
        if (!hasVat) {
            double nettoVal = p.netto != null && !p.netto.isBlank() ? parseDoubleSafe(p.netto.replace(",", ".")) : 0.0;
            p.vat = "0.00";
            p.brutto = String.format(Locale.US, "%.2f", nettoVal);
        } else {
            computeVatAndBruttoForPosition(p);
        }
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



    // ------------------------------
    // VAT (typ 06)
    /// FVU_1/200/21/00001/2026 <dane lp="1" kod="99" stawka="0.00"> UE 0%
    /// 1/320/01/00001/2026 <dane lp="1" kod="51" stawka="0.00"> ZW status "zwolniona" Status stawki VAT. Wartość wymagana. Przyjmuje wartości: opodatkowana, zwolniona, zaniżona, nie podlega
    // ------------------------------
    private void extractVat(Document doc, DmsParsedDocument out) {
        if (doc == null || out == null) return;
        NodeList list = doc.getElementsByTagName("document");
        boolean foundVat = false;
        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);
            if (!"06".equals(el.getAttribute("typ"))) continue;
            NodeList daneNodes = el.getElementsByTagName("dane");
            for (int j = 0; j < daneNodes.getLength(); j++) {
                Map<String, String> vatRates = new HashMap<>();
                Element dane = (Element) daneNodes.item(j);
                Element wart = firstElementByTag(dane, "wartosci");
                
                String kodVat = safeAttr(dane, "kod");
                String stawka = "";//safeAttr(dane, "stawka");
                String podstawa = wart != null ? safeAttr(wart, "podstawa") : "";
                String vat = wart != null ? safeAttr(wart, "vat") : "";
                String statusVat="";
                if ("02".equals(kodVat)) {
                    statusVat = "opodatkowana";
                    stawka = "23";
                } else if ("22".equals(kodVat)) {
                	statusVat = "opodatkowana";
                	stawka = "8";
                } else if ("99".equals(kodVat)) {
                	statusVat = "opodatkowana";
                	stawka = "0";
                } else if ("41".equals(kodVat)) {
                	statusVat = "nie podlega";
                	stawka = "0";
                } else if ("51".equals(kodVat)) {
                	statusVat = "zwolniona";
                	stawka = "0";
                } else {
                	statusVat = "nie podlega";
                	stawka = "0";
                }
                out.setVatRate(normalizeVatRate(stawka));
                out.setStatusVat(statusVat);
                out.setVatBase(podstawa);
                out.setVatAmount(vat);
                DmsVatEntry entry = new DmsVatEntry();
                entry.statusVat = statusVat;
                entry.stawka = stawka;//safeAttr(dane, "stawka");
                entry.podstawa = wart != null ? safeAttr(wart, "podstawa") : "";
                entry.vat = wart != null ? safeAttr(wart, "vat") : "";
                entry.podstawaParsed = parseAmount(entry.podstawa); // BigDecimal or null
                if (entry.podstawaParsed == null) entry.podstawaParsed = BigDecimal.ZERO;
                entry.remainingPodstawa = entry.podstawaParsed; // will be decreased by assignVatToPosition
                entry.consumed = entry.remainingPodstawa.compareTo(BigDecimal.ZERO) == 0;
                out.getVatEntries().add(entry);

                //kodVat = safeAttr(dane, "kod");      // 02, 22
                //stawka = stawka;//safeAttr(dane, "stawka"); // 23.00, 8.00
                vatRates.put(kodVat, stawka);
                foundVat = true;
                
                log.info(String.format("exVat entry: stawka=%s podstawa=%s vat=%s status=%s", stawka, podstawa, vat, statusVat));
                //break;
            }
        }     

        out.setHasVatDocument(foundVat);
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
        String terminPlatnosci = "";
        double dSumAdvanceNet = 0.0;
        double dSumAdvanceVat = 0.0;
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
                if (kw < 0) { out.setKierunek("rozchód"); } else { out.setKierunek("przychód"); }
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
                p.setTerminPlatnosci(termin);
                terminPlatn = termin;
                //Element daty = firstElementByTag(dane, "daty");
                //String termin = daty != null ? safeAttr(daty, "data") : "";
                p.setTerminPlatnosci(termin);   // <-- KLUCZOWE
                //log.info("extractPayments typ40: terminPlatn='%s': "+ terminPlatn);

                Element klasyf = firstElementByTag(dane, "klasyfikatory");
                String forma = klasyf != null ? safeAttr(klasyf, "kod") : "";
                p.setForma(forma);

                Element rozs = firstElementByTag(dane, "rozszerzone");
                String nrRach = rozs != null ? safeAttr(rozs, "nr_rach") : "";
                p.setNrBank(nrRach);
             // 4) JEŚLI W ŚRODKU JEST TYP 43 → NADPISUJEMY TERMIN
                if (rozs != null) {
                    NodeList nestedDocs = rozs.getElementsByTagName("document");
                    for (int k = 0; k < nestedDocs.getLength(); k++) {
                        Element nestedDoc = (Element) nestedDocs.item(k);
                        if (!"43".equals(safeAttr(nestedDoc, "typ"))) continue;

                        Element dane43 = firstElementByTag(nestedDoc, "dane");
                        if (dane43 == null) continue;

                        Element daty43 = firstElementByTag(dane43, "daty");
                        if (daty43 == null) continue;

                        String dataOper = safeAttr(daty43, "data_operacji");
                        if (dataOper == null || dataOper.isBlank()) {
                            dataOper = safeAttr(daty43, "data"); // fallback
                        }

                        if (dataOper != null && !dataOper.isBlank()) {
                            p.setTerminPlatnosci(dataOper);   // <- NADPISANIE TERMINU DLA TEJ PŁATNOŚCI
                            terminPlatn = dataOper;
                            out.setTerminPlatnosci(dataOper); // <- opcjonalnie globalnie
                        }
                    }
                }
                //log.info("1 extractPayment Kierunek: " + p.getKierunek());
                //p.setKierunek("przychód");
                listOut.add(p);
                }
            }
         // SZUKAMY TERMINU W TYP 43 (rozliczenie z wyciągu)
            if ("43".equals(typ)) {
                NodeList dane43 = el.getElementsByTagName("dane");
                for (int j = 0; j < dane43.getLength(); j++) {
                    Element dane = (Element) dane43.item(j);
                    if (dane == null) continue;
                    if (dane.getParentNode() != el) continue;

                    Element daty43 = firstElementByTag(dane, "daty");
                    if (daty43 != null) {
                        String dataOperacji = safeAttr(daty43, "data_operacji");
                        if (!dataOperacji.isBlank()) {
                            // nadpisujemy termin płatności
                            terminPlatnosci = dataOperacji;
                            // ustawiamy w out (jeśli chcesz mieć globalnie)
                            //log.info("extractPayments typ43: dataOperacji='%s': "+ dataOperacji);
                            out.setTerminPlatnosci(dataOperacji);
                        }
                    }
                }
            }

         // ------------------------------
         // ZALICZKI (typ 45)
         // ------------------------------
            if ("45".equals(typ)) {
            	//double dSumAdvanceVat = 0;
            	//double dSumAdvanceNet = 0;
            	String sSumAdvanceVat = "";
            	String sSumAdvanceNet = "";
                //String kodVat = "";
                //String stawka = "";
            	//String statusVat="";
                NodeList daneList45 = el.getElementsByTagName("dane");
                for (int j = 0; j < daneList45.getLength(); j++) {
                	Element dane = (Element) daneList45.item(j);
                if (dane == null) continue;
                if (dane.getParentNode() != el) continue;
                Element wart = firstElementByTag(dane, "wartosci");
                //kodVat = safeAttr(dane, "kod_vat");
                if (wart == null) continue;
                // 🔥 Zapisz mapę VAT dla całego dokumentu
                /*if ("02".equals(kodVat)) {
                    statusVat = "opodatkowana";
                    stawka = "23";
                } else if ("22".equals(kodVat)) {
                	statusVat = "opodatkowana";
                	stawka = "8";
                } else if ("99".equals(kodVat)) {
                	statusVat = "nie podlega";
                	stawka = "0";
                } else if ("51".equals(kodVat)) {
                	statusVat = "zwolniona";
                	stawka = "0";
                } else {
                	statusVat = "nie podlega";
                	stawka = "0";
                }*/
             DmsPayment p = new DmsPayment();
             p.setIdPlatn(UUID.randomUUID().toString());
             p.setAdvance(true);
             String lp = safeAttr(dane, "lp");
             String bruttoStr = safeAttr(wart, "brutto");
             String nettoStr = safeAttr(wart, "netto");
             log.info(String.format("45 exPay 1: nettoStr='%s' lp='%s' isAdvance='%s'", nettoStr, lp, p.isAdvance()));
             double kw = parseDoubleSafe(bruttoStr);
             if (kw < 0) { out.setKierunek("rozchód"); } else { out.setKierunek("przychód"); }
             // zawsze dodatnia kwota płatności
             bruttoStr = String.format(Locale.US, "%.2f", Math.abs(kw));
             log.info(String.format("45 exPay 2: bruttoStr='%s': ,kw='%s': ", bruttoStr, kw));
             p.setKwota(bruttoStr);
             double kwn = parseDoubleSafe(nettoStr);
             dSumAdvanceNet = dSumAdvanceNet + kwn;
             log.info(String.format("45 exPay 3: dSumAdvanceNet='%s': ,kwn='%s': ", dSumAdvanceNet, kwn));
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
                 log.info(String.format("45 exPay 4: dSumAdvanceVat='%s': ,vatZaliczki='%s': ", dSumAdvanceVat, vatZaliczki));
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

             //log.info("2 extractPayment Kierunek: " + p.getKierunek());
            //listOut.add(p);
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
            out.setDokumentFiskalny("Tak");

        }
        if (out.getDokumentFiskalny() == null || out.getDokumentFiskalny().isEmpty()) {
            out.setDokumentFiskalny("Nie");
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
    private void extractCorrection(Document doc, DmsParsedDocument out) {
        NodeList list = doc.getElementsByTagName("document");

        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("92".equals(el.getAttribute("typ"))) {

                out.setKorekta("Tak");

                Element dane = firstElementByTag(el, "dane");
                if (dane == null) return;

                Element numerEl = firstElementByTag(dane, "numer");
                if (numerEl != null) {
                    String nr = trimOrEmpty(numerEl.getTextContent());
                    out.setKorektaNumer(nr);
                }

                return; // znaleźliśmy korektę, kończymy
            }
        }

        // brak typ 92 → brak korekty
        out.setKorekta("Nie");
        out.setKorektaNumer("");
    }

    // ------------------------------
    // Pomocnicze metody
    // ------------------------------
 // Tworzy pozycję z pozostałej podstawy VAT (dostosuj pola według potrzeb)
    /*private DmsPosition createPositionFromVatEntry(DmsParsedDocument.DmsVatEntry e) {
        BigDecimal remaining = e.remainingPodstawa == null ? BigDecimal.ZERO : e.remainingPodstawa;
        DmsPosition p = new DmsPosition();
        p.netto = remaining.toPlainString();
        p.stawkaVat = e.stawka != null ? e.stawka.trim() : "0";
        p.statusVat = e.statusVat != null ? e.statusVat.trim() : "nie podlega";
        p.type = "AUTO_FROM_VAT";
        p.setKwotyDodatkowe(new ArrayList<>());
        // teraz wyzeruj źródło
        e.remainingPodstawa = BigDecimal.ZERO;
        return p;
    }*/


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
    private static String normRate(String raw) {
        if (raw == null) return "";
        String s = raw.trim().replace("%", "");
        try {
            BigDecimal bd = new BigDecimal(s.replace(",", "."));
            bd = bd.stripTrailingZeros();
            return bd.toPlainString();
        } catch (Exception ex) {
            return s;
        }
    }
    private static double parseDoubleSafe(String s) {
        if (s == null) return 0.0;
        try {
            return Double.parseDouble(s.replace(",", "."));
        } catch (Exception ex) {
            return 0.0;
        }
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
    private String f2(double d) {
        return String.format(Locale.US, "%.2f", d);
    }
    private void addIfPositive(List<DmsKwotaDodatkowa> list, String valueStr, String category,  String category2, String kontoWn, String kontoMa, String opis, String opis1) {
        double val = parseDoubleSafe(valueStr);
        //log.info(String.format("DS AddKwoty61 or 66 val=%s", val));
        if (val != 0) {
            DmsKwotaDodatkowa kd = new DmsKwotaDodatkowa();
            kd.kwota = f2(val);
            kd.kategoria = category;
            kd.kategoria2 = category2;
            kd.kontoWn = kontoWn;
            kd.kontoMa = kontoMa;
            kd.opis = opis;
            kd.opis1 = opis1;
            list.add(kd);
        }
    }


    public class DmsKwotaDodatkowa {
        public String kwota;       // jedna kwota KD
        public String kategoria;   // KATEGORIA_KD
        public String kategoria2;  // opcjonalnie
        public String kontoMa;
        public String kontoWn;
        public String opis;
        public String opis1;

        public DmsKwotaDodatkowa() {
        }
    }
    
    public static String detectVatRateFromNumbers(double nettoD, double bruttoD) {
        return detectVatRateFromNumbers(BigDecimal.valueOf(nettoD), BigDecimal.valueOf(bruttoD));
    }

    public static String detectVatRateFromNumbers(BigDecimal netto, BigDecimal brutto) {
        if (netto == null || brutto == null) return "0";
        if (netto.compareTo(BigDecimal.ZERO) == 0) return "0";

        BigDecimal diff = brutto.subtract(netto);
        BigDecimal impliedPercent = diff.multiply(new BigDecimal("100"))
                                       .divide(netto, 6, RoundingMode.HALF_UP)
                                       .abs();

        if (impliedPercent.subtract(new BigDecimal("23")).abs().compareTo(PERCENT_TOL) <= 0) return "23";
        if (impliedPercent.subtract(new BigDecimal("8")).abs().compareTo(PERCENT_TOL) <= 0)  return "8";
        if (impliedPercent.subtract(new BigDecimal("5")).abs().compareTo(PERCENT_TOL) <= 0)  return "5";
        if (impliedPercent.compareTo(new BigDecimal("0.5")) <= 0)                            return "0";

        return impliedPercent.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
    private static BigDecimal parseAmount(String s) {
        if (s == null) return null;
        try {
            String cleaned = s.trim().replace(",", ".").replaceAll("\\s+", "");
            if (cleaned.isEmpty()) return null;
            return new BigDecimal(cleaned);
        } catch (Exception ex) {
            return null;
        }
    }
    private static boolean approxEqualsAbs(BigDecimal a, BigDecimal b, BigDecimal tol) {
        if (a == null || b == null) return false;
        return a.abs().subtract(b.abs()).abs().compareTo(tol) <= 0;
    }
    private void computeVatAndBruttoForPosition(DmsPosition p) {
        BigDecimal netto = parseAmount(p.netto);
        if (netto == null) netto = BigDecimal.ZERO;
        //log.info("compute p.stawkaVat="+p.stawkaVat);
        String rateStr = trimOrEmpty(p.stawkaVat);
        //log.info("compute rateStr="+rateStr);
        BigDecimal rate;
        try {
            if (rateStr.isEmpty()) rate = BigDecimal.ZERO;
            else rate = new BigDecimal(rateStr.replace(",", "."));
        } catch (Exception ex) {
            rate = BigDecimal.ZERO;
        }

        // VAT = netto * rate / 100  (zachowuje znak netto)
        BigDecimal vat = netto.multiply(rate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        BigDecimal brutto = netto.add(vat);

        p.vat = vat.setScale(2, RoundingMode.HALF_UP).toPlainString();
        p.brutto = brutto.setScale(2, RoundingMode.HALF_UP).toPlainString();

        // dodatkowo ustaw pola systemowe jeśli masz NETTO_SYS itp.
        p.netto = netto.setScale(2, RoundingMode.HALF_UP).toPlainString();
        // (opcjonalnie) ustaw NETTO_SYS, VAT_SYS, NETTO_SYS2, VAT_SYS2 analogicznie
    }
    private String snapshotVatEntries(List<DmsParsedDocument.DmsVatEntry> entries) {
        if (entries == null) return "[]";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (DmsParsedDocument.DmsVatEntry e : entries) {
            if (i++ >= 5) break;
            sb.append(String.format("[%d:%s/%s]", i-1, e == null ? "null" : e.stawka, e == null ? "null" : e.remainingPodstawa));
        }
        return sb.toString();
    }
 // helper: infer rate from position if stawkaVat is empty (returns normalized string like "23" or "" if cannot infer)
    private String inferRateFromPosition(DmsPosition p) {
        try {
            if (p == null) return "";
            String sNet = trimOrEmpty(p.netto);
            String sVat = trimOrEmpty(p.vat);
            if (sNet.isEmpty() || sVat.isEmpty()) return "";
            BigDecimal net = parseAmount(sNet);
            BigDecimal vat = parseAmount(sVat);
            if (net == null || vat == null || net.compareTo(BigDecimal.ZERO) == 0) return "";
            // rate = vat / net * 100
            BigDecimal rate = vat.multiply(new BigDecimal("100")).divide(net, 0, RoundingMode.HALF_UP);
            return rate.toPlainString();
        } catch (Exception ex) {
            return "";
        }
    }


}
