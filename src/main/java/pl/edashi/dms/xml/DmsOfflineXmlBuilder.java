package pl.edashi.dms.xml;

import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsOutputPosition;
import pl.edashi.dms.model.DmsPayment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;

public class DmsOfflineXmlBuilder implements XmlSectionBuilder {

    private static final String NS = "http://www.comarch.pl/cdn/optima/offline";
    private final DmsDocumentOut doc;
    public DmsOfflineXmlBuilder(DmsDocumentOut doc) {
        if (!"DS".equals(doc.typ)) {
            throw new IllegalArgumentException("DmsOfflineXmlBuilder: obsługiwany jest tylko typ DS, otrzymano: " + doc.typ);
        }
        this.doc = doc;
    }
    @Override 
    public void build(Document docXml, Element root) {


        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
       // Document xml = factory.newDocumentBuilder().newDocument();

        // ROOT
        //Element root = xml.createElementNS(NS, "ROOT");
        //xml.appendChild(root);

        // SEKCJA REJESTRY_SPRZEDAZY_VAT
        Element rejSekcja = docXml.createElementNS(NS, "REJESTRY_SPRZEDAZY_VAT");
        root.appendChild(rejSekcja);

        // Nagłówek sekcji
        rejSekcja.appendChild(make(docXml, "WERSJA", "2.00"));
        rejSekcja.appendChild(make(docXml, "BAZA_ZRD_ID", "KSIEG"));   // możesz potem wyciągnąć to z konfiguracji
        rejSekcja.appendChild(make(docXml, "BAZA_DOC_ID", "DMSJB"));   // j.w.

        // Główny dokument: REJESTR_SPRZEDAZY_VAT
        Element rs = docXml.createElementNS(NS, "REJESTR_SPRZEDAZY_VAT");
        rejSekcja.appendChild(rs);

        // META
        rs.appendChild(make(docXml, "ID_ZRODLA", doc.idZrodla));
        rs.appendChild(make(docXml, "MODUL", doc.modul));               // np. "Handel"
        rs.appendChild(make(docXml, "TYP", doc.documentType));       // tutaj możesz w przyszłości zmapować typ → opis
        rs.appendChild(make(docXml, "REJESTR", doc.rejestr));

        rs.appendChild(make(docXml, "DATA_WYSTAWIENIA", doc.dataWystawienia));
        rs.appendChild(make(docXml, "DATA_SPRZEDAZY", doc.dataSprzedazy));
        rs.appendChild(make(docXml, "TERMIN", doc.termin));
        rs.appendChild(make(docXml, "NUMER", doc.documentType + "_" + doc.invoiceNumber));


        // Pola flagowe – na razie stałe wartości (można potem zmapować z doc)
        rs.appendChild(make(docXml, "KOREKTA", "Nie"));
        rs.appendChild(make(docXml, "KOREKTA_NUMER", ""));
        rs.appendChild(make(docXml, "METODA_KASOWA", "Nie"));
        rs.appendChild(make(docXml, "FISKALNA", "Nie"));
        rs.appendChild(make(docXml, "DETALICZNA", "Nie"));
        rs.appendChild(make(docXml, "WEWNETRZNA", "Nie"));
        rs.appendChild(make(docXml, "EKSPORT", "nie"));
        rs.appendChild(make(docXml, "FINALNY", "Nie"));
        rs.appendChild(make(docXml, "PODATNIK_CZYNNY", "Tak"));

        // Identyfikator księgowy – na razie numer dokumentu
        rs.appendChild(make(docXml, "IDENTYFIKATOR_KSIEGOWY", doc.numer));

        // PODMIOT (kontrahent)
        rs.appendChild(make(docXml, "TYP_PODMIOTU", "kontrahent"));
        //rs.appendChild(make(docXml, "PODMIOT", "1")); // uproszczenie
        //rs.appendChild(make(docXml, "PODMIOT_ID", doc.podmiotId));
        rs.appendChild(make(docXml, "PODMIOT_NIP", doc.podmiotNip));

        rs.appendChild(make(docXml, "NAZWA1", doc.nazwa1));
        rs.appendChild(make(docXml, "NAZWA2", doc.nazwa2));
        rs.appendChild(make(docXml, "NAZWA3", doc.nazwa3));

        rs.appendChild(make(docXml, "NIP_KRAJ", "PL"));   // na przyszłość możesz zmapować z doc.kraj
        rs.appendChild(make(docXml, "NIP", doc.podmiotNip));
        rs.appendChild(make(docXml, "KRAJ", doc.kraj));
        rs.appendChild(make(docXml, "WOJEWODZTWO", doc.wojewodztwo));
        rs.appendChild(make(docXml, "POWIAT", doc.powiat));
        rs.appendChild(make(docXml, "GMINA", ""));        // brak w DmsDocument – na razie puste
        rs.appendChild(make(docXml, "ULICA", doc.ulica));
        rs.appendChild(make(docXml, "NR_DOMU", doc.nrDomu));
        rs.appendChild(make(docXml, "NR_LOKALU", ""));
        rs.appendChild(make(docXml, "MIASTO", doc.miasto));
        rs.appendChild(make(docXml, "KOD_POCZTOWY", doc.kodPocztowy));
        rs.appendChild(make(docXml, "POCZTA", doc.miasto)); // uproszczenie

        rs.appendChild(make(docXml, "DODATKOWE", ""));
        rs.appendChild(make(docXml, "TYP_PLATNIKA", "kontrahent"));
        //rs.appendChild(make(docXml, "PLATNIK", "1"));
        //rs.appendChild(make(docXml, "PLATNIK_ID", doc.podmiotId));
        rs.appendChild(make(docXml, "PLATNIK_NIP", doc.podmiotNip));
        rs.appendChild(make(docXml, "PESEL", ""));
        rs.appendChild(make(docXml, "ROLNIK", "Nie"));

        // OPIS, waluta, forma płatności – na razie prosto
        rs.appendChild(make(docXml, "OPIS", ""));
        rs.appendChild(make(docXml, "WALUTA", "PLN")); // PLN domyślnie w systemie
        rs.appendChild(make(docXml, "FORMA_PLATNOSCI", firstPaymentForm(doc)));
        rs.appendChild(make(docXml, "FORMA_PLATNOSCI_ID", "")); // brak w modelu – zostawiamy puste

        // Deklaracje – na razie puste / domyślne
        rs.appendChild(make(docXml, "DEKLARACJA_VAT7", ""));
        rs.appendChild(make(docXml, "DEKLARACJA_VATUE", "Nie"));
        rs.appendChild(make(docXml, "DEKLARACJA_VAT27", "Nie"));

        // Kursy – uproszczone, docelowo możesz zmapować z doc
        rs.appendChild(make(docXml, "KURS_WALUTY", "NBP"));
        rs.appendChild(make(docXml, "NOTOWANIE_WALUTY_ILE", "1"));
        rs.appendChild(make(docXml, "NOTOWANIE_WALUTY_ZA_ILE", "1"));
        rs.appendChild(make(docXml, "DATA_KURSU", doc.dataWystawienia));
        rs.appendChild(make(docXml, "KURS_DO_KSIEGOWANIA", "Nie"));

        rs.appendChild(make(docXml, "KURS_WALUTY_2", "NBP"));
        rs.appendChild(make(docXml, "NOTOWANIE_WALUTY_ILE_2", "1"));
        rs.appendChild(make(docXml, "NOTOWANIE_WALUTY_ZA_ILE_2", "1"));
        rs.appendChild(make(docXml, "DATA_KURSU_2", doc.dataWystawienia));

        rs.appendChild(make(docXml, "PLATNOSC_VAT_W_PLN", "Nie"));
        rs.appendChild(make(docXml, "AKCYZA_NA_WEGIEL", "0"));
        rs.appendChild(make(docXml, "FA_Z_PA", "Nie"));
        rs.appendChild(make(docXml, "VAN_FA_Z_PA", "Nie"));
        rs.appendChild(make(docXml, "VAN_RODZAJ", "0"));
        rs.appendChild(make(docXml, "MPP", "Nie"));
        rs.appendChild(make(docXml, "NR_KSEF", ""));
        rs.appendChild(make(docXml, "DODATKOWY_OPIS", doc.dodatkowyOpis));

        // POZYCJE
        Element pozycje = docXml.createElementNS(NS, "POZYCJE");
        rs.appendChild(pozycje);
        
        if (doc.pozycje != null) {
            for (DmsOutputPosition p : doc.pozycje) {
            	Element poz = docXml.createElementNS(NS, "POZYCJA");
                pozycje.appendChild(poz);

                poz.appendChild(make(docXml, "KATEGORIA_POS", p.kanalKategoria));
                // KATEGORIA_ID_POS – brak w modelu, więc na razie puste
                poz.appendChild(make(docXml, "KATEGORIA_ID_POS", ""));
                poz.appendChild(make(docXml, "STAWKA_VAT", p.stawkaVat));
                // STATUS_VAT – uproszczenie
                poz.appendChild(make(docXml, "STATUS_VAT", "opodatkowana"));

                poz.appendChild(make(docXml, "NETTO", p.netto));
                poz.appendChild(make(docXml, "VAT", p.vat));
                // NETTO_SYS / VAT_SYS – uproszczenie, kopiujemy
                poz.appendChild(make(docXml, "NETTO_SYS", p.netto));
                poz.appendChild(make(docXml, "VAT_SYS", p.vat));
                poz.appendChild(make(docXml, "NETTO_SYS2", p.netto));
                poz.appendChild(make(docXml, "VAT_SYS2", p.vat));

                poz.appendChild(make(docXml, "RODZAJ_SPRZEDAZY", p.rodzajSprzedazy));
                // UWZ_W_PROPORCJI – na razie "Tak"
                poz.appendChild(make(docXml, "UWZ_W_PROPORCJI", "Tak"));
            }
        }

        // PLATNOSCI
        Element platnosci = docXml.createElementNS(NS, "PLATNOSCI");
        rs.appendChild(platnosci);

        if (doc.platnosci != null) {
            for (DmsPayment p : doc.platnosci) {
                Element plat = docXml.createElementNS(NS, "PLATNOSC");
                platnosci.appendChild(plat);

                plat.appendChild(make(docXml, "ID_ZRODLA_PLAT", doc.idZrodla)); // lub osobne ID jeśli masz
                plat.appendChild(make(docXml, "TERMIN_PLAT", p.termin));
                plat.appendChild(make(docXml, "FORMA_PLATNOSCI_PLAT", p.forma));
                plat.appendChild(make(docXml, "FORMA_PLATNOSCI_ID_PLAT", ""));
                plat.appendChild(make(docXml, "KWOTA_PLAT", p.kwota));
                plat.appendChild(make(docXml, "WALUTA_PLAT", "PLN"));
                plat.appendChild(make(docXml, "KURS_WALUTY_PLAT", "NBP"));
                plat.appendChild(make(docXml, "NOTOWANIE_WALUTY_ILE_PLAT", "1"));
                plat.appendChild(make(docXml, "NOTOWANIE_WALUTY_ZA_ILE_PLAT", "1"));
                plat.appendChild(make(docXml, "KWOTA_PLN_PLAT", p.kwota));
                plat.appendChild(make(docXml, "KIERUNEK", p.kierunek));
                plat.appendChild(make(docXml, "PODLEGA_ROZLICZENIU", "tak"));
                plat.appendChild(make(docXml, "KONTO", p.nrBank));
                plat.appendChild(make(docXml, "NIE_NALICZAJ_ODSETEK", "Nie"));
                plat.appendChild(make(docXml, "PRZELEW_SEPA", "Nie"));
                plat.appendChild(make(docXml, "DATA_KURSU_PLAT", p.termin));
                plat.appendChild(make(docXml, "WALUTA_DOK", "PLN"));
                plat.appendChild(make(docXml, "PLATNOSC_TYP_PODMIOTU", "kontrahent"));
                //plat.appendChild(make(docXml, "PLATNOSC_PODMIOT", "1"));
                //plat.appendChild(make(docXml, "PLATNOSC_PODMIOT_ID", doc.podmiotId));
                plat.appendChild(make(docXml, "PLATNOSC_PODMIOT_NIP", doc.podmiotNip));
                plat.appendChild(make(docXml, "PLAT_ELIXIR_O1", p.opis));
                plat.appendChild(make(docXml, "PLAT_ELIXIR_O2", ""));
                plat.appendChild(make(docXml, "PLAT_ELIXIR_O3", ""));
                plat.appendChild(make(docXml, "PLAT_ELIXIR_O4", ""));
                plat.appendChild(make(docXml, "PLAT_FA_Z_PA", "Nie"));
                plat.appendChild(make(docXml, "PLAT_VAN_FA_Z_PA", "Nie"));
                plat.appendChild(make(docXml, "PLAT_SPLIT_PAYMENT", "Nie"));
                plat.appendChild(make(docXml, "PLAT_SPLIT_KWOTA_VAT", p.vatZ != null ? p.vatZ : ""));
                plat.appendChild(make(docXml, "PLAT_SPLIT_NIP", doc.podmiotNip));
                plat.appendChild(make(docXml, "PLAT_SPLIT_NR_DOKUMENTU", doc.numer));
            }
        }

        // KODY_JPK – na razie puste
        Element kodyJpk = docXml.createElementNS(NS, "KODY_JPK");
        rs.appendChild(kodyJpk);

        // ATRYBUTY – na razie puste
        Element atrybuty = docXml.createElementNS(NS, "ATRYBUTY");
        rs.appendChild(atrybuty);

        // UWAGI – z Twojej listy doc.uwagi
        Element uwagi = docXml.createElementNS(NS, "UWAGI");
        rs.appendChild(uwagi);

        if (doc.uwagi != null) {
            for (String note : doc.uwagi) {
                uwagi.appendChild(make(docXml, "UWAGA", note));
            }
        }
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
