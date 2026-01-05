package pl.edashi.dms.model;
import java.util.ArrayList;
import java.util.List;
import pl.edashi.common.logging.AppLogger;
public class DmsDocumentOut {
	private List<DmsOutputPosition> pozycje = new ArrayList<>();
	// META
    private String idZrodla = "";
    private String modul = "";
    private String typ = "";
    private String rejestr = "";
    private String dataWystawienia = "";
    private String dataSprzedazy = "";
    private String termin = "";
    private String numer = "";
    private boolean korekta = false;
    // PODMIOT
    private String podmiotId = "";
    private String podmiotNip = "";
    private String nazwa1 = "";
    private String nazwa2 = "";
    private String nazwa3 = "";
    private String kraj = "";
    private String wojewodztwo = "";
    private String powiat = "";
    private String miasto = "";
    private String kodPocztowy = "";
    private String ulica = "";
    private String nrDomu = "";
    //FAKTURA
    private String documentType = "";
    private String rozszerzone = "";
    private String invoiceNumber = "";
    private String invoiceShortNumber = "";
    // POZYCJE
 
    // PŁATNOŚCI
    private List<DmsPayment> platnosci = new ArrayList<>();
    // VAT
    private String vatRate = "";
    private String vatBase = "";
    private String vatAmount = "";

    // OPISY
    private String dodatkowyOpis = "";
    private List<String> uwagi = new ArrayList<>();

    public DmsDocumentOut() { }
    // --- Gettery i settery dla list ---
    public List<DmsOutputPosition> getPozycje() {
        if (pozycje == null) pozycje = new ArrayList<>();
        return pozycje;
    }
    public void setPozycje(List<DmsOutputPosition> pozycje) {
        this.pozycje = pozycje != null ? pozycje : new ArrayList<>();
    }

    public List<DmsPayment> getPlatnosci() {
        if (platnosci == null) platnosci = new ArrayList<>();
        return platnosci;
    }
    public void setPlatnosci(List<DmsPayment> platnosci) {
        this.platnosci = platnosci != null ? platnosci : new ArrayList<>();
    }

    public List<String> getUwagi() {
        if (uwagi == null) uwagi = new ArrayList<>();
        return uwagi;
    }
    public void setUwagi(List<String> uwagi) {
        this.uwagi = uwagi != null ? uwagi : new ArrayList<>();
    }

    // --- Gettery i settery dla pól prostych ---
    public String getIdZrodla() { return idZrodla; }
    public void setIdZrodla(String idZrodla) { this.idZrodla = safe(idZrodla); }

    public String getModul() { return modul; }
    public void setModul(String modul) { this.modul = safe(modul); }

    public String getTyp() { return typ; }
    public void setTyp(String typ) { this.typ = safe(typ); }

    public String getRejestr() { return rejestr; }
    public void setRejestr(String rejestr) { this.rejestr = safe(rejestr); }

    public String getDataWystawienia() { return dataWystawienia; }
    public void setDataWystawienia(String dataWystawienia) { this.dataWystawienia = safe(dataWystawienia); }

    public String getDataSprzedazy() { return dataSprzedazy; }
    public void setDataSprzedazy(String dataSprzedazy) { this.dataSprzedazy = safe(dataSprzedazy); }

    public String getTermin() { return termin; }
    public void setTermin(String termin) { this.termin = safe(termin); }

    public String getNumer() { return numer; }
    public void setNumer(String numer) { this.numer = safe(numer); }

    public boolean isKorekta() { return korekta; }
    public void setKorekta(boolean korekta) { this.korekta = korekta; }

    public String getPodmiotId() { return podmiotId; }
    public void setPodmiotId(String podmiotId) { this.podmiotId = safe(podmiotId); }

    public String getPodmiotNip() { return podmiotNip; }
    public void setPodmiotNip(String podmiotNip) { this.podmiotNip = safe(podmiotNip); }

    public String getNazwa1() { return nazwa1; }
    public void setNazwa1(String nazwa1) { this.nazwa1 = safe(nazwa1); }

    public String getNazwa2() { return nazwa2; }
    public void setNazwa2(String nazwa2) { this.nazwa2 = safe(nazwa2); }

    public String getNazwa3() { return nazwa3; }
    public void setNazwa3(String nazwa3) { this.nazwa3 = safe(nazwa3); }

    public String getKraj() { return kraj; }
    public void setKraj(String kraj) { this.kraj = safe(kraj); }

    public String getWojewodztwo() { return wojewodztwo; }
    public void setWojewodztwo(String wojewodztwo) { this.wojewodztwo = safe(wojewodztwo); }

    public String getPowiat() { return powiat; }
    public void setPowiat(String powiat) { this.powiat = safe(powiat); }

    public String getMiasto() { return miasto; }
    public void setMiasto(String miasto) { this.miasto = safe(miasto); }

    public String getKodPocztowy() { return kodPocztowy; }
    public void setKodPocztowy(String kodPocztowy) { this.kodPocztowy = safe(kodPocztowy); }

    public String getUlica() { return ulica; }
    public void setUlica(String ulica) { this.ulica = safe(ulica); }

    public String getNrDomu() { return nrDomu; }
    public void setNrDomu(String nrDomu) { this.nrDomu = safe(nrDomu); }

    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = safe(documentType); }

    public String getRozszerzone() { return rozszerzone; }
    public void setRozszerzone(String rozszerzone) { this.rozszerzone = safe(rozszerzone); }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = safe(invoiceNumber); }

    public String getInvoiceShortNumber() { return invoiceShortNumber; }
    public void setInvoiceShortNumber(String invoiceShortNumber) { this.invoiceShortNumber = safe(invoiceShortNumber); } 
    
    public String getVatRate() { return vatRate; }
    public void setVatRate(String vatRate) { this.vatRate = safe(vatRate); }

    public String getVatBase() { return vatBase; }
    public void setVatBase(String vatBase) { this.vatBase = safe(vatBase); }

    public String getVatAmount() { return vatAmount; }
    public void setVatAmount(String vatAmount) { this.vatAmount = safe(vatAmount); }

    public String getDodatkowyOpis() { return dodatkowyOpis; }
    public void setDodatkowyOpis(String dodatkowyOpis) { this.dodatkowyOpis = safe(dodatkowyOpis); }

    // --- Helper ---
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    @Override
    public String toString() {
        return "DmsDocumentOut{" +
                "idZrodla='" + idZrodla + '\'' +
                ", typ='" + typ + '\'' +
                ", invoiceNumber='" + invoiceNumber + '\'' +
                ", pozycje=" + (pozycje != null ? pozycje.size() : 0) +
                ", platnosci=" + (platnosci != null ? platnosci.size() : 0) +
                '}';
    }
}

