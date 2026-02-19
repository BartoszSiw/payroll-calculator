package pl.edashi.dms.xml;

import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
	        rk.appendChild(makeCdata(docXml, "NUMERATOR_NUMER_NR", safe(doc.getNrRKB())));
	        rk.appendChild(makeCdata(docXml, "NUMER_OBCY", ""));
	        // ZAPISY_KB
	        LOG.info("CashReportXmlBuilder: doc.reportNumber='" + doc.getReportNumber()
	        + "' doc.reportNumberPos='" + doc.getReportNumberPos()
	        + "' rapKasa.size=" + (doc.getRapKasa()==null?0:doc.getRapKasa().size()));

	    if (doc.getRapKasa() != null) {
	        int i = 0;
	        for (DmsOutputPosition p : doc.getRapKasa()) {
	            LOG.info(String.format("  rap[%d]: reportNumber='%s' reportNumberPos='%s' nrRKB='%s' kwota='%s' kierunek='%s' opis='%s'",
	                i++,
	                p.getReportNumber(), p.getReportNumberPos(), p.getNrRKB(),
	                p.getKwotaRk(), p.getKierunek(), p.getOpis()));
	        }
	    }
	        Element rapKasa = docXml.createElementNS(NS, "ZAPISY_KB");
	        rk.appendChild(rapKasa);
	        LOG.info(String.format("rapKasa='%s ' getReportNumber='%s '", rapKasa, safe(doc.getReportNumberPos())));
	        // ZAPIS_KB
	        if (doc.getRapKasa() != null) {
	        	int lp = 1;
	        for (DmsOutputPosition k : doc.getRapKasa()) {
            	Element rap = docXml.createElementNS(NS, "ZAPIS_KB");
            	rapKasa.appendChild(rap);
        		rap.appendChild(makeCdata(docXml, "ID_ZRODLA_ZAPISU", ""));
    	        rap.appendChild(makeCdata(docXml, "SYMBOL_DOKUMENTU_ZAPISU", ""));
    	        rap.appendChild(makeCdata(docXml, "SYMBOL_DOKUMENTU_ZAPISU_ID", ""));
    	        rap.appendChild(makeCdata(docXml, "DATA_DOK", safe(doc.getDataWystawienia())));
    	        rap.appendChild(makeCdata(docXml, "NUMER_ZAPISU", safe(k.getDowodNumber())));
    	        rap.appendChild(makeCdata(docXml, "NUMERATOR_REJESTR", safe(k.getNrRKB())));
    	        rap.appendChild(makeCdata(docXml, "NUMERATOR_NUMER_NR_ZAPISU", safe(k.getNrRKB())));
    	        rap.appendChild(makeCdata(docXml, "NUMER_OBCY_ZAPISU", safe(k.getNumer())));
    	        rap.appendChild(makeCdata(docXml, "KIERUNEK", safe(k.getKierunek())));
    	        rap.appendChild(makeCdata(docXml, "KWOTA", safe(k.getKwotaRk())));
    	        /*rap.appendChild(makeCdata(docXml, "LP", safe(k.lp)));
    	        rap.appendChild(makeCdata(docXml, "TYP", safe(k.typ)));
    	        rap.appendChild(makeCdata(docXml, "KWOTA", safe(k.kwota)));
    	        rap.appendChild(makeCdata(docXml, "WALUTA", safe(k.waluta)));
    	        rap.appendChild(makeCdata(docXml, "WALUTA_DOK", safe(k.walutaDok)));
    	        rap.appendChild(makeCdata(docXml, "KURS_WALUTY", safe(k.kursWaluty)));
    	        rap.appendChild(makeCdata(docXml, "NOTOWANIE_WALUTY_ILE", safe(k.ile)));
    	        rap.appendChild(makeCdata(docXml, "NOTOWANIE_WALUTY_ZA_ILE", safe(k.zaIle)));
    	        rap.appendChild(makeCdata(docXml, "DATA_KURSU", safe(k.dataKursu)));
    	        rap.appendChild(makeCdata(docXml, "KWOTA_PLN", safe(k.kwotaPln)));
    	        rap.appendChild(makeCdata(docXml, "KIERUNEK", safe(k.kierunek)));
    	        rap.appendChild(makeCdata(docXml, "PODLEGA_ROZLICZENIU", safe(k.podlega)));
    	        rap.appendChild(makeCdata(docXml, "JEST_WYNAGRODZENIEM", safe(k.wynagrodzenie)));
    	        rap.appendChild(makeCdata(docXml, "TYP_PODMIOTU", safe(k.typPodmiotu)));
    	        rap.appendChild(makeCdata(docXml, "PODMIOT", safe(k.podmiot)));
    	        rap.appendChild(makeCdata(docXml, "PODMIOT_ID", safe(k.podmiotId)));
    	        rap.appendChild(makeCdata(docXml, "PODMIOT_NIP", safe(k.podmiotNip)));
    	        rap.appendChild(makeCdata(docXml, "NAZWA1", safe(k.nazwa1)));
    	        rap.appendChild(makeCdata(docXml, "NAZWA2", safe(k.nazwa2)));
    	        rap.appendChild(makeCdata(docXml, "NAZWA3", safe(k.nazwa3)));
    	        rap.appendChild(makeCdata(docXml, "KRAJ", safe(k.kraj)));
    	        rap.appendChild(makeCdata(docXml, "WOJEWODZTWO", safe(k.woj)));
    	        rap.appendChild(makeCdata(docXml, "POWIAT", safe(k.powiat)));
    	        rap.appendChild(makeCdata(docXml, "GMINA", safe(k.gmina)));
    	        rap.appendChild(makeCdata(docXml, "ULICA", safe(k.ulica)));
    	        rap.appendChild(makeCdata(docXml, "NR_DOMU", safe(k.nrDomu)));
    	        rap.appendChild(makeCdata(docXml, "NR_LOKALU", safe(k.nrLokalu)));
    	        rap.appendChild(makeCdata(docXml, "MIASTO", safe(k.miasto)));
    	        rap.appendChild(makeCdata(docXml, "KOD_POCZTOWY", safe(k.kod)));
    	        rap.appendChild(makeCdata(docXml, "POCZTA", safe(k.poczta)));
    	        rap.appendChild(makeCdata(docXml, "DODATKOWE", safe(k.dodatkowe)));
    	        rap.appendChild(makeCdata(docXml, "BANK_NR", safe(k.bankNr)));
    	        rap.appendChild(makeCdata(docXml, "BANK_ID", safe(k.bankId)));
    	        rap.appendChild(makeCdata(docXml, "NR_RACHUNKU", safe(k.nrRachunku)));
    	        rap.appendChild(makeCdata(docXml, "IBAN", safe(k.iban)));
    	        rap.appendChild(makeCdata(docXml, "KARTA_KR_NUMER", safe(k.karta)));
    	        rap.appendChild(makeCdata(docXml, "OPIS", safe(k.opis)));
    	        rap.appendChild(makeCdata(docXml, "KONTO", safe(k.konto)));
    	        rap.appendChild(makeCdata(docXml, "SPLIT_PAYMENT", safe(k.split)));
    	        rap.appendChild(makeCdata(docXml, "ZAPIS_VAT", safe(k.zapisVat)));*/

    	        rap.appendChild(makeCdata(docXml, "KWOTY_DODATKOWE", ""));
	        }
	       }
	        }
	  

		// -----------------------
	    // Helpers
	    // -----------------------
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
}


