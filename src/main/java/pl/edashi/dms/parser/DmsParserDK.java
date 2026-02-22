package pl.edashi.dms.parser;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.model.DocumentMetadata;
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
/**
 * Parser DK.xml
 * - wyciąga główny numer dokumentu (z elementu <numer> w <dane>)
 * - wyciąga gen_doc_id z atrybutu DMS (jeśli obecny)
 * - wyciąga powiązane numery z zagnieżdżonych dokumentów (np. pozycje dowodu kasowego -> numer wewnętrzny DS)
 * - wyciąga kwotę z <wartosci kwota="..."> jeśli występuje
 */
public class DmsParserDK {
	private final AppLogger log = new AppLogger("DmsParserDK");
	public DmsParsedDocument parse(Document doc, String fileName) {
    //public DmsParsedDocument parse(InputStream xmlStream, String sourceFileName) throws Exception {

        DmsParsedDocument out = new DmsParsedDocument();
        out.setDocumentType("DK");
        out.setSourceFileName(fileName);
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
        log.info("Last Parser DK ustawiono NrDokumentu='" + out.getNrDokumentu()+ "'getReportNumber='" + out.getReportNumber() + "' out.reportNumberPos='" + out.getReportNumberPos() + "' file= '" + out.getSourceFileName());
        log.info("Last Parser DK ustawiono getRapKasa='" + out.getRapKasa());
        //log.info("DK parser: ustawiono out.reportNumber (raw) = '" + raw + "' file=" + out.getSourceFileName());

        // ============================
        // 3. PŁATNOŚCI (typ 48 + 49)
        // ============================
        //List<DmsPayment> payments = extractPayments(doc, out);
        //out.setPayments(payments);

        // ============================
        // 4. UWAGI (operator, opis pozycji)
        // ============================
        //List<String> notes = extractNotes(doc);
        //out.setNotes(notes);

        // ============================
        // 5. DODATKOWY OPIS
        // ============================
        //String mainNumber = extractMainNumber(doc);
        //out.setAdditionalDescription("DK " + (mainNumber != null ? mainNumber : ""));
        // ============================
        // 6. POZYCJE (typ 49)
        // ============================
        //extractPositions(doc, out);
        // ============================
        // 7. PUSTE POLA (DK ich nie ma)
        // ============================
        //out.setPositions(new ArrayList<>()); // puste
        //out.setVatRate("");
        //out.setVatBase("");
        //out.setVatAmount("");
        //out.setFiscalNumber("");
        //out.setFiscalDevice("");
        //out.setFiscalDate("");

        return out;
    }
    /*public DmsParsedDocument parse(InputStream xmlStream, String fileName) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(xmlStream);
        return parse(doc, fileName);
    }*/

