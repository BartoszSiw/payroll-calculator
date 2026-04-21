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
import java.util.Map;
import java.util.stream.Collectors;
/**
 * Parser RD.xml
 * - wyciąga główny numer dokumentu (z elementu <numer> w <dane>)
 * - wyciąga gen_doc_id z atrybutu DMS (jeśli obecny)
 * - wyciąga powiązane numery z zagnieżdżonych dokumentów (np. pozycje dowodu kasowego -> numer wewnętrzny DS)
 * - wyciąga kwotę z <wartosci kwota="..."> jeśli występuje
 */
public class DmsParserRD implements DmsParser{
	private final AppLogger log = new AppLogger("DmsParserRD");
	public DmsParsedDocument parse(Document doc, String fileName) {
    //public DmsParsedDocument parse(InputStream xmlStream, String sourceFileName) throws Exception {

        DmsParsedDocument out = new DmsParsedDocument();
        out.setSourceFileName(fileName);
        Contractor c = extractContractor(doc);
        out.setContractor(c);
        out.setTypDocAnalizer("RD");
        String code = "C";
        out.setMappingTarget(MappingTarget.fromCode(code));
        Element root = doc.getDocumentElement();
        String xmlRootId = safeAttr(root, "id");
        out.setDocumentType(("DWP".equalsIgnoreCase(xmlRootId) || "DWW".equalsIgnoreCase(xmlRootId)) ? "DWP" : "RD");
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
                        //log.info(String.format("Parser RD kasa='%s ' oddzial='%s '  ", kasa, oddzial));
                        out.setDaneRejestr(kasa); // upewnij się, że DmsParsedDocument ma setter
                    	out.setOddzial(oddzial);}
                    break; // zwykle tylko jeden rekord 02
                }
            }
        } catch (Exception ex) {
            log.warn("Parser RD: nie udało się odczytać kasa: " + ex.getMessage());
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

        extractAndSetReportAndDowodNumbers(doc, out);
        //log.info(String.format("BEFORE extractPositionsRD: out id='%s' kwotaRk='%s'", System.identityHashCode(out), out.getKwotaRk()));
        List<DmsRapKasa> rapKasa = extractPositionsRD(doc, out);
        //log.info(String.format("AFTER extractPositionsRD: out id='%s' kwotaRk='%s' rapKasa.size='%s'",System.identityHashCode(out), out.getKwotaRk(), rapKasa == null ? 0 : rapKasa.size()));
        out.setRapKasa(rapKasa);

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
    private List<DmsRapKasa> extractPositions48(Document doc, List<DmsRapKasa> list, Contractor contractor) {
    	if (doc == null) return list;
    	NodeList docs = doc.getElementsByTagName("document");

    	for (int i = 0; i < docs.getLength(); i++) {
            Element el = (Element) docs.item(i);
            if (!"48".equals(el.getAttribute("typ"))) continue;
            Element dane48 = firstDirectChild(el, "dane");
            if (dane48 == null) continue;
            DmsRapKasa r = new DmsRapKasa();
            r.setContractor(contractor);
            // 1) kierunek: tylko Z/W na typ 48 (kod_dok zwykle 03 — nie rozróżnia DW vs DWP)
            Element klas = firstDirectChild(dane48, "klasyfikatory");
            Element numEl = firstDirectChild(dane48, "numer");
            if (klas != null) {
                String kier = "Z".equalsIgnoreCase(klas.getAttribute("klasyfikacja"))
                        ? "przychód"
                        : "rozchód";
                r.setKierunek(kier);
            }

            // 2) numer dowodu: karta 03 (lub 01 raport) — bez rozróżniania 03 vs 04
            if (numEl != null) {
                String tx = numEl.getTextContent() != null ? numEl.getTextContent().trim() : "";
                if (!tx.isEmpty()) {
                    String kd = safeAttr(numEl, "kod_dok").trim();
                    String seg = tx.contains("/") ? tx.substring(0, tx.indexOf('/')).trim() : "";
                    if ("03".equals(kd) || "04".equals(kd) || "01".equals(kd)
                            || "03".equals(seg) || "04".equals(seg) || "01".equals(seg)) {
                        r.setDowodNumber(removeLeadingZeroInFirstSegment(tx));
                    }
                }
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
            log.info(String.format("Pos 48 RD nrDokumentu='%s ' getDowodNumber='%s ' kwotaRk='%s ' getNrRKB='%s ' kierunek='%s '", r.getNrDokumentu() ,r.getDowodNumber(), r.getKwotaRk(), r.getNrRKB(), r.getKierunek()));
            list.add(r);
        }
    	return list;
    }
    private void extractPositions49(Document doc, List<DmsRapKasa> list) {
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
                }else {
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
                DmsRapKasa match = null;
                if (lpAttr != null && !lpAttr.isBlank()) {
                    for (DmsRapKasa p : list) {
                        if (lpAttr.equals(p.getLp())) {
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
                    skeletonIndex++;
                }

                String kierunekLine = match.getKierunek() != null ? match.getKierunek() : "";
                if (kwRk != null && !kwRk.isBlank()) {
                    double v = parseKwotaDouble(kwRk);
                    if (v < 0) {
                        kwRk = String.format(Locale.US, "%.2f", Math.abs(v));
                    }
                }

                if (kwRk != null && !kwRk.isBlank()) {
                    match.setKwotaRk(kwRk);
                }
                if (numer49 != null && !numer49.isBlank()) {
                    match.setNrDokumentu(numer49);
                }
                match.setLp(lpAttr);
                match.setOpis1(opis1);
                match.setKierunek(kierunekLine);
                match.setInvertKpdKwdSymbol(false);
                applyDowodFromAncestorTyp48(dane49, match);
                log.info(String.format("49 RD -> filled skeleton id='%s' nrDokumentu='%s' kwotaRk='%s'",
                         System.identityHashCode(match), match.getNrDokumentu(), match.getKwotaRk()));
            }
        }
    }


    private List<DmsRapKasa> extractPositionsRD(Document doc, DmsParsedDocument out) {
    	String podmiot ="";
    	Contractor co = extractContractor(doc); 
    	out.setContractor(co);
        /*if (contractor != null) {
            if (contractor.getNip() != null && !contractor.getNip().isBlank()) {
                podmiot = contractor.getNip();
            } else {
                podmiot = contractor.getFullName() == null ? "" : contractor.getFullName();
            }
            contractor.setPodmiot(podmiot);
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
	   extractPositions49(doc,list);   // nowe: pozycje z dokumentów 49
	    //log.info(String.format("ext RD list='%s '", list));
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
	            r.setDowodNumber(dowod);
	            String numerFa = dowod;
	            String nrIdPlat = MappingIdDocs.generateCandidate(podmiot, "D", numerFa, 36);
	            String fullKey = MappingIdDocs.buildFullKey(podmiot, numerFa);
	            String hash = MappingIdDocs.shortHashFromFullKey(fullKey, 6);
	            String docKey = MappingIdDocs.generateDocId(podmiot, "C", numerFa, 36);

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
	                payId.get(0).setIdPlatn(nrIdPlat);
	            }
	            log.info("ext RD parser: przypisano dowodNumber='" + dowod + "' kwotaRk='" + r.getKwotaRk() + "' do pozycji; nrDokumentu='" + r.getNrDokumentu() + "' file=" + out.getSourceFileName());
	        }
	    } else {
	        boolean anyLine = false;
	        for (DmsRapKasa r : list) {
	            String dowod = r.getDowodNumber();
	            if (dowod == null || dowod.isBlank()) {
	                continue;
	            }
	            anyLine = true;
	            String numerFa = dowod;
	            String nrIdPlat = MappingIdDocs.generateCandidate(podmiot, "D", numerFa, 36);
	            String fullKey = MappingIdDocs.buildFullKey(podmiot, numerFa);
	            String hash = MappingIdDocs.shortHashFromFullKey(fullKey, 6);
	            String docKey = MappingIdDocs.generateDocId(podmiot, "C", numerFa, 36);
	            r.setFullKey(fullKey);
	            out.setFullKey(fullKey);
	            r.setDocKey(docKey);
	            out.setDocKey(docKey);
	            r.setNrIdPlat(nrIdPlat);
	            out.setNrIdPlat(nrIdPlat);
	            r.setHash(hash);
	            out.setHash(hash);
	            log.info("ext RD parser: dowod tylko z linii='" + dowod + "' file=" + out.getSourceFileName());
	        }
	        if (!anyLine) {
	            log.info("ext RD parser: brak dowodNumber w out i pozycjach; file=" + out.getSourceFileName());
	        }
	    }

	    return list;

	}       
    private void extractAndSetReportAndDowodNumbers(Document doc, DmsParsedDocument out) {
    	if (doc == null || out == null) return;
        NodeList docs = doc.getElementsByTagName("document");
        //log.info("1 RD parser total nodes=" + (docs == null ? 0 : docs.getLength()));
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
                            log.info("1 RD parser: ustawiono reportNumber = '" + rep + "' file=" + out.getSourceFileName());
                        }
                    }
                }
                break; // tylko pierwszy document typ=02
            }
        }
        // Pierwszy sensowny numer z typ 48 (kod_dok często tylko 03 — kierunek DW/DWP z Z/W w pozycjach)
        String chosen = null;
        String nrRkbChosen = null;
        for (int i = 0; i < docs.getLength(); i++) {
            Element el = (Element) docs.item(i);
            if (el == null || !"48".equals(el.getAttribute("typ"))) {
                continue;
            }
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
                raw = numEl.getAttribute("nr");
            }
            if (raw == null || raw.isBlank()) {
                log.info("1 numer element empty in typ=48; file=" + out.getSourceFileName());
                continue;
            }
            raw = raw.trim();
            String repNrRKB = numEl.getAttribute("nr").trim();
            if (chosen == null) {
                chosen = raw;
                nrRkbChosen = repNrRKB;
                break;
            }
        }
        if (chosen != null) {
            out.setDowodNumber(chosen);
            out.setNrRKB(nrRkbChosen);
            log.info(String.format("1 RD parser: ustawiono dowodNumber = '%s' nrRKB='%s' file=%s",
                    chosen, nrRkbChosen, out.getSourceFileName()));
        }
    }
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
    // ------------------------------
    // Pomocnicze metody
    // ------------------------------
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

    /** Numer dowodu z nadrzędnego document typ=48 dla pozycji typ 49. */
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
        // usuń prefiks rejestru "01/" jeśli chcesz porównywać KO i RD
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
    private void synchronizeDocFromPositions(DmsParsedDocument out) {
        if (out == null) return;
        List<DmsRapKasa> rap = out.getRapKasa();
        if (rap == null || rap.isEmpty()) return;

        // znajdź pierwszą niepustą wartość dla każdego pola
        String nrDok = rap.stream()
            .map(DmsRapKasa::getNrDokumentu)
            .filter(s -> s != null && !s.isBlank())
            .findFirst().orElse(null);

        String dowod = rap.stream()
            .map(DmsRapKasa::getDowodNumber)
            .filter(s -> s != null && !s.isBlank())
            .findFirst().orElse(null);

        String kwota = rap.stream()
            .map(DmsRapKasa::getKwotaRk)
            .filter(s -> s != null && !s.isBlank())
            .findFirst().orElse(null);

        String reportPos = rap.stream()
            .map(DmsRapKasa::getReportNumberPos)
            .filter(s -> s != null && !s.isBlank())
            .findFirst().orElse(null);

        if ((out.getNrDokumentu() == null || out.getNrDokumentu().isBlank()) && nrDok != null) {
            out.setNrDokumentu(nrDok.trim());
            log.info(String.format("SYNC: ustawiono out.nrDokumentu='%s' from rapKasa file='%s'", nrDok.trim(), out.getSourceFileName()));
        }
        if ((out.getDowodNumber() == null || out.getDowodNumber().isBlank()) && dowod != null) {
            out.setDowodNumber(dowod.trim());
            log.info(String.format("SYNC: ustawiono out.dowodNumber='%s' from rapKasa file='%s'", dowod.trim(), out.getSourceFileName()));
        }
        if ((out.getKwotaRk() == null || out.getKwotaRk().isBlank()) && kwota != null) {
            out.setKwotaRk(kwota.trim());
            log.info(String.format("SYNC: ustawiono out.kwotaRk='%s' from rapKasa file='%s'", kwota.trim(), out.getSourceFileName()));
        }
        if ((out.getReportNumberPos() == null || out.getReportNumberPos().isBlank()) && reportPos != null) {
            out.setReportNumberPos(reportPos.trim());
            log.info(String.format("SYNC: ustawiono out.reportNumberPos=''%s'' from rapKasa file='%s'", reportPos.trim(), out.getSourceFileName()));
        }
    }

}

