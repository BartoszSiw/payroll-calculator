package pl.edashi.dms.xml;

import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsOutputPosition;
import pl.edashi.dms.model.DmsPosition;
import pl.edashi.dms.model.DmsPayment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

public class DmsOfflineXmlBuilder {

    private static final String NS = "http://www.comarch.pl/cdn/optima/offline";

    public String build(DmsDocumentOut doc) throws Exception {

        if (!"DS".equals(doc.typ)) {
            throw new IllegalArgumentException("DmsOfflineXmlBuilder: obecnie obsługiwany jest tylko typ DS, otrzymano: " + doc.typ);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document xml = factory.newDocumentBuilder().newDocument();

        // ROOT
        Element root = xml.createElementNS(NS, "ROOT");
        xml.appendChild(root);

        // SEKCJA REJESTRY_SPRZEDAZY_VAT
        Element rejSekcja = xml.createElementNS(NS, "REJESTRY_SPRZEDAZY_VAT");
        root.appendChild(rejSekcja);

        // Nagłówek sekcji
        rejSekcja.appendChild(make(xml, "WERSJA", "2.00"));
        rejSekcja.appendChild(make(xml, "BAZA_ZRD_ID", "KSIEG"));   // możesz potem wyciągnąć to z konfiguracji
        rejSekcja.appendChild(make(xml, "BAZA_DOC_ID", "DMSJB"));   // j.w.

        // Główny dokument: REJESTR_SPRZEDAZY_VAT
        Element rs = xml.createElementNS(NS, "REJESTR_SPRZEDAZY_VAT");
        rejSekcja.appendChild(rs);

        // META
        rs.appendChild(make(xml, "ID_ZRODLA", doc.idZrodla));
        rs.appendChild(make(xml, "MODUL", doc.modul));               // np. "Handel"
        rs.appendChild(make(xml, "TYP", doc.documentType));       // tutaj możesz w przyszłości zmapować typ → opis
        rs.appendChild(make(xml, "REJESTR", doc.rejestr));

        rs.appendChild(make(xml, "DATA_WYSTAWIENIA", doc.dataWystawienia));
        rs.appendChild(make(xml, "DATA_SPRZEDAZY", doc.dataSprzedazy));
        rs.appendChild(make(xml, "TERMIN", doc.termin));
        rs.appendChild(make(xml, "NUMER", doc.invoiceNumber));


        // Pola flagowe – na razie stałe wartości (można potem zmapować z doc)
        rs.appendChild(make(xml, "KOREKTA", "Nie"));
        rs.appendChild(make(xml, "KOREKTA_NUMER", ""));
        rs.appendChild(make(xml, "METODA_KASOWA", "Nie"));
        rs.appendChild(make(xml, "FISKALNA", "Nie"));
        rs.appendChild(make(xml, "DETALICZNA", "Nie"));
        rs.appendChild(make(xml, "WEWNETRZNA", "Nie"));
        rs.appendChild(make(xml, "EKSPORT", "nie"));
        rs.appendChild(make(xml, "FINALNY", "Nie"));
        rs.appendChild(make(xml, "PODATNIK_CZYNNY", "Tak"));

        // Identyfikator księgowy – na razie numer dokumentu
        rs.appendChild(make(xml, "IDENTYFIKATOR_KSIEGOWY", doc.numer));

        // PODMIOT (kontrahent)
        rs.appendChild(make(xml, "TYP_PODMIOTU", "kontrahent"));
        rs.appendChild(make(xml, "PODMIOT", "1")); // uproszczenie
        rs.appendChild(make(xml, "PODMIOT_ID", doc.podmiotId));
        rs.appendChild(make(xml, "PODMIOT_NIP", doc.podmiotNip));

        rs.appendChild(make(xml, "NAZWA1", doc.nazwa1));
        rs.appendChild(make(xml, "NAZWA2", doc.nazwa2));
        rs.appendChild(make(xml, "NAZWA3", doc.nazwa3));

        rs.appendChild(make(xml, "NIP_KRAJ", "PL"));   // na przyszłość możesz zmapować z doc.kraj
        rs.appendChild(make(xml, "NIP", doc.podmiotNip));
        rs.appendChild(make(xml, "KRAJ", doc.kraj));
        rs.appendChild(make(xml, "WOJEWODZTWO", doc.wojewodztwo));
        rs.appendChild(make(xml, "POWIAT", doc.powiat));
        rs.appendChild(make(xml, "GMINA", ""));        // brak w DmsDocument – na razie puste
        rs.appendChild(make(xml, "ULICA", doc.ulica));
        rs.appendChild(make(xml, "NR_DOMU", doc.nrDomu));
        rs.appendChild(make(xml, "NR_LOKALU", ""));
        rs.appendChild(make(xml, "MIASTO", doc.miasto));
        rs.appendChild(make(xml, "KOD_POCZTOWY", doc.kodPocztowy));
        rs.appendChild(make(xml, "POCZTA", doc.miasto)); // uproszczenie

        rs.appendChild(make(xml, "DODATKOWE", ""));
        rs.appendChild(make(xml, "TYP_PLATNIKA", "kontrahent"));
        rs.appendChild(make(xml, "PLATNIK", "1"));
        rs.appendChild(make(xml, "PLATNIK_ID", doc.podmiotId));
        rs.appendChild(make(xml, "PLATNIK_NIP", doc.podmiotNip));
        rs.appendChild(make(xml, "PESEL", ""));
        rs.appendChild(make(xml, "ROLNIK", "Nie"));

        // OPIS, waluta, forma płatności – na razie prosto
        rs.appendChild(make(xml, "OPIS", ""));
        rs.appendChild(make(xml, "WALUTA", "PLN")); // PLN domyślnie w systemie
        rs.appendChild(make(xml, "FORMA_PLATNOSCI", firstPaymentForm(doc)));
        rs.appendChild(make(xml, "FORMA_PLATNOSCI_ID", "")); // brak w modelu – zostawiamy puste

        // Deklaracje – na razie puste / domyślne
        rs.appendChild(make(xml, "DEKLARACJA_VAT7", ""));
        rs.appendChild(make(xml, "DEKLARACJA_VATUE", "Nie"));
        rs.appendChild(make(xml, "DEKLARACJA_VAT27", "Nie"));

        // Kursy – uproszczone, docelowo możesz zmapować z doc
        rs.appendChild(make(xml, "KURS_WALUTY", "NBP"));
        rs.appendChild(make(xml, "NOTOWANIE_WALUTY_ILE", "1"));
        rs.appendChild(make(xml, "NOTOWANIE_WALUTY_ZA_ILE", "1"));
        rs.appendChild(make(xml, "DATA_KURSU", doc.dataWystawienia));
        rs.appendChild(make(xml, "KURS_DO_KSIEGOWANIA", "Nie"));

        rs.appendChild(make(xml, "KURS_WALUTY_2", "NBP"));
        rs.appendChild(make(xml, "NOTOWANIE_WALUTY_ILE_2", "1"));
        rs.appendChild(make(xml, "NOTOWANIE_WALUTY_ZA_ILE_2", "1"));
        rs.appendChild(make(xml, "DATA_KURSU_2", doc.dataWystawienia));

        rs.appendChild(make(xml, "PLATNOSC_VAT_W_PLN", "Nie"));
        rs.appendChild(make(xml, "AKCYZA_NA_WEGIEL", "0"));
        rs.appendChild(make(xml, "FA_Z_PA", "Nie"));
        rs.appendChild(make(xml, "VAN_FA_Z_PA", "Nie"));
        rs.appendChild(make(xml, "VAN_RODZAJ", "0"));
        rs.appendChild(make(xml, "MPP", "Nie"));
        rs.appendChild(make(xml, "NR_KSEF", ""));
        rs.appendChild(make(xml, "DODATKOWY_OPIS", doc.dodatkowyOpis));

        // POZYCJE
        Element pozycje = xml.createElementNS(NS, "POZYCJE");
        rs.appendChild(pozycje);
        
        if (doc.pozycje != null) {
            for (DmsOutputPosition p : doc.pozycje) {
            	Element poz = xml.createElementNS(NS, "POZYCJA");
                pozycje.appendChild(poz);

                poz.appendChild(make(xml, "KATEGORIA_POS", p.kategoria));
                // KATEGORIA_ID_POS – brak w modelu, więc na razie puste
                poz.appendChild(make(xml, "KATEGORIA_ID_POS", ""));
                poz.appendChild(make(xml, "STAWKA_VAT", p.stawkaVat));
                // STATUS_VAT – uproszczenie
                poz.appendChild(make(xml, "STATUS_VAT", "opodatkowana"));

                poz.appendChild(make(xml, "NETTO", p.netto));
                poz.appendChild(make(xml, "VAT", p.vat));
                // NETTO_SYS / VAT_SYS – uproszczenie, kopiujemy
                poz.appendChild(make(xml, "NETTO_SYS", p.netto));
                poz.appendChild(make(xml, "VAT_SYS", p.vat));
                poz.appendChild(make(xml, "NETTO_SYS2", p.netto));
                poz.appendChild(make(xml, "VAT_SYS2", p.vat));

                poz.appendChild(make(xml, "RODZAJ_SPRZEDAZY", p.rodzajSprzedazy));
                // UWZ_W_PROPORCJI – na razie "Tak"
                poz.appendChild(make(xml, "UWZ_W_PROPORCJI", "Tak"));
            }
        }

        // PLATNOSCI
        Element platnosci = xml.createElementNS(NS, "PLATNOSCI");
        rs.appendChild(platnosci);

        if (doc.platnosci != null) {
            for (DmsPayment p : doc.platnosci) {
                Element plat = xml.createElementNS(NS, "PLATNOSC");
                platnosci.appendChild(plat);

                plat.appendChild(make(xml, "ID_ZRODLA_PLAT", doc.idZrodla)); // lub osobne ID jeśli masz
                plat.appendChild(make(xml, "TERMIN_PLAT", p.termin));
                plat.appendChild(make(xml, "FORMA_PLATNOSCI_PLAT", p.forma));
                plat.appendChild(make(xml, "FORMA_PLATNOSCI_ID_PLAT", ""));
                plat.appendChild(make(xml, "KWOTA_PLAT", p.kwota));
                plat.appendChild(make(xml, "WALUTA_PLAT", "PLN"));
                plat.appendChild(make(xml, "KURS_WALUTY_PLAT", "NBP"));
                plat.appendChild(make(xml, "NOTOWANIE_WALUTY_ILE_PLAT", "1"));
                plat.appendChild(make(xml, "NOTOWANIE_WALUTY_ZA_ILE_PLAT", "1"));
                plat.appendChild(make(xml, "KWOTA_PLN_PLAT", p.kwota));
                plat.appendChild(make(xml, "KIERUNEK", p.kierunek));
                plat.appendChild(make(xml, "PODLEGA_ROZLICZENIU", "tak"));
                plat.appendChild(make(xml, "KONTO", p.nrBank));
                plat.appendChild(make(xml, "NIE_NALICZAJ_ODSETEK", "Nie"));
                plat.appendChild(make(xml, "PRZELEW_SEPA", "Nie"));
                plat.appendChild(make(xml, "DATA_KURSU_PLAT", p.termin));
                plat.appendChild(make(xml, "WALUTA_DOK", "PLN"));
                plat.appendChild(make(xml, "PLATNOSC_TYP_PODMIOTU", "kontrahent"));
                plat.appendChild(make(xml, "PLATNOSC_PODMIOT", "1"));
                plat.appendChild(make(xml, "PLATNOSC_PODMIOT_ID", doc.podmiotId));
                plat.appendChild(make(xml, "PLATNOSC_PODMIOT_NIP", doc.podmiotNip));
                plat.appendChild(make(xml, "PLAT_ELIXIR_O1", p.opis));
                plat.appendChild(make(xml, "PLAT_ELIXIR_O2", ""));
                plat.appendChild(make(xml, "PLAT_ELIXIR_O3", ""));
                plat.appendChild(make(xml, "PLAT_ELIXIR_O4", ""));
                plat.appendChild(make(xml, "PLAT_FA_Z_PA", "Nie"));
                plat.appendChild(make(xml, "PLAT_VAN_FA_Z_PA", "Nie"));
                plat.appendChild(make(xml, "PLAT_SPLIT_PAYMENT", "Nie"));
                plat.appendChild(make(xml, "PLAT_SPLIT_KWOTA_VAT", doc.vatAmount != null ? doc.vatAmount : ""));
                plat.appendChild(make(xml, "PLAT_SPLIT_NIP", doc.podmiotNip));
                plat.appendChild(make(xml, "PLAT_SPLIT_NR_DOKUMENTU", doc.numer));
            }
        }

        // KODY_JPK – na razie puste
        Element kodyJpk = xml.createElementNS(NS, "KODY_JPK");
        rs.appendChild(kodyJpk);

        // ATRYBUTY – na razie puste
        Element atrybuty = xml.createElementNS(NS, "ATRYBUTY");
        rs.appendChild(atrybuty);

        // UWAGI – z Twojej listy doc.uwagi
        Element uwagi = xml.createElementNS(NS, "UWAGI");
        rs.appendChild(uwagi);

        if (doc.uwagi != null) {
            for (String note : doc.uwagi) {
                uwagi.appendChild(make(xml, "UWAGA", note));
            }
        }

        // SERIALIZACJA
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(xml), new StreamResult(writer));

        return writer.toString();
    }

    private Element make(Document doc, String name, String value) {
        Element el = doc.createElementNS(NS, name);
        // Wymuszamy CDATA nawet jeśli wartość jest null lub pusta
        if (value == null || value.isBlank()) {
            value = " ";
        }


        el.appendChild(doc.createCDATASection(value));
        return el;
    }

    private String firstPaymentForm(DmsDocumentOut doc) {
        if (doc.platnosci != null && !doc.platnosci.isEmpty()) {
            return doc.platnosci.get(0).forma;
        }
        return "";
    }
}