    // ------------------------------
    // KONTRAHENT (typ 35)
    // ------------------------------
    private Contractor extractContractor(Document doc) {

        NodeList list = doc.getElementsByTagName("document");

        for (int i = 0; i < list.getLength(); i++) {
            Element el = (Element) list.item(i);

            if ("35".equals(el.getAttribute("typ"))) {

                Element dane = firstElementByTag(el, "dane");
                if (dane == null) continue;
                Element rozs = firstElementByTag(dane, "rozszerzone");
                if (rozs == null) continue;

                Contractor c = new Contractor();
                c.setId(safeAttr(rozs, "kod_klienta"));
                c.setNip(safeAttr(rozs, "nip"));
                c.setName1(safeAttr(rozs, "nazwa1"));
                c.setName2(safeAttr(rozs, "nazwa2"));
                c.setName3(safeAttr(rozs, "nazwa3"));
                c.setStreet(safeAttr(rozs, "ulica"));
                c.setZip(safeAttr(rozs, "kod_poczta"));
                c.setCity(safeAttr(rozs, "miejscowosc"));
                c.setCountry(safeAttr(rozs, "kod_kraju"));

                return c;
            }
        }
        return null;
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
         // 1) klasyfikacja Z/W
            Element klas = firstDirectChild(dane48, "klasyfikatory");
            if (klas != null) {
                String kier = "Z".equalsIgnoreCase(klas.getAttribute("klasyfikacja"))
                        ? "przychód"
                        : "rozchód";
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

                // weź kolejny skeleton z listy (jeśli jest), inaczej utwórz nowy
                DmsRapKasa match;
                if (skeletonIndex < list.size()) {
                    match = list.get(skeletonIndex);
                } else {
                    match = new DmsRapKasa();
                    list.add(match);
                }
                skeletonIndex++; // przesuwamy wskaźnik na następny skeleton

                // ustawiamy pola z 49 (w Twoim przypadku nie nadpisują pól z 48)
                if (kwRk != null && !kwRk.isBlank()) match.setKwotaRk(kwRk);
                if (numer49 != null && !numer49.isBlank()) match.setNrDokumentu(numer49);

                // opcjonalny log debugowy
                log.info(String.format("49 DK-> filled skeleton id='%s' nrDokumentu='%s' kwotaRk='%s'",
                         System.identityHashCode(match), match.getNrDokumentu(), match.getKwotaRk()));
            }
        }
    }

    private List<DmsRapKasa> extractPositionsDK(Document doc, DmsParsedDocument out) {
        Contractor contractor = extractContractor(doc);
        out.setContractor(contractor);
	    // 1) Pozycje z typ="48"
	    List<DmsRapKasa> list = new ArrayList<>();
	    extractPositions48(doc,list,contractor);   // istniejące pozycje na poziomie 48
	    extractPositions49(doc,list);   // nowe: pozycje z dokumentów 49
	    //log.info(String.format("ext DK list='%s '", list));
	 // po zebraniu listy rapKasa (po extractPositions48 i extractPositions49)
	    String dowod = null;
	    if (out != null) {
	        // pole, które wcześniej ustawiłeś w extractAndSetReportNumberPosFromFirstPosition
	        dowod = out.getDowodNumber(); // lub out.getNrDowodu() / właściwy getter
	    }
	    if (dowod != null && !dowod.isBlank()) {
	        for (DmsRapKasa r : list) {
	            // ustawiamy numer dowodu KP/KW w polach pozycji
	            r.setDowodNumber(dowod); // pole używane do zapisu NR_RAPORTU / NR_DOWODU w XML
	            //r.setReportNumberPos(removeLeadingZeroInFirstSegment(dowod)); // jeśli chcesz formatowany wariant
	            //r.setReportNumber(removeLeadingZeroInFirstSegment(dowod));
	            log.info("ext DK parser: przypisano dowodNumber='" + dowod + "' do pozycji; nrDokumentu='" + r.getNrDokumentu() + "' file=" + out.getSourceFileName());
	        }
	    } else {
	        log.info("ext DK parser: brak dowodNumber w out — nie przypisano do pozycji; file=" + out.getSourceFileName());
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
        for (int i = 0; i < docs.getLength(); i++) {
            Element el = (Element) docs.item(i);
            if (el == null) continue;
            //log.info("1 Inspecting document: tag=" + el.getNodeName() + " typ=" + el.getAttribute("typ") + " attrs=" + listAttributes(el));
            if (!"48".equals(el.getAttribute("typ"))) continue; // tylko pozycje typu 48
            Element dane48 = firstDirectChild(el, "dane");
            if (dane48 == null) {
            	log.info("1 Found document typ=48 but no direct <dane> child; file=" + out.getSourceFileName());
            	continue;
            }

            // spróbuj pobrać numer z elementu 'numer' z kod_dok="01"
            Element numEl = firstDirectChild(dane48, "numer");
            if (numEl == null) {
                log.info("1 Found <dane> in typ=48 but no direct <numer>; file=" + out.getSourceFileName());
                continue;
            }
         // preferujemy tekst elementu (np. "01/00003/2026"), bez harmonizacji
            String raw = numEl.getTextContent();
            String repNrRKB = numEl.getAttribute("nr").trim();
            out.setNrRKB(repNrRKB);
            //if (raw == null || raw.isBlank()) raw = numEl.getAttribute("nr");
            if (raw == null || raw.isBlank()) {
                log.info("1 numer element empty in typ=48; file=" + out.getSourceFileName());
                continue;
            }

            raw = raw.trim(); // tylko trim, zachowujemy format "01/00003/2026"
            // **TUTAJ TYLKO** ustawiamy pole dowodowe — nie ruszamy reportNumber/reportNumberPos/nrRKB
            out.setDowodNumber(raw); // <- jeśli Twój setter ma inną nazwę, zamień na właściwy (np. setNrDowodu)
            log.info("1 DK parser: ustawiono dowodNumber (KP/KW) = '" + raw + "' file=" + out.getSourceFileName());
            //////////////////////////
            /*log.info("Pos DK parser: nie znaleziono document typ=48; file=" + out.getSourceFileName());
            String raw1 = null;
            if (numEl != null && "01".equals(numEl.getAttribute("kod_dok"))) {
            	raw1= numEl.getTextContent();
                log.info("Pos Found <dane> but no direct <numer> child; file=" + out.getSourceFileName() +" raw= " + raw1);
            }
            String rawNrKpW = null;
            if (numEl != null && "01".equals(numEl.getAttribute("kod_dok"))) {
            	rawNrKpW = numEl.getTextContent();
            	log.info("Pos numer kod_dok != '01' (rawNrKpW='" + rawNrKpW + "'), file=" + out.getSourceFileName());
            	
            }
            // fallback: jeśli nie ma elementu numer, spróbuj z opisu/klasyfikatorów
            if ((raw == null || raw.isBlank())) {
                Element opisEl = firstDirectChild(dane48, "opis");
                if (opisEl != null) raw = opisEl.getTextContent();
            }

            if (raw1 != null) {
                raw1 = removeLeadingZeroInFirstSegment(raw1.trim());
                // usuń tylko trailing/leading spacje; nie zmieniaj formatu numeru
                out.setReportNumberPos(raw1);
                // opcjonalnie ustaw też reportNumber (jeśli chcesz, żeby pole reportNumber było dostępne)
                if (out.getReportNumber() == null || out.getReportNumber().isBlank()) {
                    out.setReportNumber(raw);
                }
                log.info("Pos DK parser: ustawiono out.reportNumberPos = '" + raw1 + "' file=" + out.getSourceFileName());
            }*/
            
            return; 
            }
        
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
}

