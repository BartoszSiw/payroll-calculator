package pl.edashi.dms.xml;

import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsOutputPosition;
import pl.edashi.dms.model.DmsPayment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import java.util.Locale;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public class DmsOfflinePurchaseBuilder implements XmlSectionBuilder {

    private static final String NS = "http://www.comarch.pl/cdn/optima/offline";
    private final DmsDocumentOut doc;

    public DmsOfflinePurchaseBuilder(DmsDocumentOut doc) {
        if (doc == null) throw new IllegalArgumentException("DmsOfflinePurchaseBuilder: doc is null");
        Set<String> PURCHASE_TYPES = Set.of("DZ", "FVZ", "FVZk", "FS", "FK");
        if (!PURCHASE_TYPES.contains(doc.getTyp())) {
            throw new IllegalArgumentException("DmsOfflinePurchaseBuilder: obsługiwany jest tylko typ zakupowy, otrzymano: " + safe(doc.getTyp()));
        }
        this.doc = doc;
    }

    @Override
    public void build(Document docXml, Element root) {
        if (docXml == null || root == null) return;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        // SEKCJA REJESTRY_ZAKUPU_VAT
        Element rejSekcja = docXml.createElementNS(NS, "REJESTRY_ZAKUPU_VAT");
        root.appendChild(rejSekcja);

        // Nagłówek sekcji
        rejSekcja.appendChild(makeCdata(docXml, "WERSJA", "2.00"));
        rejSekcja.appendChild(makeCdata(docXml, "BAZA_ZRD_ID", "KSIEG"));
        rejSekcja.appendChild(makeCdata(docXml, "BAZA_DOC_ID", "DMS_1"));

        // REJESTR_ZAKUPU_VAT
        Element rz = docXml.createElementNS(NS, "REJESTR_ZAKUPU_VAT");
        rejSekcja.appendChild(rz);

        // META
        rz.appendChild(makeCdata(docXml, "ID_ZRODLA", safe(doc.getIdZrodla())));
        rz.appendChild(makeCdata(docXml, "MODUL", safe(doc.getModul() != null ? doc.getModul() : "Rejestr Vat")));
        rz.appendChild(makeCdata(docXml, "TYP", "Rejestr zakupu"));
        rz.appendChild(makeCdata(docXml, "REJESTR", safe(doc.getRejestr())));

        rz.appendChild(makeCdata(docXml, "DATA_WYSTAWIENIA", safe(doc.getDataWystawienia())));
        rz.appendChild(makeCdata(docXml, "DATA_ZAKUPU", safe(doc.getDataSprzedazy())));
        rz.appendChild(makeCdata(docXml, "DATA_WPLYWU", safe(doc.getDataOperacji())));
        rz.appendChild(makeCdata(docXml, "TERMIN", safe(doc.getTermin())));
        rz.appendChild(makeCdata(docXml, "DATA_DATAOBOWIAZKUPODATKOWEGO", safe(doc.getDataObowiazkuPodatkowego())));
        rz.appendChild(makeCdata(docXml, "DATA_DATAPRAWAODLICZENIA", safe(doc.getDataPrawaOdliczenia())));
        rz.appendChild(makeCdata(docXml, "NUMER",  safe(doc.getDocumentType()) + "_" + safe(doc.getInvoiceNumber())));
        rz.appendChild(makeCdata(docXml, "KOREKTA", doc.getKorekta()));
        rz.appendChild(makeCdata(docXml, "KOREKTA_NUMER", safe(doc.getKorektaNumer())));
        rz.appendChild(makeCdata(docXml, "WEWNETRZNA", "Nie"));
        rz.appendChild(makeCdata(docXml, "METODA_KASOWA", "Nie"));
        rz.appendChild(makeCdata(docXml, "FISKALNA", "Nie"));
        rz.appendChild(makeCdata(docXml, "DETALICZNA", "Nie"));
        rz.appendChild(makeCdata(docXml, "EKSPORT", "nie"));
        rz.appendChild(makeCdata(docXml, "FINALNY", "Nie"));
        rz.appendChild(makeCdata(docXml, "PODATNIK_CZYNNY", "Tak"));
        rz.appendChild(makeCdata(docXml, "IDENTYFIKATOR_KSIEGOWY", safe(doc.getNumer())));

        // PODMIOT (dostawca)
        rz.appendChild(makeCdata(docXml, "TYP_PODMIOTU", "kontrahent"));
        //rz.appendChild(makeCdata(docXml, "PODMIOT", safe(doc.getPodmiotAkronim())));
        //rz.appendChild(makeCdata(docXml, "PODMIOT_ID", safe(doc.getPodmiotId())));
        rz.appendChild(makeCdata(docXml, "PODMIOT_NIP", safe(doc.getPodmiotNip())));
        rz.appendChild(makeCdata(docXml, "NAZWA1", safe(doc.getNazwa1())));
        rz.appendChild(makeCdata(docXml, "NAZWA2", safe(doc.getNazwa2())));
        rz.appendChild(makeCdata(docXml, "NAZWA3", safe(doc.getNazwa3())));
        rz.appendChild(makeCdata(docXml, "NIP_KRAJ", safe(doc.getKrajCode() != null ? doc.getKrajCode() : "PL")));
        rz.appendChild(makeCdata(docXml, "NIP", safe(doc.getPodmiotNip())));
        rz.appendChild(makeCdata(docXml, "KRAJ", safe(doc.getKraj())));
        rz.appendChild(makeCdata(docXml, "WOJEWODZTWO", safe(doc.getWojewodztwo())));
        rz.appendChild(makeCdata(docXml, "POWIAT", safe(doc.getPowiat())));
        rz.appendChild(makeCdata(docXml, "GMINA", safe(doc.getGmina())));
        rz.appendChild(makeCdata(docXml, "ULICA", safe(doc.getUlica())));
        rz.appendChild(makeCdata(docXml, "NR_DOMU", safe(doc.getNrDomu())));
        rz.appendChild(makeCdata(docXml, "NR_LOKALU", safe(doc.getNrLokalu())));
        rz.appendChild(makeCdata(docXml, "MIASTO", safe(doc.getMiasto())));
        rz.appendChild(makeCdata(docXml, "KOD_POCZTOWY", safe(doc.getKodPocztowy())));
        rz.appendChild(makeCdata(docXml, "POCZTA", safe(doc.getPoczta())));
        rz.appendChild(makeCdata(docXml, "DODATKOWE", safe(doc.getDodatkowyOpis())));
        rz.appendChild(makeCdata(docXml, "PESEL", safe(doc.getPesel())));
        rz.appendChild(makeCdata(docXml, "ROLNIK", "Nie"));
        rz.appendChild(makeCdata(docXml, "TYP_PLATNIKA", "kontrahent"));
        //rz.appendChild(makeCdata(docXml, "PLATNIK", safe(doc.getPodmiotNip())));
        //rz.appendChild(makeCdata(docXml, "PLATNIK_ID", safe(doc.getPodmiotId())));
        rz.appendChild(makeCdata(docXml, "PLATNIK_NIP", safe(doc.getPodmiotNip())));
        rz.appendChild(makeCdata(docXml, "OPIS", safe(doc.getOpis())));
        rz.appendChild(makeCdata(docXml, "FORMA_PLATNOSCI", firstPaymentForm(doc)));
        rz.appendChild(makeCdata(docXml, "FORMA_PLATNOSCI_ID", safe(doc.getFormaPlatnosciId())));
        rz.appendChild(makeCdata(docXml, "DEKLARACJA_VAT7", safe(doc.getDeklaracjaVat7())));
        rz.appendChild(makeCdata(docXml, "DEKLARACJA_VATUE", safe(doc.getDeklaracjaVatUE() != null ? doc.getDeklaracjaVatUE() : "Nie")));

        // Kursy i waluta
        rz.appendChild(makeCdata(docXml, "WALUTA", safe(doc.getWaluta() != null ? doc.getWaluta() : "PLN")));
        rz.appendChild(makeCdata(docXml, "KURS_WALUTY", "NBP"));
        rz.appendChild(makeCdata(docXml, "NOTOWANIE_WALUTY_ILE", "1"));
        rz.appendChild(makeCdata(docXml, "NOTOWANIE_WALUTY_ZA_ILE", "1"));
        rz.appendChild(makeCdata(docXml, "DATA_KURSU", safe(doc.getDataWystawienia())));
        rz.appendChild(makeCdata(docXml, "KURS_DO_KSIEGOWANIA", "Nie"));
        rz.appendChild(makeCdata(docXml, "KURS_WALUTY_2", "NBP"));
        rz.appendChild(makeCdata(docXml, "NOTOWANIE_WALUTY_ILE_2", "1"));
        rz.appendChild(makeCdata(docXml, "NOTOWANIE_WALUTY_ZA_ILE_2", "1"));
        rz.appendChild(makeCdata(docXml, "DATA_KURSU_2", safe(doc.getDataWystawienia())));
        rz.appendChild(makeCdata(docXml, "PLATNOSC_VAT_W_PLN", "Nie"));
        rz.appendChild(makeCdata(docXml, "AKCYZA_NA_WEGIEL", safe(doc.getAkcyzaNaWegiel())));
        rz.appendChild(makeCdata(docXml, "AKCYZA_NA_WEGIEL_KOLUMNA_KPR", safe(doc.getAkcyzaKolumnaKpr())));
        rz.appendChild(makeCdata(docXml, "JPK_FA", "Nie"));
        rz.appendChild(makeCdata(docXml, "MPP", safe(doc.getMpp() != null ? doc.getMpp() : "Nie")));
        rz.appendChild(makeCdata(docXml, "NR_KSEF", safe(doc.getNrKsef())));
        rz.appendChild(makeCdata(docXml, "DODATKOWY_OPIS", safe(doc.getDodatkowyOpis())));

        // POZYCJE
        Element pozycje = docXml.createElementNS(NS, "POZYCJE");
        rz.appendChild(pozycje);

        if (doc.getPozycje() != null) {
            int lp = 1;
            for (DmsOutputPosition p : doc.getPozycje()) {
                Element poz = docXml.createElementNS(NS, "POZYCJA");
                pozycje.appendChild(poz);

                // LP
                poz.appendChild(makeCdata(docXml, "LP", String.valueOf(lp++)));

                // KATEGORIA_POS / KATEGORIA_ID_POS
                poz.appendChild(makeCdata(docXml, "KATEGORIA_POS", safe(p.getKategoria2())));
                poz.appendChild(makeCdata(docXml, "KATEGORIA_ID_POS", ""));
                poz.appendChild(makeCdata(docXml, "KATEGORIA_POS_2", safe(p.getKanalKategoria())));
                poz.appendChild(makeCdata(docXml, "KATEGORIA_ID_POS_2", safe(p.getKategoriaId())));
                // STAWKA_VAT normalized (e.g. "23")
                poz.appendChild(makeCdata(docXml, "STAWKA_VAT", normalizeVatRate(safe(p.getStawkaVat()))));
                poz.appendChild(makeCdata(docXml, "STATUS_VAT", safe(p.getStatusVat() != null ? p.getStatusVat() : "opodatkowana")));

                // NETTO / VAT / NETTO_SYS / VAT_SYS / NETTO_SYS2 / VAT_SYS2
                poz.appendChild(makeCdata(docXml, "NETTO", formatAmount(p.getNetto())));
                poz.appendChild(makeCdata(docXml, "VAT", formatAmount(p.getVat())));
                poz.appendChild(makeCdata(docXml, "NETTO_SYS", formatAmount(p.getNetto())));
                poz.appendChild(makeCdata(docXml, "VAT_SYS", formatAmount(p.getVat())));
                poz.appendChild(makeCdata(docXml, "NETTO_SYS2", formatAmount(p.getNetto())));
                poz.appendChild(makeCdata(docXml, "VAT_SYS2", formatAmount(p.getVat())));

                // RODZAJ_ZAKUPU, ODLICZENIA_VAT, KOLUMNA_KPR, KOLUMNA_RYCZALT
                poz.appendChild(makeCdata(docXml, "RODZAJ_ZAKUPU", safe(p.getRodzajKoszty())));
                poz.appendChild(makeCdata(docXml, "ODLICZENIA_VAT", safe(p.getOdliczeniaVat() != null ? p.getOdliczeniaVat() : "tak")));
                poz.appendChild(makeCdata(docXml, "KOLUMNA_KPR", safe(p.getKolumnaKpr() != null ? p.getKolumnaKpr() : "Nie księgować")));
                poz.appendChild(makeCdata(docXml, "KOLUMNA_RYCZALT", safe(p.getKolumnaRyczalt())));

                // OPIS_POZ / OPIS_POZ_2
                poz.appendChild(makeCdata(docXml, "OPIS_POZ", safe(p.getOpis())));
                poz.appendChild(makeCdata(docXml, "OPIS_POZ_2", safe(p.getOpis2())));
            }
        }

        // KWOTY_DODATKOWE - puste jeśli brak
        Element kwotyDod = docXml.createElementNS(NS, "KWOTY_DODATKOWE");
        rz.appendChild(kwotyDod);

        // PLATNOSCI
        if (doc.getPlatnosci() != null && !doc.getPlatnosci().isEmpty()) {
            Element platnosci = docXml.createElementNS(NS, "PLATNOSCI");
            rz.appendChild(platnosci);
        List<DmsPayment> platnosciList = doc.getPlatnosci();
        if (platnosciList != null && !platnosciList.isEmpty()) {
            for (DmsPayment p : platnosciList) {
                Element plat = docXml.createElementNS(NS, "PLATNOSC");
                platnosci.appendChild(plat);

                plat.appendChild(makeCdata(docXml, "ID_ZRODLA_PLAT", safe(p.getIdZrodla() != null ? p.getIdZrodla() : doc.getIdZrodla())));
                plat.appendChild(makeCdata(docXml, "TERMIN_PLAT", safe(p.getTermin())));
                plat.appendChild(makeCdata(docXml, "FORMA_PLATNOSCI_PLAT", safe(p.getForma())));
                plat.appendChild(makeCdata(docXml, "FORMA_PLATNOSCI_ID_PLAT", safe(p.getFormaId())));
                plat.appendChild(makeCdata(docXml, "KWOTA_PLAT", formatAmount(p.getKwota())));
                plat.appendChild(makeCdata(docXml, "WALUTA_PLAT", "PLN"));
                plat.appendChild(makeCdata(docXml, "KURS_WALUTY_PLAT", "NBP"));
                plat.appendChild(makeCdata(docXml, "NOTOWANIE_WALUTY_ILE_PLAT", "1"));
                plat.appendChild(makeCdata(docXml, "NOTOWANIE_WALUTY_ZA_ILE_PLAT", "1"));
                plat.appendChild(makeCdata(docXml, "KWOTA_PLN_PLAT", formatAmount(p.getKwota())));
                plat.appendChild(makeCdata(docXml, "KIERUNEK", safe(p.getKierunek())));
                plat.appendChild(makeCdata(docXml, "PODLEGA_ROZLICZENIU", safe(p.getPodlegaRozliczeniu() != null ? p.getPodlegaRozliczeniu() : "tak")));
                plat.appendChild(makeCdata(docXml, "KONTO", safe(p.getNrBank())));
                plat.appendChild(makeCdata(docXml, "NIE_NALICZAJ_ODSETEK", "Nie"));
                plat.appendChild(makeCdata(docXml, "PRZELEW_SEPA", "Nie"));
                plat.appendChild(makeCdata(docXml, "DATA_KURSU_PLAT", safe(p.getTermin())));
                plat.appendChild(makeCdata(docXml, "WALUTA_DOK", "PLN"));
                plat.appendChild(makeCdata(docXml, "PLATNOSC_TYP_PODMIOTU", "kontrahent"));
                plat.appendChild(makeCdata(docXml, "PLATNOSC_PODMIOT", safe(doc.getPodmiotAkronim())));
                //plat.appendChild(makeCdata(docXml, "PLATNOSC_PODMIOT_ID", safe(doc.getPodmiotId())));
                plat.appendChild(makeCdata(docXml, "PLATNOSC_PODMIOT_NIP", safe(doc.getPodmiotNip())));
                plat.appendChild(makeCdata(docXml, "PLAT_ELIXIR_O1", safe(p.getOpis())));
                plat.appendChild(makeCdata(docXml, "PLAT_ELIXIR_O2", ""));
                plat.appendChild(makeCdata(docXml, "PLAT_ELIXIR_O3", ""));
                plat.appendChild(makeCdata(docXml, "PLAT_ELIXIR_O4", ""));
                plat.appendChild(makeCdata(docXml, "PLAT_FA_Z_PA", "Nie"));
                plat.appendChild(makeCdata(docXml, "PLAT_VAN_FA_Z_PA", "Nie"));
                plat.appendChild(makeCdata(docXml, "PLAT_SPLIT_PAYMENT", safe(p.getSplitPayment() != null ? p.getSplitPayment() : "Nie")));
                plat.appendChild(makeCdata(docXml, "PLAT_SPLIT_KWOTA_VAT", formatAmount(p.getVatZ())));
                plat.appendChild(makeCdata(docXml, "PLAT_SPLIT_NIP", safe(doc.getPodmiotNip())));
                plat.appendChild(makeCdata(docXml, "PLAT_SPLIT_NR_DOKUMENTU", safe(doc.getInvoiceNumber())));
            }
        }
        }
        // KODY_JPK, ATRYBUTY, UWAGI
        Element kodyJpk = docXml.createElementNS(NS, "KODY_JPK");
        rz.appendChild(kodyJpk);

        Element atrybuty = docXml.createElementNS(NS, "ATRYBUTY");
        rz.appendChild(atrybuty);

        Element uwagi = docXml.createElementNS(NS, "UWAGI");
        rz.appendChild(uwagi);
        if (doc.getUwagi() != null) {
            for (String note : doc.getUwagi()) {
                uwagi.appendChild(makeCdata(docXml, "UWAGA", note));
            }
        }
    }

    private String firstPaymentForm(DmsDocumentOut doc) {
        if (doc.getPlatnosci() != null && !doc.getPlatnosci().isEmpty()) {
            return doc.getPlatnosci().get(0).getForma();
        }
        return "";
    }
	// -----------------------
    // Helpers
    // -----------------------
    private Element makeCdata(Document docXml, String name, String value) {
        Element el = docXml.createElementNS(NS, name);
        String v = value == null ? "" : value;
        if (v.isBlank()) v = "";
        // escape CDATA end
        String safeValue = v.replace("]]>", "]]]]><![CDATA[>");
        el.appendChild(docXml.createCDATASection(safeValue));
        return el;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String normalizeVatRate(String raw) {
        if (raw == null) return "";
        raw = raw.trim();
        if (raw.isEmpty()) return "";
        if (raw.contains(".")) raw = raw.substring(0, raw.indexOf('.'));
        return raw;
    }

    private static String formatAmount(String raw) {
        double v = 0.0;
        try {
            if (raw != null && !raw.isBlank()) v = Double.parseDouble(raw);
        } catch (Exception e) { v = 0.0; }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("0.##", symbols);
        df.setMinimumFractionDigits(0);
        df.setMaximumFractionDigits(2);
        // ensure two decimals when needed (Optima examples show 12.65)
        return String.format(Locale.US, "%.2f", v);
    }
}
