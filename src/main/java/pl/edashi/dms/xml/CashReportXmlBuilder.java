package pl.edashi.dms.xml;

import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import pl.edashi.dms.model.Contractor;
import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsOutputPosition;
import pl.edashi.dms.model.DmsRapKasa;
import pl.edashi.dms.parser.util.DocumentNumberExtractor;

public class CashReportXmlBuilder implements XmlSectionBuilder {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CashReportXmlBuilder.class);
	 private static final String NS = "http://www.comarch.pl/cdn/optima/offline";
	 private final DmsDocumentOut doc;

	    public CashReportXmlBuilder(DmsDocumentOut doc) {
	        if (doc == null) {
	            throw new IllegalArgumentException("CashReportXmlBuilder: report is null");
	        }

	        // jeśli chcesz walidować typy raportów, możesz dodać:
	        // Set<String> TYPES = Set.of("RKB", "RKW");
	        // if (!TYPES.contains(report.symbolDok)) throw ...

	        this.doc = doc;
	    }

	    @Override
	    public void build(Document docXml, Element root) {
	        if (docXml == null || root == null) return;

	        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	        factory.setNamespaceAware(true);
	        Element rejSekcja = docXml.createElementNS(NS, "RAPORTY_KB");
	        root.appendChild(rejSekcja);
	        // Nagłówek sekcji
	        rejSekcja.appendChild(makeCdata(docXml, "WERSJA", "2.00"));
	        rejSekcja.appendChild(makeCdata(docXml, "BAZA_ZRD_ID", "KSIEG"));
	        rejSekcja.appendChild(makeCdata(docXml, "BAZA_DOC_ID", "DMS_1"));

	        // REJESTR_ZAKUPU_VAT
	        Element rk = docXml.createElementNS(NS, "RAPORT_KB");
	        rejSekcja.appendChild(rk);
	     // META
	        rk.appendChild(makeCdata(docXml, "ID_ZRODLA", ""));//safe(doc.getIdZrodla())
	        rk.appendChild(makeCdata(docXml, "RACHUNEK", "1"));
	        rk.appendChild(makeCdata(docXml, "RACHUNEK_ID", ""));
	        rk.appendChild(makeCdata(docXml, "DATA_OTWARCIA", safe(doc.getDataOtwarcia())));
	        rk.appendChild(makeCdata(docXml, "DATA_ZAMKNIECIA", safe(doc.getDataZamkniecia())));
	        rk.appendChild(makeCdata(docXml, "SYMBOL_DOKUMENTU", "RK"));
	        rk.appendChild(makeCdata(docXml, "SYMBOL_DOKUMENTU_ID", ""));
	        rk.appendChild(makeCdata(docXml, "NUMER", "RK/"+safe(doc.getReportNumber())));
	        rk.appendChild(makeCdata(docXml, "NUMERATOR_NUMER_NR", safe(doc.getNrRep())));
	        rk.appendChild(makeCdata(docXml, "NUMER_OBCY", ""));
	        // ZAPISY_KB
	        /*LOG.info("CashReportXmlBuilder: doc.reportNumber='" + doc.getReportNumber()
	        + "' doc.reportNumberPos='" + doc.getReportNumberPos()
	        + "' rapKasa.size=" + (doc.getRapKasa()==null?0:doc.getRapKasa().size()));*/

	    if (doc.getRapKasa() != null) {
	        int i = 0;
	        for (DmsOutputPosition p : doc.getRapKasa()) {
	            /*LOG.info(String.format("  rap[%d]: nrDokumentu:'%s' reportNumber='%s' reportNumberPos='%s' nrRKB='%s' kwota='%s' kierunek='%s' opis='%s'",
	                i++, p.getNrDokumentu(),
	                p.getReportNumber(), p.getReportNumberPos(), p.getNrRKB(),
	                p.getKwotaRk(), p.getKierunek(), p.getOpis()));*/
	        }
	    }
	        Element rapKasa = docXml.createElementNS(NS, "ZAPISY_KB");
	        rk.appendChild(rapKasa);
	        //LOG.info(String.format("rapKasa='%s ' getReportNumber='%s '", rapKasa, safe(doc.getReportNumberPos())));
	        // ZAPIS_KB
	        if (doc.getRapKasa() != null) {
	        	int lp = 1;
	        for (DmsOutputPosition k : doc.getRapKasa()) {
            	Element rap = docXml.createElementNS(NS, "ZAPIS_KB");
            	rapKasa.appendChild(rap);
        		rap.appendChild(makeCdata(docXml, "ID_ZRODLA_ZAPISU", ""));
    	        rap.appendChild(makeCdata(docXml, "SYMBOL_DOKUMENTU_ZAPISU", safe(k.getSymbolKPW())));
    	        rap.appendChild(makeCdata(docXml, "SYMBOL_DOKUMENTU_ZAPISU_ID", ""));
    	        rap.appendChild(makeCdata(docXml, "DATA_DOK", safe(k.getDataWystawienia())));
    	        rap.appendChild(makeCdata(docXml, "NUMER_ZAPISU", safe(k.getDowodNumber())));
    	        rap.appendChild(makeCdata(docXml, "NUMERATOR_REJESTR", "1"));
    	        rap.appendChild(makeCdata(docXml, "NUMERATOR_NUMER_NR_ZAPISU", safe(k.getNrRKB())));
    	        rap.appendChild(makeCdata(docXml, "NUMER_OBCY_ZAPISU", safe(k.getNrDokumentu())));
    	        
    	        
    	        //rap.appendChild(makeCdata(docXml, "LP", safe(k.lp)));
    	        String draftTyp = mapingPositionSymbol(safe(k.getSymbolKPW()));
    	        rap.appendChild(makeCdata(docXml, "TYP", safe(draftTyp)));
    	        rap.appendChild(makeCdata(docXml, "KWOTA", safe(k.getKwotaRk())));
    	        rap.appendChild(makeCdata(docXml, "WALUTA", ""));
    	        rap.appendChild(makeCdata(docXml, "WALUTA_DOK", ""));
    	        rap.appendChild(makeCdata(docXml, "KURS_WALUTY", "NBP"));
    	        rap.appendChild(makeCdata(docXml, "NOTOWANIE_WALUTY_ILE", "1"));
    	        rap.appendChild(makeCdata(docXml, "NOTOWANIE_WALUTY_ZA_ILE", "1"));
    	        rap.appendChild(makeCdata(docXml, "DATA_KURSU", safe(k.getDataWystawienia())));
    	        rap.appendChild(makeCdata(docXml, "KWOTA_PLN", safe(k.getKwotaRk())));
    	        rap.appendChild(makeCdata(docXml, "KIERUNEK", safe(k.getKierunek())));
    	        rap.appendChild(makeCdata(docXml, "PODLEGA_ROZLICZENIU", "tak"));
    	        rap.appendChild(makeCdata(docXml, "JEST_WYNAGRODZENIEM", "Nie"));
    	        rap.appendChild(makeCdata(docXml, "TYP_PODMIOTU", "kontrahent"));
    	        rap.appendChild(makeCdata(docXml, "PODMIOT", safeContractorField(k, "nip")));
    	        rap.appendChild(makeCdata(docXml, "PODMIOT_ID", ""));
    	        rap.appendChild(makeCdata(docXml, "PODMIOT_NIP", safeContractorField(k, "nip")));
    	        rap.appendChild(makeCdata(docXml, "NAZWA1", safeContractorField(k, "name1")));
    	        rap.appendChild(makeCdata(docXml, "NAZWA2", safeContractorField(k, "name2")));
    	        rap.appendChild(makeCdata(docXml, "NAZWA3", safeContractorField(k, "name3")));
    	        rap.appendChild(makeCdata(docXml, "KRAJ", safeContractorField(k, "country")));
    	        rap.appendChild(makeCdata(docXml, "WOJEWODZTWO", ""));
    	        rap.appendChild(makeCdata(docXml, "POWIAT", ""));
    	        rap.appendChild(makeCdata(docXml, "GMINA", ""));
    	        rap.appendChild(makeCdata(docXml, "ULICA", safeContractorField(k, "street")));
    	        rap.appendChild(makeCdata(docXml, "NR_DOMU", ""));
    	        rap.appendChild(makeCdata(docXml, "NR_LOKALU", ""));
    	        rap.appendChild(makeCdata(docXml, "MIASTO", safeContractorField(k, "city")));
    	        rap.appendChild(makeCdata(docXml, "KOD_POCZTOWY", safeContractorField(k, "zip")));
    	        rap.appendChild(makeCdata(docXml, "POCZTA", ""));
    	        rap.appendChild(makeCdata(docXml, "DODATKOWE", ""));
    	        rap.appendChild(makeCdata(docXml, "BANK_NR", ""));
    	        rap.appendChild(makeCdata(docXml, "BANK_ID", ""));
    	        rap.appendChild(makeCdata(docXml, "NR_RACHUNKU", ""));
    	        rap.appendChild(makeCdata(docXml, "IBAN", ""));
    	        rap.appendChild(makeCdata(docXml, "KARTA_KR_NUMER", ""));
    	        rap.appendChild(makeCdata(docXml, "OPIS", ""));
    	        String draftKonto = mapingPositionKonto(safe(k.getKierunek()));
    	        rap.appendChild(makeCdata(docXml, "KONTO", safe(draftKonto)));
    	        rap.appendChild(makeCdata(docXml, "SPLIT_PAYMENT", ""));
    	        rap.appendChild(makeCdata(docXml, "ZAPIS_VAT", ""));

    	        rap.appendChild(makeCdata(docXml, "KWOTY_DODATKOWE", ""));
	        }
	       }
	        }
	  

		// -----------------------
	    // Helpers
	    // -----------------------
	    private static String mapingPositionKonto(String kierunek) {
	        if (kierunek == null) kierunek = "";
	        kierunek = kierunek.trim().toLowerCase();

	        // Normalizuj symbol (np. "01/..." -> "01", "kp" -> "KP")
	        String kie = kierunek == null ? "" : kierunek.trim().toUpperCase();
	        if (kie.matches("^\\d{2}(/.*)?$")) {
	            kie = kie.split("/")[0];
	        }

	        // Proste reguły: rozchód -> 202, przychód -> 101, inaczej ""
	        if ("rozchód".equals(kierunek) || "rozchod".equals(kierunek)) {
	            return "202";
	        } else if ("przychód".equals(kierunek) || "przychod".equals(kierunek)) {
	            return "201";
	        }

	        // Opcjonalnie: jeśli chcesz różnicować po symbolu, dopisz tu reguły:
	        // if ("01".equals(sym) || "KP".equals(sym)) return "202";
	        // if ("02".equals(sym) || "KW".equals(sym)) return "101";

	        return "";
	    }

	    private static String mapingPositionSymbol(String symbolOrCode) {
	        if (symbolOrCode == null) return "";
	        String s = symbolOrCode.trim().toUpperCase();
	        // jeśli dostaniesz coś w formacie "01/..." lub "02/..." -> weź pierwszy segment
	        if (s.matches("^\\d{2}(/.*)?$")) {
	            s = s.split("/")[0];
	        }

	        switch (s) {
	            case "KPD":
	            case "01":
	                return "gotówka";
	            case "KWD":
	            case "02":
	                return "gotówka";
	            case "DW":
	            case "03":
	                return "karta";
	            default:
	                return ""; // nieznany symbol -> puste (bez błędów)
	        }
	    }


	    private static String safe(String s) {
	        return s == null ? "" : s;
	    }
	    private Element makeCdata(Document docXml, String name, String value) {
	        Element el = docXml.createElementNS(NS, name);
	        String v = value == null ? "" : value;
	        if (v.isBlank()) v = "";
	        // escape CDATA end
	        String safeValue = v.replace("]]>", "]]]]><![CDATA[>");
	        el.appendChild(docXml.createCDATASection(safeValue));
	        return el;
	    }	   
	    private String safeContractorField(DmsOutputPosition pos, String field) {
	        if (pos == null) return "";
	        Contractor c = pos.getContractor();
	        if (c == null) return "";

	        String value;
	        switch (field) {
	            case "nip":           value = c.getNip(); break;
	            case "name1":         value = c.getName1(); break;
	            case "name2":         value = c.getName2(); break;
	            case "name3":         value = c.getName3(); break;
	            case "fullName":      value = c.getFullName(); break;
	            case "country":       value = c.getCountry(); break;
	            case "region":        value = c.getRegion(); break;
	            case "city":          value = c.getCity(); break;
	            case "zip":           value = c.getZip(); break;
	            case "street":        value = c.getStreet(); break;
	            case "houseNumber":   value = c.getHouseNumber(); break;
	            case "czynny":        value = c.getCzynny(); break;
	            default:              value = null; break;
	        }
	        return value == null ? "" : value.trim();
	    }

}


