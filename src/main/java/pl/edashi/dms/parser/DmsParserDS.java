package pl.edashi.dms.parser;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.common.logging.AppLogger.LogUtils;

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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

public class DmsParserDS implements DmsParser{
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
        boolean hasNumberInDane = false;

        boolean found = DocumentNumberExtractor.extractFromGenInfo(root, out, fileName,hasNumberInDane);
     // jeśli AppLogger ma metodę do pobrania wewnętrznego loggera:
        //org.slf4j.Logger slf = org.slf4j.LoggerFactory.getLogger(DmsParserDS.class);
        //log.info("SLF4J logger name = " + slf.getName());
        //log.info("AppLogger name = " + log.getName() + ", effectiveLevel = " + log.getEffectiveLevel());

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
        List<DmsPosition> positions = extractPositions(doc, out, kwoty66);
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
        if ("Tak".equalsIgnoreCase(out.getDokumentFiskalny())
                && (c == null || c.getNip() == null || c.getNip().isBlank())) {

            // jeśli masz akronim w Contractor:
            if (c != null && c.getFullName().isBlank()) {
                c.setFullName("SPRZEDAZ_PARAGONOWA");
                c.setName1("Sprzedaż Paragonowa");
            }
        }
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
                c.nip = rozs.getAttribute("nip");
                c.name1 = rozs.getAttribute("nazwa1");
                c.name2 = rozs.getAttribute("nazwa2");
                c.name3 = rozs.getAttribute("nazwa3");
                c.country = rozs.getAttribute("kod_kraju");
                c.city = rozs.getAttribute("miejscowosc");
                c.zip = rozs.getAttribute("kod_poczta");
                c.street = rozs.getAttribute("ulica");

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
        c.nip = ""; 
        c.name1 = ""; 
        c.name2 = ""; 
        c.name3 = ""; 
        c.fullName = ""; 
        return c;
    }

    // ------------------------------
    // POZYCJE 
    // ------------------------------
    private List<DmsKwotaDodatkowa> extractKwoty66(Document doc) {
        List<DmsKwotaDodatkowa> kwoty66 = new ArrayList<>();
        NodeList allDocs = doc.getElementsByTagName("document");
        for (int k = 0; k < allDocs.getLength(); k++) {
    	    Element el = (Element) allDocs.item(k);
    	    //log.info("DS: document[" + k + "] typ=" + el.getAttribute("typ"));
    	    if ("66".equals(el.getAttribute("typ"))) {
    	        NodeList daneList66 = el.getElementsByTagName("dane");
    	        //log.info("DS: typ 66, daneList length = " + daneList66.getLength());
    	        for (int j = 0; j < daneList66.getLength(); j++) { 
    	        	Element dane = (Element) daneList66.item(j); 
    	        	Element wart = (Element) dane.getElementsByTagName("wartosci").item(0); 
    	        	Element rozs = (Element) dane.getElementsByTagName("rozszerzone").item(0);
    	        	//log.info("DS: typ 66, wart node = " + (wart != null));
	        	if (wart == null) continue;
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
        } }
        log.info("DS: extractKwoty66 -> size=" + kwoty66.size());
        return kwoty66;
    }

    private List<DmsPosition> extractPositions(Document doc, DmsParsedDocument out, List<DmsKwotaDodatkowa> kwoty66){
    	boolean hasType66 = !kwoty66.isEmpty();
    	log.info(String.format("1 kwoty66 p.getKwotyDodatkowe()='%s': ", kwoty66));
        List<DmsPosition> listOut = new ArrayList<>();
        //if (doc == null || out == null) return listOut;
        NodeList list = doc.getElementsByTagName("document");
        // 1) Zbierz pozycje (03/04/05)
        for (int i = 0; i < list.getLength(); i++) {
            Element document = (Element) list.item(i);
            String typ = document.getAttribute("typ");
            if (!"03".equals(typ) && !"04".equals(typ) && !"05".equals(typ) ) continue;
            log.info(String.format("1 kwoty66 p.getKwotyDodatkowe()='%s': typ='%s '", kwoty66, typ));
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
                log.info(String.format("1 kwoty66 p.getKwotyDodatkowe()='%s': typ='%s '", p.getKwotyDodatkowe(), typ));
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
                //log.info(LogUtils.safeFormat("hasVat=%s, vatRate=%s", hasVat, vatRate));
                if (!hasVat) {
                	double nettoVal = 0.0;
                    p.stawkaVat = "0";
                    p.vat = "0.00";
                    p.statusVat = "opodatkowana";
                    if (p.netto != null && !p.netto.isBlank()) {
                        nettoVal = parseDoubleSafe(p.netto.replace(",", "."));
                    }
                    String bruttoStr = String.format(Locale.US, "%.2f", nettoVal);
                    p.brutto = bruttoStr;
                    //p.brutto = String.format(Locale.US, "%.2f", p.netto);
                    //log.info(String.format("1 hasVat p.statusVat='%s': ", p.statusVat));
                } else {
                    p.stawkaVat = vatRate != null ? vatRate : "";
                    p.statusVat = "opodatkowana";
                    //log.info(String.format("2 hasVat p.statusVat='%s': ", p.statusVat));
                    if (p.netto != null && !p.netto.isBlank()) {
                        try {
                            double netto = parseDoubleSafe(p.netto);
                            double vat = netto * (parseDoubleSafe(vatRate) / 100.0);
                            double brutto = netto + vat;
                            p.brutto = String.format(Locale.US, "%.2f", brutto);
                            p.vat = String.format(Locale.US, "%.2f", vat);
                            log.info(String.format("3 hasVat p.statusVat='%s': ", p.statusVat));
                        } catch (Exception ex) {
                            p.vat = "0.00";
                        }
                    } else {
                        p.vat = "0.00";
                    }
                }
                p.kategoria = "PRZYCHODY";
                String readRejestr = out.getDaneRejestr();
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
            	 log.info(String.format("22 kwoty66 netto_zakup='%s': isRejCzesciSklep='%s '", wart.getAttribute("netto_zakup"), isRejCzesciSklep));
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
             log.info(String.format("5kategoria='%s': ", p.kategoria));
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
            advPos.statusVat = "opodatkowana";
            advPos.setStawkaVat("23"); // jeśli znasz stawkę VAT, wpisz ją
            try { advPos.setAdvance(true); } catch (Throwable ignored) {}
            // Dodajemy zaliczkę na koniec listy pozycji
            listOut.add(advPos);
            log.info("extractPositions Zaliczka: appended DmsPosition advance -> netto=" + advPos.getNetto() + " vat=" + advPos.getVat() + " brutto=" + advPos.getBrutto());
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

            log.info(String.format("5 extractPositions: correctedVat='{}', correctedNet='{}'", vat, net));
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

            if ("06".equals(el.getAttribute("typ"))) {

                Element dane = firstElementByTag(el, "dane");
                if (dane == null) continue;
                Element wart = firstElementByTag(dane, "wartosci");

                String stawka = safeAttr(dane, "stawka");
                String kodVat = safeAttr(dane, "kod");
                String podstawa = wart != null ? safeAttr(wart, "podstawa") : "";
                String vat = wart != null ? safeAttr(wart, "vat") : "";
                
             // Ustal status VAT wg kodu i stawki
               /* if ("51".equals(kodVat)) {
                    // 51 = ZW
                    out.setStatusVat("zwolniona");
                    out.setVatRate("ZW");
                } 
                else if ("99".equals(kodVat) && "0.00".equals(stawka)) {
                    // 99 + 0.00 = 0% (ale nie ZW)
                    out.setStatusVat("opodatkowana");
                    out.setVatRate("0.00");
                }
                else if ("02".equals(kodVat)) {
                    // 02 = 23%
                    out.setStatusVat("opodatkowana");
                    out.setVatRate("23.00");
                }
                else {
                    // fallback – normalizacja stawki
                    out.setStatusVat(stawka);
                    out.setVatRate(normalizeVatRate(stawka));
                }*/

                out.setVatRate(normalizeVatRate(stawka));
                out.setVatBase(podstawa);
                out.setVatAmount(vat);
                //log.info(String.format("extractVat: podstawa='%s': ,vat='%s': ", podstawa, vat));
                foundVat = true;
                break;
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
             log.info(String.format("1 extractPayments: nettoStr='%s': ,lp='%s': ", nettoStr, lp));
             double kw = parseDoubleSafe(bruttoStr);
             if (kw < 0) { out.setKierunek("rozchód"); } else { out.setKierunek("przychód"); }
             // zawsze dodatnia kwota płatności
             bruttoStr = String.format(Locale.US, "%.2f", Math.abs(kw));
             log.info(String.format("2 extractPayments: bruttoStr='%s': ,kw='%s': ", bruttoStr, kw));
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
                 log.info(String.format("3 extractPayments: dSumAdvanceVat='%s': ,vatZaliczki='%s': ", dSumAdvanceVat, vatZaliczki));
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

   /* public class DmsKwotaDodatkowa {
    	public String nettoDlRob;
    	public String nettoKoMat;
    	public String nettoGwMat;
    	public String nettoKoszt;
    	public String nettoZakup;
        public String kategoria;
        public String kategoria2;
    	public String kontoMa;
    	public String kontoWn;

        public DmsKwotaDodatkowa(String nettoDlRob, String nettoKoMat,String nettoGwMat,String nettoKoszt, String nettoZakup, String kategoria, String kategoria2,String kontoWn, String kontoMa) {
        	this.nettoDlRob = nettoDlRob;
        	this.nettoKoMat = nettoKoMat;
        	this.nettoGwMat = nettoGwMat;
        	this.nettoKoszt = nettoKoszt;
        	this.nettoZakup = nettoZakup;
            this.kategoria = kategoria;
            this.kategoria2 = kategoria2;
            this.kontoWn = kontoWn;
            this.kontoMa = kontoMa;
        }
    }*/



}
