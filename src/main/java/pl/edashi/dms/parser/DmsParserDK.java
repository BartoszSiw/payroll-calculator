package pl.edashi.dms.parser;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.common.util.MappingIdDocs;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.model.DmsPayment;
import pl.edashi.dms.model.DocumentMetadata;
import pl.edashi.dms.model.MappingTarget;
import pl.edashi.dms.model.Contractor;
import pl.edashi.dms.model.DmsRapKasa;
import org.w3c.dom.*;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
/**
 * Parser DK.xml
 * - wyciąga główny numer dokumentu (z elementu <numer> w <dane>)
 * - wyciąga gen_doc_id z atrybutu DMS (jeśli obecny)
 * - wyciąga powiązane numery z zagnieżdżonych dokumentów (np. pozycje dowodu kasowego -> numer wewnętrzny DS)
 * - wyciąga kwotę z <wartosci kwota="..."> jeśli występuje
 */
public class DmsParserDK implements DmsParser{
	private final AppLogger log = new AppLogger("DmsParserDK");
	public DmsParsedDocument parse(Document doc, String fileName) {
    //public DmsParsedDocument parse(InputStream xmlStream, String sourceFileName) throws Exception {

        DmsParsedDocument out = new DmsParsedDocument();
        out.setDocumentType("DK");
        out.setSourceFileName(fileName);
        Contractor c = extractContractor(doc);
        out.setContractor(c);
        out.setTypDocAnalizer("DK");
        /*log.info(String.format(Locale.US,
                "ParserDZ: END file=%s type=%s nr=%s positions=%d",
                fileName, out.getDocumentType(), out.getInvoiceShortNumber(), out.getPositions().size()));*/

        String code = "K";
        out.setMappingTarget(MappingTarget.fromCode(code));
        Element root = doc.getDocumentElement();
        Element document = (Element) doc.getElementsByTagName("document").item(0);
        //Element numer = (Element) document.getElementsByTagName("numer").item(0);
        String genDocId = safeAttr(root, "gen_doc_id");
        String id = safeAttr(root, "id");
        String trans = safeAttr(root, "trans");
        
        // ============================
        // 1. METADATA
        // ============================

        if (!isEmpty(genDocId)) out.setId(genDocId);
        else if (!isEmpty(id)) out.setId(id);
        Element daty = firstElementByTag(doc, "daty");
        Element warto = (Element) doc.getElementsByTagName("wartosci").item(0);
        String data = daty != null ? safeAttr(daty, "data") : "";
        try {
            NodeList docList = doc.getElementsByTagName("document");
            for (int i = 0; i < docList.getLength(); i++) {
                Element docEl = (Element) docList.item(i);
                if ("02".equals(docEl.getAttribute("typ"))) {
                    Element daneEl = firstElementByTag(docEl, "dane");
                    if (daneEl != null && daneEl.hasAttribute("kasa")) {
                        String kasa = daneEl.getAttribute("kasa").trim();
                        String oddzial = daneEl.getAttribute("oddzial").trim();
                        //log.info(String.format("Parser DK kasa='%s ' oddzial='%s '  ", kasa, oddzial));
                        out.setDaneRejestr(kasa); // upewnij się, że DmsParsedDocument ma setter
                    	out.setOddzial(oddzial);}
                    break; // zwykle tylko jeden rekord 02
                }
            }
        } catch (Exception ex) {
            log.warn("Parser DK: nie udało się odczytać kasa: " + ex.getMessage());
        }
        DocumentMetadata metadata = new DocumentMetadata(
                !isEmpty(genDocId) ? genDocId : "",
                !isEmpty(id) ? id : "",
                !isEmpty(trans) ? trans : "",
                fileName != null ? fileName : "",
                data,
                data,
                data,
                data,
                warto.getAttribute("waluta")
        );
        out.setMetadata(metadata);
        // ============================
        // 2. KONTRAHENT (typ 35)
        // ============================

        // ============================
        // 6. POZYCJE (typ 49)
        // ============================
     // przykład w parserze DK, przed extractPositions48(...) lub wewnątrz obsługi typ 48
        /*Optional<String> kpKw = extractKpKwNumberForDK(doc, out);
        if (kpKw.isPresent()) {
            String kpw = kpKw.get();
            // ustaw w dokumencie DK (jeśli chcesz, żeby dokument DK miał pole dowodu)
            out.setDowodNumber(kpw); // lub out.setReportNumberPosDowod(kp) — zależnie od modelu
            log.info("Parser DK: znaleziono numer KP/KW = '" + kpw + "' file=" + out.getSourceFileName());
        }*/

        extractAndSetReportAndDowodNumbers(doc, out);
        List<DmsRapKasa> rapKasa = extractPositionsDK(doc, out);
        out.setRapKasa(rapKasa);
     // w parserze DK, po wyciągnięciu pozycji
        /*String raw = rapKasa.get(0).getReportNumberPos(); // lub getNrRKB()
        String attrNr = rapKasa.get(0).getNrRKB();
        if (raw != null) raw = raw.trim();
        out.setReportNumber(raw); // BEZ dalszej normalizacji
        out.setReportNumberPos(raw);
        out.setNrRKB(attrNr);*/
        //log.info("Last Parser DK ustawiono NrDokumentu='" + out.getNrDokumentu()+ "'getReportNumber='" + out.getReportNumber() + "' out.reportNumberPos='" + out.getReportNumberPos() + "' file= '" + out.getSourceFileName());
        log.info("Last Parser DK ustawiono getRapKasa='" + out.getRapKasa());
        //log.info("DK parser: ustawiono out.reportNumber (raw) = '" + raw + "' file=" + out.getSourceFileName());



        return out;
    }
    // ------------------------------
    // KONTRAHENT (typ 35)
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
        c.setNip(""); 
        c.name1 = ""; 
        c.name2 = ""; 
        c.name3 = ""; 
        c.fullName = ""; 
        return c;
    }
    // ------------------------------
    // PŁATNOŚCI (typ 48 + 49)
    // ------------------------------
    /*private List<DmsPayment> extractPayments(Document doc, DmsParsedDocument out) {

        List<DmsPayment> listOut = new ArrayList<>();
        NodeList list = doc.getElementsByTagName("document");

        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("48".equals(el.getAttribute("typ"))) {

                Element dane48 = firstElementByTag(el, "dane");
                if (dane48 == null) continue;

                // kwota
                Element wart = firstElementByTag(dane48, "wartosci");
                String kwota = wart != null ? safeAttr(wart, "kwota") : "";

                // data zatwierdzenia
                Element daty = firstElementByTag(dane48, "daty");
                String date = daty != null ? safeAttr(daty, "data_zatw") : "";

                // forma płatności (kod)
                Element klasyf = firstElementByTag(dane48, "klasyfikatory");
                String forma = klasyf != null ? safeAttr(klasyf, "kod") : "";

                // operator
                Element operator = firstElementByTag(dane48, "operatorzy");
                String operatorName = operator != null ? safeAttr(operator, "nazwa") : "";

                // pozycje (typ 49) - opis/relacje
                String opis = extractPaymentDescription(dane48);

                DmsPayment p = new DmsPayment();
                p.setKwota(kwota);
                p.setTermin(date);
                p.setForma(forma);
                p.setKierunek(out.getKierunek());
                p.setOpis(opis);
                p.setOperatorName(operatorName);
                p.setAdvance(false);
                listOut.add(p);
            }
        }

        return listOut;
    }*/


    private String extractPaymentDescription(Element dane48) {
        NodeList docs = dane48.getElementsByTagName("document");

        for (int i = 0; i < docs.getLength(); i++) {
            Element el = (Element) docs.item(i);

            if ("49".equals(el.getAttribute("typ"))) {
                Element dane49 = firstElementByTag(el, "dane");
                if (dane49 == null) continue;
                Element rozs = firstElementByTag(dane49, "rozszerzone");
                
                if (rozs != null && rozs.hasAttribute("opis1")) {
                	return safeAttr(rozs, "opis1");
                }
                // jeśli numer jest bezpośrednio w <numer> wewnątrz 49
                Element numer = firstElementByTag(dane49, "numer");
                if (numer != null) {
                    String txt = numer.getTextContent();
                    if (txt != null && !txt.trim().isEmpty()) return txt.trim();
                    String attrNr = safeAttr(numer, "nr");
                    if (attrNr != null && !attrNr.isEmpty()) return attrNr;
                }
            }
        }
        return "";
    }

    // ------------------------------
    // UWAGI (operator + opis pozycji)
    // ------------------------------
    private List<String> extractNotes(Document doc) {

        List<String> notes = new ArrayList<>();

        // operator z typ 02 (pierwsze dane)
        Element dane02 = firstElementByTag(doc, "dane");
        if (dane02 != null) {
            Element operator = firstElementByTag(dane02, "operatorzy");
            if (operator != null) {
                String name = safeAttr(operator, "nazwa");
                if (name != null && !name.isEmpty()) notes.add("Operator: " + name);
            }
        }
        return notes;
    }

    private List<DmsRapKasa> extractPositions48(Document doc, List<DmsRapKasa> list,Contractor contractor) {
    	if (doc == null) return list;
    	NodeList docs = doc.getElementsByTagName("document");

    	for (int i = 0; i < docs.getLength(); i++) {
            Element el = (Element) docs.item(i);
            if (!"48".equals(el.getAttribute("typ"))) continue;
            Element dane48 = firstDirectChild(el, "dane");
            if (dane48 == null) continue;
            DmsRapKasa r = new DmsRapKasa();
            r.setContractor(contractor);
         // 1) klasyfikacja Z/W na poziomie typ 48 (szkielet; pozycje typ 49 ustawiają kierunek osobno)
            Element klas = firstDirectChild(dane48, "klasyfikatory");
            if (klas != null) {
                String kk = safeAttr(klas, "klasyfikacja").trim();
                String kier = "Z".equalsIgnoreCase(kk) ? "przychód" : "rozchód";
                r.setKierunek(kier);
            }

            // 2) numer raportu kasowego (kod_dok="01")
            Element numEl = firstDirectChild(dane48, "numer");
            if (numEl != null && "01".equals(numEl.getAttribute("kod_dok"))) {
            	String rawNumber = numEl.getTextContent().trim();
            	String attrNr = numEl.getAttribute("nr");
            	//r.setNrDokumentu(rawNumber);
                //r.setNrRKB(removeLeadingZeroInFirstSegment(attrNr)); // np. 01/00001/2026
                //r.setReportNumber(removeLeadingZeroInFirstSegment(rawNumber));
                //r.setReportNumberPos(removeLeadingZeroInFirstSegment(rawNumber));
                r.setDowodNumber(removeLeadingZeroInFirstSegment(rawNumber));
            }

            // 3) kwota KP/KW
            /*Element wart = firstDirectChild(dane48, "wartosci");
            if (wart != null) {
            	String kwoRk = safeAttr(wart, "kwota");
                //r.setKwotaRk(kwoRk);
            }*/

            // 4) opis płatności (z typ 49)
            /*Element rozs = firstDirectChild(dane48, "rozszerzone");
            if (rozs != null) {
                Element doc49 = firstDirectChild(rozs, "document");
                if (doc49 != null && "49".equals(doc49.getAttribute("typ"))) {
                    Element dane49 = firstDirectChild(doc49, "dane");
                    if (dane49 != null) {
                        Element rozs49 = firstDirectChild(dane49, "rozszerzone");
                        if (rozs49 != null) {
                            //r.opis = safeAttr(rozs49, "opis1");
                        }
                    }
                }
            }*/
            log.info(String.format("Pos 48 DK nrDokumentu='%s ' getDowodNumber='%s ' kwotaRk='%s ' getNrRKB='%s ' kierunek='%s '", r.getNrDokumentu() ,r.getDowodNumber(), r.getKwotaRk(), r.getNrRKB(), r.getKierunek()));
            list.add(r);
        }
    	return list;
    }
    private void extractPositions49(Document doc, List<DmsRapKasa> list,DmsParsedDocument out) {
        if (doc == null) return;
        NodeList docs = doc.getElementsByTagName("document");

        // indeks wskazujący na kolejny skeleton do uzupełnienia
        int skeletonIndex = 0;

        for (int i = 0; i < docs.getLength(); i++) {
            Element docEl = (Element) docs.item(i);
            if (docEl == null || !"49".equals(docEl.getAttribute("typ"))) continue;

            NodeList daneList = docEl.getElementsByTagName("dane");
            for (int j = 0; j < daneList.getLength(); j++) {
                Element dane49 = (Element) daneList.item(j);
                if (dane49 == null) continue;

                // odczyt pól z 49
                String lpAttr = dane49.getAttribute("lp"); 
                String opis1 = null;
             // ---- tutaj wstawione: odczyt lp_porz i fallback na indeks ----
                Element rozs = firstDirectChild(dane49, "rozszerzone");
                if (lpAttr == null || lpAttr.isBlank()) {
                    if (rozs != null) {
                        String lpPorz = rozs.getAttribute("lp_porz");
                        if (lpPorz != null && !lpPorz.isBlank()) {
                            lpAttr = lpPorz.trim();
                        }
                    }
                } else {
                	opis1 = rozs.getAttribute("opis1");
                	if (rozs != null) {
                		if (opis1 != null && !opis1.isBlank()) {
                            opis1 = opis1.trim();
                        }
                	}
                	
                }
                if (lpAttr == null || lpAttr.isBlank()) {
                    // fallback: użyj pozycji w dokumencie (1-based)
                    lpAttr = String.valueOf(j + 1);
                }
                String kwRk = null;
                Element wart49 = firstDirectChild(dane49, "wartosci");
                if (wart49 != null) {
                    kwRk = safeAttr(wart49, "kwota");
                }

                String numer49 = null;
                Element num49 = firstDirectChild(dane49, "numer");
                if (num49 != null) {
                    String txt = num49.getTextContent();
                    if (txt != null && !txt.isBlank()) {
                        numer49 = txt;
                    } else {
                        String attrNr = num49.getAttribute("nr");
                        //if (attrNr != null && !attrNr.isBlank()) nr = attrNr;
                    }
                }
                // Kierunek: osobno dla każdej pozycji typ 49 — Z/W z linii lub z nadrzędnego typ 48; ujemna kwota → wartość dodatnia + odwrócenie kierunku
                Element klas49 = firstDirectChild(dane49, "klasyfikatory");
                String kierunekLine = resolveKierunekForTyp49(dane49, klas49);
                boolean invertKpdKwd = false;
                if (kwRk != null && !kwRk.isBlank()) {
                    double v = parseKwotaDouble(kwRk);
                    if (v < 0) {
                        kwRk = String.format(Locale.US, "%.2f", Math.abs(v));
                        kierunekLine = invertKierunek(kierunekLine);
                        invertKpdKwd = true; // kod 01/02 z XML → w assemblerze zamiana KPD↔KWD razem z kierunkiem
                    }
                }
                // weź kolejny skeleton z listy (jeśli jest), inaczej utwórz nowy
                DmsRapKasa match = null;
                if (lpAttr != null && !lpAttr.isBlank()) {
                    for (DmsRapKasa p : list) {
                        String existingLp = p.getLp();
                        if (lpAttr.equals(existingLp)) {
                            match = p;
                            break;
                        }
                    }
                }
                if (match == null) {
                    if (skeletonIndex < list.size()) {
                        match = list.get(skeletonIndex);
                    } else {
                        match = new DmsRapKasa();
                        list.add(match);
                    }
                    skeletonIndex++; // przesuwamy wskaźnik na następny skeleton
                }

                // ustawiamy pola z 49 (w Twoim przypadku nie nadpisują pól z 48)
                if (kwRk != null && !kwRk.isBlank()) match.setKwotaRk(kwRk);
                if (numer49 != null && !numer49.isBlank()) match.setNrDokumentu(numer49);
                match.setLp(lpAttr);
                match.setOpis1(opis1);
                match.setKierunek(kierunekLine);
                match.setInvertKpdKwdSymbol(invertKpdKwd);
                applyDowodFromAncestorTyp48(dane49, match);

                //match.setNrDokKasowego(lpAttr);
                // opcjonalny log debugowy
                log.info(String.format("49 DK-> filled skeleton id='%s' nrDokumentu='%s' kwotaRk='%s' opis1='%s ' kierunek='%s '",
                         System.identityHashCode(match), match.getNrDokumentu(), match.getKwotaRk(), match.getOpis1(), match.getKierunek()));
            }
        }
    }

    private List<DmsRapKasa> extractPositionsDK(Document doc, DmsParsedDocument out) {
  String podmiot ="";
        Contractor co = extractContractor(doc);
        out.setContractor(co);
        /*if (cont != null) {
            if (cont.getNip() != null && !cont.getNip().isBlank()) {
                podmiot = cont.getNip();
            } else {
                podmiot = cont.getFullName() == null ? "" : cont.getFullName();
            }
            cont.setPodmiot(podmiot);
            // c.podmiot = podmiot;
        } else {
            podmiot = "";
        }*/
        if ("Tak".equalsIgnoreCase(out.getDokumentFiskalny())) {
       	 log.info(String.format("if 1 podmiot='%s' fullName='%s'", podmiot, co.fullName));
            // dokument fiskalny (paragon)
       	 if ((co.getNip() == null || co.getNip().isBlank()) 
       		        && (co.getFullName() == null || co.getFullName().isBlank())) {
                // brak NIP — traktujemy jako sprzedaż paragonowa
                String paragonName = "SPRZEDAZ_PARAGONOWA";
                if (co != null) {
                    // ustawiaj tylko jeśli nie nadpisujesz czegoś ważnego
                    co.setFullName(paragonName);
                    co.setName1("Sprzedaż Paragonowa");
                    co.setPodmiot(paragonName);
                }
                podmiot = paragonName;
            } else {
                // jest NIP lub full name 
           	 log.info(String.format("if 2 podmiot='%s' fullName='%s' nip='%s'", podmiot, co.fullName, co.getNip()));
           		 if (co.getNip() != null && !co.getNip().isBlank()) {
           		 podmiot = co.getNip().trim();
           		 log.info(String.format("if 3 podmiot='%s' fullName='%s' nip='%s'", podmiot, co.fullName, co.getNip()));
           		 co.setPodmiot(podmiot);
           	 } else {
                    podmiot = co.getFullName().trim();
                    log.info(String.format("if 4 podmiot='%s' fullName='%s'", podmiot, co.fullName));
                    co.setPodmiot(podmiot);
           	 }
                //if (c != null) c.setPodmiot(podmiot);
            }
        } else {
       	 log.info(String.format("else podmiot='%s'", podmiot));
            // nie jest dokumentem fiskalnym — preferuj NIP, potem fullName
            if (co != null && co.getNip() != null && !co.getNip().isBlank()) {
                podmiot = co.getNip().trim();
                co.setPodmiot(podmiot);
            } else if (co != null && co.getFullName() != null && !co.getFullName().isBlank()) {
                podmiot = co.getFullName().trim();
                co.setPodmiot(podmiot);
            } else {
         	   String paragonName = "SPRZEDAZ_PARAGONOWA";
               if (co != null) {
                   // ustawiaj tylko jeśli nie nadpisujesz czegoś ważnego
                   co.setFullName(paragonName);
                   co.setName1("Sprzedaż Paragonowa");
                   co.setPodmiot(paragonName);
               }
               podmiot = paragonName;
           }
            if (co != null) co.setPodmiot(podmiot);
        }
        out.setContractor(co);
        out.setPodmiot(podmiot);
	    // 1) Pozycje z typ="48"
	    List<DmsRapKasa> list = new ArrayList<>();
	    extractPositions48(doc,list,co);   // istniejące pozycje na poziomie 48
	    extractPositions49(doc,list,out);   // nowe: pozycje z dokumentów 49
	    //log.info(String.format("ext DK list='%s '", list));
	 // po zebraniu listy rapKasa (po extractPositions48 i extractPositions49)
	    String fallbackDowod = out != null ? out.getDowodNumber() : null;
	    if (fallbackDowod != null && !fallbackDowod.isBlank()) {
	        for (DmsRapKasa r : list) {
	            String dowod = r.getDowodNumber();
	            if (dowod == null || dowod.isBlank()) {
	                dowod = fallbackDowod;
	            }
	            if (dowod == null || dowod.isBlank()) {
	                continue;
	            }
	            // ustawiamy numer dowodu KP/KW w polach pozycji (najpierw z typ 48 nadrzędnego dla typ 49)
	        	r.setPodmiot(podmiot);
	            r.setDowodNumber(dowod); // pole używane do zapisu NR_RAPORTU / NR_DOWODU w XML
	            //r.setReportNumberPos(removeLeadingZeroInFirstSegment(dowod)); // jeśli chcesz formatowany wariant
	            //r.setReportNumber(removeLeadingZeroInFirstSegment(dowod));
	            String numerFa = dowod;
	            String nrIdPlat = MappingIdDocs.generateCandidate(podmiot, "D",numerFa, 36);
	            String fullKey = MappingIdDocs.buildFullKey(podmiot, numerFa);
	            String hash = MappingIdDocs.shortHashFromFullKey(fullKey, 6);
	            String docKey = MappingIdDocs.generateDocId(podmiot, "K" ,numerFa, 36);

	            r.setFullKey(fullKey);
	            out.setFullKey(fullKey);
	            r.setDocKey(docKey);
	            out.setDocKey(docKey);
	            r.setNrIdPlat(nrIdPlat);
	            out.setNrIdPlat(nrIdPlat);
	            r.setHash(hash);
	            out.setHash(hash);
	            List<DmsPayment> payId = out.getPayments();
	            if (payId != null && !payId.isEmpty()) {
	            	log.info("payments size="+out.getPayments().size());
	                payId.get(0).setIdPlatn(nrIdPlat); // setIdPlatn zwraca void — wykonaj to osobno
	            }
	            log.info("ext DK parser dowodNumber='" + dowod + "' do pozycji; nrDokumentu='" + r.getNrDokumentu() + "' file=" + out.getSourceFileName());
	        }
	    } else {
	        boolean anyLine = false;
	        for (DmsRapKasa r : list) {
	            String dowod = r.getDowodNumber();
	            if (dowod == null || dowod.isBlank()) {
	                continue;
	            }
	            anyLine = true;
	            r.setPodmiot(podmiot);
	            r.setDowodNumber(dowod);
	            String numerFa = dowod;
	            String nrIdPlat = MappingIdDocs.generateCandidate(podmiot, "D", numerFa, 36);
	            String fullKey = MappingIdDocs.buildFullKey(podmiot, numerFa);
	            String hash = MappingIdDocs.shortHashFromFullKey(fullKey, 6);
	            String docKey = MappingIdDocs.generateDocId(podmiot, "K", numerFa, 36);
	            r.setFullKey(fullKey);
	            out.setFullKey(fullKey);
	            r.setDocKey(docKey);
	            out.setDocKey(docKey);
	            r.setNrIdPlat(nrIdPlat);
	            out.setNrIdPlat(nrIdPlat);
	            r.setHash(hash);
	            out.setHash(hash);
	            log.info("ext DK parser dowodNumber='" + dowod + "' (tylko z linii) nrDokumentu='" + r.getNrDokumentu() + "' file=" + out.getSourceFileName());
	        }
	        if (!anyLine) {
	            log.info("ext DK parser: brak dowodNumber (out i pozycje) — nie przypisano do pozycji; file=" + out.getSourceFileName());
	        }
	    }

	    return list;

	 // sprawdź, czy istnieje document typ=03
	    /*boolean has03 = false;
	    NodeList allDocs = doc.getElementsByTagName("document");
	    for (int i = 0; i < allDocs.getLength(); i++) {
	        Element el = (Element) allDocs.item(i);
	        if ("03".equals(el.getAttribute("typ"))) {
	            has03 = true;
	            break;
	        }
	    }*/

	    // jeśli brak 03 i brak 50 → twórz pozycje z VAT 06
	    /*if (!has03 && list.isEmpty()) {
	        list = createPositionsFromVat06(doc);
	    }*/

	    // 2) Uzupełnienie z typ="03"
	    //apply03ToPositions(doc, list);

	    // 3) VAT z typ="06"
	    //applyVatToPositions(out, list);

	    // 4) Korekty
	   //applyCorrectionsDZ(out, list);

	}      
    private void extractAndSetReportAndDowodNumbers(Document doc, DmsParsedDocument out) {
    	if (doc == null || out == null) return;
        NodeList docs = doc.getElementsByTagName("document");
        //log.info("1 DK parser total nodes=" + (docs == null ? 0 : docs.getLength()));
     // 1) numer raportu (RKB) z pierwszego document typ="02"
        for (int i = 0; i < docs.getLength(); i++) {
            Element el = (Element) docs.item(i);
            if (el == null) continue;
            if ("02".equals(el.getAttribute("typ"))) {
                Element dane02 = firstDirectChild(el, "dane");
                if (dane02 != null) {
                    Element num02 = firstDirectChild(dane02, "numer");
                    if (num02 != null) {
                        String rep = num02.getTextContent();
                        String repRkNr = num02.getAttribute("nr");
                        repRkNr = repRkNr.trim();
                        out.setNrRep(repRkNr);
                        if (rep == null || rep.isBlank()) rep = num02.getAttribute("nr");
                        if (rep != null && !rep.isBlank()) {
                            rep = rep.trim();
                            out.setReportNumber(rep); // **numer raportu** dla assemblera
                            log.info("1 DK parser: ustawiono reportNumber (RKB) = '" + rep + "' file=" + out.getSourceFileName());
                        }
                    }
                }
                break; // tylko pierwszy document typ=02
            }
        }
        // Wiele bloków document typ="48" w jednym pliku: wcześniej brano tylko pierwszy (return) —
        // wtedy numer z kod_dok="01" (KPD) mógł zablokować właściwy "02" (KWD).
        String dowod02 = null;
        String nrRkb02 = null;
        String dowod01 = null;
        String nrRkb01 = null;
        String dowodAny = null;
        String nrRkbAny = null;
        for (int i = 0; i < docs.getLength(); i++) {
            Element el = (Element) docs.item(i);
            if (el == null) continue;
            if (!"48".equals(el.getAttribute("typ"))) continue;
            Element dane48 = firstDirectChild(el, "dane");
            if (dane48 == null) {
                log.info("1 Found document typ=48 but no direct <dane> child; file=" + out.getSourceFileName());
                continue;
            }
            Element numEl = firstDirectChild(dane48, "numer");
            if (numEl == null) {
                log.info("1 Found <dane> in typ=48 but no direct <numer>; file=" + out.getSourceFileName());
                continue;
            }
            String raw = numEl.getTextContent();
            if (raw == null || raw.isBlank()) {
                log.info("1 numer element empty in typ=48; file=" + out.getSourceFileName());
                continue;
            }
            raw = raw.trim();
            String repNrRKB = numEl.getAttribute("nr").trim();
            String kodDok = safeAttr(numEl, "kod_dok").trim();
            if (dowodAny == null) {
                dowodAny = raw;
                nrRkbAny = repNrRKB;
            }
            if ("02".equals(kodDok) && dowod02 == null) {
                dowod02 = raw;
                nrRkb02 = repNrRKB;
            } else if ("01".equals(kodDok) && dowod01 == null) {
                dowod01 = raw;
                nrRkb01 = repNrRKB;
            }
        }
        String chosen = dowod02 != null ? dowod02 : (dowod01 != null ? dowod01 : dowodAny);
        String nrRkbChosen = dowod02 != null ? nrRkb02 : (dowod01 != null ? nrRkb01 : nrRkbAny);
        if (chosen != null) {
            out.setDowodNumber(chosen);
            out.setNrRKB(nrRkbChosen);
            log.info(String.format("1 DK parser: ustawiono dowodNumber (KP/KW) = '%s' nrRKB='%s' (prefer kod_dok 02>01>pierwszy) file=%s",
                    chosen, nrRkbChosen, out.getSourceFileName()));
        }
    }

    // ------------------------------
    // Pomocnicze metody
    // ------------------------------
    /**
     * Numer dowodu KP/KW (np. {@code 02/00001/2026}) z {@code document typ="48"} obejmującego tę pozycję typ 49.
     * W jednym pliku DK może być wiele bloków 48 z różnym {@code kod_dok} — dokumentowy fallback z
     * {@link #extractAndSetReportAndDowodNumbers} nie wystarcza; assembler musi widzieć ten numer w
     * {@link DmsRapKasa#getDowodNumber()}.
     */
    private void applyDowodFromAncestorTyp48(Element dane49, DmsRapKasa match) {
        if (dane49 == null || match == null) {
            return;
        }
        Element doc48 = findAncestorDocumentOfType(dane49, "48");
        if (doc48 == null) {
            return;
        }
        Element dane48 = firstDirectChild(doc48, "dane");
        if (dane48 == null) {
            return;
        }
        Element numEl = firstDirectChild(dane48, "numer");
        if (numEl == null) {
            return;
        }
        String raw = numEl.getTextContent();
        if (raw == null || raw.isBlank()) {
            return;
        }
        match.setDowodNumber(raw.trim());
        String nrAttr = numEl.getAttribute("nr");
        if (nrAttr != null && !nrAttr.trim().isEmpty()) {
            match.setNrRKB(nrAttr.trim());
        }
    }

    private Element findAncestorDocumentOfType(Node start, String typ) {
        Node n = start;
        while (n != null) {
            if (n.getNodeType() == Node.ELEMENT_NODE && "document".equals(n.getNodeName())) {
                Element e = (Element) n;
                if (typ.equals(e.getAttribute("typ"))) return e;
            }
            n = n.getParentNode();
        }
        return null;
    }

    /** Jak przy typ 48: Z → przychód, każdy inny kod (W, DZ, …) → rozchód. */
    private static String kierunekFromTyp48Klasyfikatory(Element klas48) {
        if (klas48 == null) {
            return "";
        }
        String kk = safeAttr(klas48, "klasyfikacja").trim();
        return "Z".equalsIgnoreCase(kk) ? "przychód" : "rozchód";
    }

    /**
     * Kierunek dla pojedynczej pozycji typ 49: jawne Z/W z linii; DS/DZ i inne (bez Z/W na linii) —
     * ta sama reguła co nagłówek typ 48 (Z → przychód, W → rozchód). Powiązanie z dokumentem DS na linii
     * nie determinuje kierunku gotówki; liczy się klasyfikacja dowodu kasowego (typ 48).
     */
    private String resolveKierunekForTyp49(Element dane49, Element klas49) {
        if (klas49 != null) {
            String kk = safeAttr(klas49, "klasyfikacja").trim();
            if ("Z".equalsIgnoreCase(kk)) {
                return "przychód";
            }
            if ("W".equalsIgnoreCase(kk)) {
                return "rozchód";
            }
        }
        Element doc48 = findAncestorDocumentOfType(dane49, "48");
        if (doc48 == null) {
            return "";
        }
        Element dane48 = firstDirectChild(doc48, "dane");
        Element klas48 = dane48 != null ? firstDirectChild(dane48, "klasyfikatory") : null;
        return kierunekFromTyp48Klasyfikatory(klas48);
    }

    private static String invertKierunek(String k) {
        if (k == null || k.isBlank()) {
            return "";
        }
        String t = k.trim();
        if ("przychód".equalsIgnoreCase(t)) {
            return "rozchód";
        }
        if ("rozchód".equalsIgnoreCase(t)) {
            return "przychód";
        }
        return k;
    }

    private static double parseKwotaDouble(String s) {
        if (s == null || s.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(s.trim().replace(",", "."));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Element firstDirectChild(Element parent, String tag) {
        if (parent == null || tag == null) return null;
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() != Node.ELEMENT_NODE) continue;
            // preferuj localName (bez prefiksu), fallback na nodeName
            String local = n.getLocalName();
            String name = local != null ? local : n.getNodeName();
            if (tag.equals(name)) {
                return (Element) n;
            }
        }
        return null;
    }

    private String listAttributes(Element e) {
        NamedNodeMap at = e.getAttributes();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < at.getLength(); i++) {
            Node a = at.item(i);
            sb.append(a.getNodeName()).append("='").append(a.getNodeValue()).append("' ");
        }
        return sb.toString().trim();
    }

    private static Element firstElementByTag(Node parent, String tagName) {
        if (parent == null) return null;
        NodeList list = null;
        if (parent instanceof Document) {
            list = ((Document) parent).getElementsByTagName(tagName);
        } else if (parent instanceof Element) {
            list = ((Element) parent).getElementsByTagName(tagName);
        }
        if (list == null || list.getLength() == 0) return null;
        return (Element) list.item(0);
    }

    private static String safeAttr(Element el, String name) {
        if (el == null) return "";
        String v = el.getAttribute(name);
        return v != null ? v : "";
    }
    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Pomocnicza metoda do serializacji Document -> String (jeśli potrzebujesz)
     */
    private static String documentToString(Document doc) throws TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
 // mapowanie/normalizacja helper (używaj tej samej funkcji w parserach/assemblerze)
    private String normalizeReport(String s) {
        if (s == null) return null;
        String t = s.trim();
        // usuń prefiks rejestru "01/" jeśli chcesz porównywać KO i DK
        t = t.replaceFirst("^\\d{2}/", "");
        t = t.replaceAll("\\s+", "");
        return t.isEmpty() ? null : t;
    }
    private String removeLeadingZeroInFirstSegment(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return s;

        String[] parts = s.split("/", 3); // max 3 części, nie rozbijamy dalej
        if (parts.length >= 1) {
            String first = parts[0];
            // jeśli pierwszy segment zaczyna się od '0' i jest liczbą, usuń wiodące zera
            if (first.matches("^0+\\d+$")) {
                try {
                    int v = Integer.parseInt(first); // "01" -> 1, "001" -> 1
                    parts[0] = String.valueOf(v);
                } catch (NumberFormatException ex) {
                    // jeśli nie da się sparsować, zostaw oryginał
                    parts[0] = first;
                }
            }
        }
        // złącz z powrotem tylko tyle segmentów ile było
        if (parts.length == 1) return parts[0];
        if (parts.length == 2) return parts[0] + "/" + parts[1];
        return parts[0] + "/" + parts[1] + "/" + parts[2];
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
}

