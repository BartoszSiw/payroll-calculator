package pl.edashi.optima.builder;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.StringWriter;
import java.util.List;

import pl.edashi.dms.xml.XmlSectionBuilder;
import pl.edashi.optima.model.OfflineContractor;

public class ContractorsXmlBuilder implements XmlSectionBuilder {

    private static final String NS = "http://www.comarch.pl/cdn/optima/offline";
    private final List<OfflineContractor> contractors;
    public ContractorsXmlBuilder(List<OfflineContractor> contractors) {
        this.contractors = contractors;
    }
    @Override
    public void build(Document xml, Element root) {

    //public String build(List<OfflineContractor> contractors) throws Exception {

        Element sekcja = xml.createElementNS(NS, "KONTRAHENCI");
        root.appendChild(sekcja);

        sekcja.appendChild(make(xml, "WERSJA", "2.00"));
        sekcja.appendChild(make(xml, "BAZA_ZRD_ID", "KSIEG"));
        sekcja.appendChild(make(xml, "BAZA_DOC_ID", "DMS_1"));

        for (OfflineContractor c : contractors) {
            Element k = xml.createElementNS(NS, "KONTRAHENT");
            sekcja.appendChild(k);

            k.appendChild(make(xml, "ID_ZRODLA", c.idZrodla));
            k.appendChild(make(xml, "AKRONIM", c.akronim != null ? c.akronim : " "));
            k.appendChild(make(xml, "ZEZWOLENIE", " "));
            k.appendChild(make(xml, "OPIS", " "));

            k.appendChild(make(xml, "CHRONIONY", "Nie"));
            k.appendChild(make(xml, "RODZAJ", c.rodzaj));
            k.appendChild(make(xml, "EKSPORT", c.eksport));
            k.appendChild(make(xml, "FINALNY", c.finalny));
            k.appendChild(make(xml, "PLATNIK_VAT", c.platnikVat));
            k.appendChild(make(xml, "MEDIALNY", c.medialny));
            k.appendChild(make(xml, "POWIAZANY_UOV", "Nie"));
            k.appendChild(make(xml, "NIEAKTYWNY", "Nie"));
            k.appendChild(make(xml, "ROLNIK", "Nie"));
            k.appendChild(make(xml, "NIE_NALICZAJ_ODSETEK", "Nie"));
            k.appendChild(make(xml, "METODA_KASOWA", "Nie"));
            k.appendChild(make(xml, "METODA_KASOWA_SPR", "Nie"));
            k.appendChild(make(xml, "NIE_UWZGLEDNIAJ_KASOWY_PIT", "Nie"));

            k.appendChild(make(xml, "KONTOODB", "202"));
            k.appendChild(make(xml, "KONTODOST", "201"));

            k.appendChild(make(xml, "FORMA_PLATNOSCI", c.formaPlatnosci));
            k.appendChild(make(xml, "FORMA_PLATNOSCI_ID", c.formaPlatnosciId));

            k.appendChild(make(xml, "MAX_ZWLOKA", "0"));
            k.appendChild(make(xml, "CENY", "domyślna"));
            k.appendChild(make(xml, "JEST_LIMIT_KREDYTU", "Nie"));
            k.appendChild(make(xml, "LIMIT_KREDYTU", "0"));
            k.appendChild(make(xml, "NIE_PODLEGA_ROZLICZENIU", "Nie"));
            k.appendChild(make(xml, "KOMORNIK", "Nie"));
            k.appendChild(make(xml, "UPUST", "0"));
            k.appendChild(make(xml, "INFORMACJE", "Nie"));
            k.appendChild(make(xml, "INDYWIDUALNY_TERMIN", "Nie"));
            k.appendChild(make(xml, "TERMIN", String.valueOf(c.termin)));
            k.appendChild(make(xml, "KAUCJE_PLATNOSCI", "Nie"));
            k.appendChild(make(xml, "KAUCJE_TERMIN", "60"));
            k.appendChild(make(xml, "KOD_TRANSAKCJI", " "));
            k.appendChild(make(xml, "BLOKADA_DOKUMENTOW", "Nie"));
            k.appendChild(make(xml, "LIMIT_PRZETERMINOWANY", "Nie"));
            k.appendChild(make(xml, "LIMIT_PRZETERMINOWANY_WARTOSC", "0"));
            k.appendChild(make(xml, "KRAJ_ISO", c.krajIso));

            Element opiekun = xml.createElementNS(NS, "OPIEKUN");
            k.appendChild(opiekun);

            // ADRESY
            Element adresy = xml.createElementNS(NS, "ADRESY");
            k.appendChild(adresy);

            Element adres = xml.createElementNS(NS, "ADRES");
            adresy.appendChild(adres);

            adres.appendChild(make(xml, "STATUS", "aktualny"));
            adres.appendChild(make(xml, "EAN", " "));
            adres.appendChild(make(xml, "GLN", " "));
            adres.appendChild(make(xml, "NAZWA1", c.nazwa1));
            adres.appendChild(make(xml, "NAZWA2", c.nazwa2));
            adres.appendChild(make(xml, "NAZWA3", c.nazwa3));
            adres.appendChild(make(xml, "KRAJ", c.kraj));
            adres.appendChild(make(xml, "WOJEWODZTWO", c.wojewodztwo));
            adres.appendChild(make(xml, "POWIAT", c.powiat));
            adres.appendChild(make(xml, "GMINA", c.gmina));
            adres.appendChild(make(xml, "ULICA", c.ulica));
            adres.appendChild(make(xml, "NR_DOMU", c.nrDomu));
            adres.appendChild(make(xml, "NR_LOKALU", c.nrLokalu));
            adres.appendChild(make(xml, "MIASTO", c.miasto));
            adres.appendChild(make(xml, "KOD_POCZTOWY", c.kodPocztowy));
            adres.appendChild(make(xml, "POCZTA", c.poczta));
            adres.appendChild(make(xml, "DODATKOWE", " "));
            adres.appendChild(make(xml, "NIP_KRAJ", c.nipKraj != null ? c.nipKraj : " "));
            adres.appendChild(make(xml, "NIP", c.nip != null ? c.nip : " "));
            adres.appendChild(make(xml, "REGON", c.regon != null ? c.regon : " "));
            adres.appendChild(make(xml, "PESEL", c.pesel != null ? c.pesel : " "));
            adres.appendChild(make(xml, "TELEFON1", " "));
            adres.appendChild(make(xml, "TELEFON2", " "));
            adres.appendChild(make(xml, "FAX", " "));
            adres.appendChild(make(xml, "URL", " "));
            adres.appendChild(make(xml, "EMAIL", " "));

            // Reszta: GRUPY, KNT_RACHUNKI, WERYFIKACJE – możesz dodać później
        }
    }

    private Element make(Document xml, String name, String value) {
        Element el = xml.createElementNS(NS, name);
        if (value == null || value.isBlank()) { el.appendChild(xml.createCDATASection(" ")); 
        } 
        else { el.appendChild(xml.createCDATASection(value)); 
        }
        return el;
    }
}