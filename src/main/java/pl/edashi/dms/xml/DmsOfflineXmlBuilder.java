package pl.edashi.dms.xml;
import pl.edashi.dms.model.*;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;

import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
public class DmsOfflineXmlBuilder {

	    public String build(DmsDocument doc) throws Exception {

	        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	        DocumentBuilder builder = factory.newDocumentBuilder();
	        Document xml = builder.newDocument();

	        Element root = xml.createElementNS("http://www.comarch.pl/cdn/optima/offline", "ROOT");
	        xml.appendChild(root);

	        Element rej = xml.createElement("REJESTRY_SPRZEDAZY_VAT");
	        root.appendChild(rej);

	        rej.appendChild(make(xml, "WERSJA", "2.00"));
	        rej.appendChild(make(xml, "BAZA_ZRD_ID", "DMSJB"));
	        rej.appendChild(make(xml, "BAZA_DOC_ID", "KSIEG"));

	        Element rs = xml.createElement("REJESTR_SPRZEDAZY_VAT");
	        rej.appendChild(rs);

	        rs.appendChild(make(xml, "ID_ZRODLA", doc.idZrodla));
	        rs.appendChild(make(xml, "MODUL", doc.modul));
	        rs.appendChild(make(xml, "TYP", doc.typ));
	        rs.appendChild(make(xml, "REJESTR", doc.rejestr));

	        rs.appendChild(make(xml, "DATA_WYSTAWIENIA", doc.dataWystawienia));
	        rs.appendChild(make(xml, "DATA_SPRZEDAZY", doc.dataSprzedazy));
	        rs.appendChild(make(xml, "TERMIN", doc.termin));
	        rs.appendChild(make(xml, "NUMER", doc.numer));

	        // PODMIOT
	        rs.appendChild(make(xml, "PODMIOT_ID", doc.podmiotId));
	        rs.appendChild(make(xml, "PODMIOT_NIP", doc.podmiotNip));
	        rs.appendChild(make(xml, "NAZWA1", doc.nazwa1));
	        rs.appendChild(make(xml, "NAZWA2", doc.nazwa2));
	        rs.appendChild(make(xml, "MIASTO", doc.miasto));
	        rs.appendChild(make(xml, "KOD_POCZTOWY", doc.kodPocztowy));
	        rs.appendChild(make(xml, "ULICA", doc.ulica));
	        rs.appendChild(make(xml, "NR_DOMU", doc.nrDomu));

	        // POZYCJE
	        Element pozycje = xml.createElement("POZYCJE");
	        rs.appendChild(pozycje);

	        for (DmsPosition p : doc.pozycje) {
	            Element poz = xml.createElement("POZYCJA");
	            pozycje.appendChild(poz);

	            poz.appendChild(make(xml, "KATEGORIA_POS", p.kategoria));
	            poz.appendChild(make(xml, "STAWKA_VAT", p.stawkaVat));
	            poz.appendChild(make(xml, "NETTO", p.netto));
	            poz.appendChild(make(xml, "VAT", p.vat));
	            poz.appendChild(make(xml, "RODZAJ_SPRZEDAZY", p.rodzajSprzedazy));
	        }
	        // VAT
	        rs.appendChild(make(xml, "STAWKA_VAT", doc.vatRate));
	        rs.appendChild(make(xml, "PODSTAWA", doc.vatBase));
	        rs.appendChild(make(xml, "KWOTA_VAT", doc.vatAmount));
	        // PŁATNOŚCI
	        Element platnosci = xml.createElement("PLATNOSCI");
	        rs.appendChild(platnosci);

	        for (DmsPayment p : doc.platnosci) {
	            Element plat = xml.createElement("PLATNOSC");
	            platnosci.appendChild(plat);

	            plat.appendChild(make(xml, "TERMIN_PLAT", p.termin));
	            plat.appendChild(make(xml, "FORMA_PLATNOSCI_PLAT", p.forma));
	            plat.appendChild(make(xml, "KWOTA_PLAT", p.kwota));
	            plat.appendChild(make(xml, "KIERUNEK", p.kierunek));
	            plat.appendChild(make(xml, "PLAT_ELIXIR_O1", p.opis));
	        }
	        // UWAGI
	        Element notes = xml.createElement("UWAGI");
	        rs.appendChild(notes);

	        for (String note : doc.uwagi) {
	            notes.appendChild(make(xml, "UWAGA", note));
	        }
	        // SERIALIZACJA
	        Transformer transformer = TransformerFactory.newInstance().newTransformer();
	        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

	        StringWriter writer = new StringWriter();
	        transformer.transform(new DOMSource(xml), new StreamResult(writer));

	        return writer.toString();
	    }

	    private Element make(Document doc, String name, String value) {
	        Element el = doc.createElement(name);
	        el.setTextContent(value == null ? "" : value);
	        return el;
	    }
	}
