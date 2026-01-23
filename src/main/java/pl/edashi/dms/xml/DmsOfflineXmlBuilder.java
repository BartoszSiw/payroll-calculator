package pl.edashi.dms.xml;

import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsOutputPosition;
import pl.edashi.dms.model.DmsPayment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

public class DmsOfflineXmlBuilder implements XmlSectionBuilder {

    private static final String NS = "http://www.comarch.pl/cdn/optima/offline";
    private final DmsDocumentOut doc;
    public DmsOfflineXmlBuilder(DmsDocumentOut doc) {
        if (doc == null) throw new IllegalArgumentException("DmsOfflineXmlBuilder: doc is null");
        Set<String> DS_TYPES = Set.of("DS", "FV", "PR", "FZL", "FVK", "RWS","PRK", "FZLK", "FVU", "FVM", "FVG");
    	 if (!DS_TYPES.contains(doc.getTyp())) {
            throw new IllegalArgumentException("DmsOfflineXmlBuilder: obsługiwany jest tylko typ DS, otrzymano: " + safe(doc.getTyp()));
        }
        this.doc = doc;
    }
    @Override 
    public void build(Document docXml, Element root) {
        if (docXml == null || root == null) return;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
       // Document xml = factory.newDocumentBuilder().newDocument();
        //System.out.println("[BUILDER] typ=" + doc.getTyp() + ", documentType=" + doc.getDocumentType());

        // ROOT
        //Element root = xml.createElementNS(NS, "ROOT");
        //xml.appendChild(root);

        // SEKCJA REJESTRY_SPRZEDAZY_VAT
        Element rejSekcja = docXml.createElementNS(NS, "REJESTRY_SPRZEDAZY_VAT");
        root.appendChild(rejSekcja);

        // Nagłówek sekcji
        rejSekcja.appendChild(make(docXml, "WERSJA", "2.00"));
        rejSekcja.appendChild(make(docXml, "BAZA_ZRD_ID", "KSIEG"));   // możesz potem wyciągnąć to z konfiguracji
        rejSekcja.appendChild(make(docXml, "BAZA_DOC_ID", "DMS_1"));   // j.w.

        // Główny dokument: REJESTR_SPRZEDAZY_VAT
        Element rs = docXml.createElementNS(NS, "REJESTR_SPRZEDAZY_VAT");
        rejSekcja.appendChild(rs);

        // META
        rs.appendChild(make(docXml, "ID_ZRODLA", safe(doc.getIdZrodla())));
        rs.appendChild(make(docXml, "MODUL", safe(doc.getModul())));               // np. "Handel"
        rs.appendChild(make(docXml, "TYP", safe(doc.getDocumentType())));       // tutaj możesz w przyszłości zmapować typ → opis
        rs.appendChild(make(docXml, "REJESTR", safe(doc.getRejestr())));

        rs.appendChild(make(docXml, "DATA_WYSTAWIENIA", safe(doc.getDataWystawienia())));
        rs.appendChild(make(docXml, "DATA_SPRZEDAZY", safe(doc.getDataSprzedazy())));
        rs.appendChild(make(docXml, "TERMIN",safe(doc.getTermin())));
        rs.appendChild(make(docXml, "NUMER", safe(doc.getDocumentType()) + "_" + safe(doc.getInvoiceNumber())));

        // Pola flagowe – na razie stałe wartości (można potem zmapować z doc)
        rs.appendChild(make(docXml, "KOREKTA", doc.getKorekta()));
        rs.appendChild(make(docXml, "KOREKTA_NUMER", doc.getKorektaNumer()));
        rs.appendChild(make(docXml, "METODA_KASOWA", "Nie"));
        rs.appendChild(make(docXml, "FISKALNA", doc.getDokumentFiskalny()));
        rs.appendChild(make(docXml, "DETALICZNA", doc.getDokumentDetaliczny()));
        rs.appendChild(make(docXml, "WEWNETRZNA", doc.getDocumentWewne()));
        rs.appendChild(make(docXml, "EKSPORT", "nie"));
        rs.appendChild(make(docXml, "FINALNY", "Nie"));
        rs.appendChild(make(docXml, "PODATNIK_CZYNNY", doc.getCzynny()));

        // Identyfikator księgowy – na razie numer dokumentu
        rs.appendChild(make(docXml, "IDENTYFIKATOR_KSIEGOWY", ""));//safe(doc.getNumer())

        // PODMIOT (kontrahent)
        rs.appendChild(make(docXml, "TYP_PODMIOTU", "kontrahent"));
        rs.appendChild(make(docXml, "PODMIOT", safe(doc.getPodmiotAkronim()))); // uproszczenie
        //rs.appendChild(make(docXml, "PODMIOT_ID", doc.podmiotId));
        rs.appendChild(make(docXml, "PODMIOT_NIP", safe(doc.getPodmiotNip())));

        rs.appendChild(make(docXml, "NAZWA1", safe(doc.getNazwa1())));
        rs.appendChild(make(docXml, "NAZWA2", safe(doc.getNazwa2())));
        rs.appendChild(make(docXml, "NAZWA3", safe(doc.getNazwa3())));

        rs.appendChild(make(docXml, "NIP_KRAJ", "PL"));   // na przyszłość możesz zmapować z doc.kraj
        rs.appendChild(make(docXml, "NIP", safe(doc.getPodmiotNip())));
        rs.appendChild(make(docXml, "KRAJ", safe(doc.getKraj())));
        rs.appendChild(make(docXml, "WOJEWODZTWO", safe(doc.getWojewodztwo())));
        rs.appendChild(make(docXml, "POWIAT", safe(doc.getPowiat())));
        rs.appendChild(make(docXml, "GMINA", ""));        // brak w DmsDocument – na razie puste
        rs.appendChild(make(docXml, "ULICA", safe(doc.getUlica())));
        rs.appendChild(make(docXml, "NR_DOMU", safe(doc.getNrDomu())));
        rs.appendChild(make(docXml, "NR_LOKALU", ""));
        rs.appendChild(make(docXml, "MIASTO", safe(doc.getMiasto())));
        rs.appendChild(make(docXml, "KOD_POCZTOWY", safe(doc.getKodPocztowy())));
        rs.appendChild(make(docXml, "POCZTA", safe(doc.getMiasto()))); // uproszczenie

        rs.appendChild(make(docXml, "DODATKOWE", ""));
        rs.appendChild(make(docXml, "TYP_PLATNIKA", "kontrahent"));
        rs.appendChild(make(docXml, "PLATNIK", safe(doc.getPodmiotAkronim())));
        //rs.appendChild(make(docXml, "PLATNIK_ID", doc.podmiotId));
        rs.appendChild(make(docXml, "PLATNIK_NIP", safe(doc.getPodmiotNip())));
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
        rs.appendChild(make(docXml, "DATA_KURSU", safe(doc.getDataWystawienia())));
        rs.appendChild(make(docXml, "KURS_DO_KSIEGOWANIA", "Nie"));

        rs.appendChild(make(docXml, "KURS_WALUTY_2", "NBP"));
        rs.appendChild(make(docXml, "NOTOWANIE_WALUTY_ILE_2", "1"));
        rs.appendChild(make(docXml, "NOTOWANIE_WALUTY_ZA_ILE_2", "1"));
        rs.appendChild(make(docXml, "DATA_KURSU_2", safe(doc.getDataWystawienia())));

        rs.appendChild(make(docXml, "PLATNOSC_VAT_W_PLN", "Nie"));
        rs.appendChild(make(docXml, "AKCYZA_NA_WEGIEL", "0"));
        rs.appendChild(make(docXml, "FA_Z_PA", "Nie"));
        rs.appendChild(make(docXml, "VAN_FA_Z_PA", "Nie"));
        rs.appendChild(make(docXml, "VAN_RODZAJ", "0"));
        rs.appendChild(make(docXml, "MPP", "Nie"));
        rs.appendChild(make(docXml, "NR_KSEF", ""));
        rs.appendChild(make(docXml, "DODATKOWY_OPIS", doc.getDodatkowyOpis()));

        // POZYCJE
        Element pozycje = docXml.createElementNS(NS, "POZYCJE");
        rs.appendChild(pozycje);
        
        if (doc.getPozycje() != null) {
            for (DmsOutputPosition p : doc.getPozycje()) {
            	Element poz = docXml.createElementNS(NS, "POZYCJA");
                pozycje.appendChild(poz);

                poz.appendChild(make(docXml, "KATEGORIA_POS", safe(p.getKategoria())));
                // KATEGORIA_ID_POS – brak w modelu, więc na razie puste
                poz.appendChild(make(docXml, "KATEGORIA_ID_POS", ""));
                poz.appendChild(make(docXml, "KATEGORIA_POS_2", safe(p.getKanalKategoria())));
                poz.appendChild(make(docXml, "KATEGORIA_ID_POS_2", safe(p.getKategoriaId())));
                poz.appendChild(make(docXml, "STAWKA_VAT", safe(p.getStawkaVat())));
                // STATUS_VAT – uproszczenie
                poz.appendChild(make(docXml, "STATUS_VAT", safe(p.getStatusVat())));
                // NETTO_SYS / VAT_SYS – uproszczenie, kopiujemy
                poz.appendChild(make(docXml, "NETTO", safe(p.getNetto())));
                poz.appendChild(make(docXml, "VAT", safe(p.getVat())));
                poz.appendChild(make(docXml, "NETTO_SYS", safe(p.getNetto())));
                poz.appendChild(make(docXml, "VAT_SYS", safe(p.getVat())));
                poz.appendChild(make(docXml, "NETTO_SYS2", safe(p.getNetto())));
                poz.appendChild(make(docXml, "VAT_SYS2", safe(p.getVat())));
                //System.out.println("[BUILDER] netto=" + safe(p.getNetto()));
                //System.out.println("[BUILDER] vat=" + safe(p.getVat()));
                //System.out.println("[BUILDER] vatZ=" + safe(p.getVatZ()));

                poz.appendChild(make(docXml, "RODZAJ_SPRZEDAZY", safe(p.getRodzajSprzedazy())));
                // UWZ_W_PROPORCJI – na razie "Tak"
                poz.appendChild(make(docXml, "UWZ_W_PROPORCJI", safe(doc.getUwzgProp())));
            }
        }

        // PLATNOSCI
        if (doc.getPlatnosci() != null && !doc.getPlatnosci().isEmpty()) {
            Element platnosci = docXml.createElementNS(NS, "PLATNOSCI");
            rs.appendChild(platnosci);
            // dodaj PLATNOSC...
        List<DmsPayment> platnosciList = doc.getPlatnosci();
        if (platnosciList != null && !platnosciList.isEmpty()) {
            for (DmsPayment p : platnosciList) {
            	if (p.isAdvance()) continue; // NIE eksportujemy zaliczek
                Element plat = docXml.createElementNS(NS, "PLATNOSC");
                platnosci.appendChild(plat);
                plat.appendChild(make(docXml, "ID_ZRODLA_PLAT", safe(p.getIdPlatn()))); // osobne UUID 
                plat.appendChild(make(docXml, "TERMIN_PLAT", safe(p.getTerminPlatnosci())));
                plat.appendChild(make(docXml, "FORMA_PLATNOSCI_PLAT", safe(p.getForma())));
                plat.appendChild(make(docXml, "FORMA_PLATNOSCI_ID_PLAT", ""));
                plat.appendChild(make(docXml, "KWOTA_PLAT", safe(p.getKwota())));
                plat.appendChild(make(docXml, "WALUTA_PLAT", "PLN"));
                plat.appendChild(make(docXml, "KURS_WALUTY_PLAT", "NBP"));
                plat.appendChild(make(docXml, "NOTOWANIE_WALUTY_ILE_PLAT", "1"));
                plat.appendChild(make(docXml, "NOTOWANIE_WALUTY_ZA_ILE_PLAT", "1"));
                plat.appendChild(make(docXml, "KWOTA_PLN_PLAT", safe(p.getKwota())));
                plat.appendChild(make(docXml, "KIERUNEK", safe(p.getKierunek())));
                plat.appendChild(make(docXml, "PODLEGA_ROZLICZENIU", "tak"));
                plat.appendChild(make(docXml, "KONTO", safe(p.getNrBank())));
                plat.appendChild(make(docXml, "NIE_NALICZAJ_ODSETEK", "Nie"));
                plat.appendChild(make(docXml, "PRZELEW_SEPA", "Nie"));
                plat.appendChild(make(docXml, "DATA_KURSU_PLAT", safe(p.getTermin())));
                plat.appendChild(make(docXml, "WALUTA_DOK", "PLN"));
                plat.appendChild(make(docXml, "PLATNOSC_TYP_PODMIOTU", "kontrahent"));
                plat.appendChild(make(docXml, "PLATNOSC_PODMIOT", safe(doc.getPodmiotAkronim())));
                //plat.appendChild(make(docXml, "PLATNOSC_PODMIOT_ID", doc.podmiotId));
                plat.appendChild(make(docXml, "PLATNOSC_PODMIOT_NIP", safe(doc.getPodmiotNip())));
                plat.appendChild(make(docXml, "PLAT_ELIXIR_O1", safe(p.getOpis())));
                plat.appendChild(make(docXml, "PLAT_ELIXIR_O2", ""));
                plat.appendChild(make(docXml, "PLAT_ELIXIR_O3", ""));
                plat.appendChild(make(docXml, "PLAT_ELIXIR_O4", ""));
                plat.appendChild(make(docXml, "PLAT_FA_Z_PA", "Nie"));
                plat.appendChild(make(docXml, "PLAT_VAN_FA_Z_PA", "Nie"));
                plat.appendChild(make(docXml, "PLAT_SPLIT_PAYMENT", "Nie"));
                plat.appendChild(make(docXml, "PLAT_SPLIT_KWOTA_VAT", safe(p.getVatZ()) != null ? safe(p.getVatZ()) : ""));
                plat.appendChild(make(docXml, "PLAT_SPLIT_NIP", safe(doc.getPodmiotNip())));
                plat.appendChild(make(docXml, "PLAT_SPLIT_NR_DOKUMENTU", safe(doc.getNumer())));
            }
        }  //else {
        	//platnosci.appendChild(docXml.createCDATASection(" "));
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

        if (doc.getUwagi() != null) {
            for (String note : doc.getUwagi()) {
                uwagi.appendChild(make(docXml, "UWAGA", note));
            }
        }
    }

    private Element make(Document docXml, String name, String value) {
        if (docXml == null) throw new IllegalArgumentException("docXml is null");
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name is null or empty");

        Element el = docXml.createElementNS(NS, name);

        // Wymuszamy CDATA nawet jeśli wartość jest null lub pusta
        String v = value == null ? "" : value;
        if (v.isBlank()) v = " ";

        // CDATA nie może zawierać sekwencji "]]>" — rozbijamy/uciekamy ją bez zmiany treści
        // zamieniamy "]]>" na "]]]]><![CDATA[>" co tworzy dwa CDATA obok siebie po serializacji
        String safeValue = v.replace("]]>", "]]]]><![CDATA[>");

        el.appendChild(docXml.createCDATASection(safeValue));
        return el;
    }

    private String firstPaymentForm(DmsDocumentOut doc) {
        if (doc.getPlatnosci() != null && !doc.getPlatnosci().isEmpty()) {
            return doc.getPlatnosci().get(0).getForma();
        }
        return "";
    }
    private static String safe(String s) {
        return s == null ? "" : s;
    }

}
