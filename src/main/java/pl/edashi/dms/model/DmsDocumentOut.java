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
    private String dataWplywu = "";
    private String dataOperacji = "";
    private String dataObowiazkuPodatkowego = "";
    private String dataPrawaOdliczenia = "";
    private String termin = "";
    //private String terminPlatnosci = "";
	private String numer = "";
    private String korekta = "";
    private String korektaNumer = "";
    // PODMIOT
    private String podmiotAkronim = "";
    private String podmiotId = "";
    private String podmiotNip = "";
    private String nazwa1 = "";
    private String nazwa2 = "";
    private String nazwa3 = "";
    private String kraj = "";
    private String krajCode = "";
    private String wojewodztwo = "";
    private String powiat = "";
    private String gmina = "";
    private String miasto = "";
    private String poczta = "";
    private String kodPocztowy = "";
    private String ulica = "";
    private String nrDomu = "";
    private String nrLokalu = "";
    private String czynny = "";
    //FAKTURA
    private String documentType = "";
    private String documentWewne = "";
    private String rozszerzone = "";
    private String invoiceNumber = "";
    private String invoiceShortNumber = "";
    private String numerKsef = "";
    private String uwzgProp = "";
    private String dokumentFiskalny = "";
    private String dokumentDetaliczny = "";
    // POZYCJE - pozycje są w liście pozycje (DmsOutputPosition)
 
    // PŁATNOŚCI
    private List<DmsPayment> platnosci = new ArrayList<>();
    //private String advanceNet;
    //private String advanceVat;

    // VAT
    private String vatRate = "";
    private String vatBase = "";
    private String vatAmount = "";

    // OPISY
    private String dodatkowyOpis = "";
    private List<String> uwagi = new ArrayList<>();
    // Dodatkowe pola księgowe / importowe
    private String formaPlatnosciId = "";
    private String waluta = "";
    private String akcyzaNaWegiel = "0";
    private String akcyzaKolumnaKpr = "";
    private String mpp = "Nie";
    private String nrKsef = "";
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
    
    public String getUwzgProp() { return uwzgProp; }
    public void setUwzgProp(String uwzgProp) { this.uwzgProp = uwzgProp; }
    
    public String getModul() { return modul; }
    public void setModul(String modul) { this.modul = safe(modul); }

    public String getTyp() { return typ; }
    public void setTyp(String typ) { this.typ = safe(typ); }

    public String getRejestr() { return rejestr; }
    public void setRejestr(String rejestr) { this.rejestr = safe(rejestr); }

    public String getDataWystawienia() { return dataWystawienia; }
    public void setDataWystawienia(String dataWystawienia) { this.dataWystawienia = safe(dataWystawienia); }
    
    public String getDataOperacji() { return dataOperacji; }
    public void setDataOperacji(String dataOperacji) { this.dataOperacji = safe(dataOperacji); }

    public String getDataSprzedazy() { return dataSprzedazy; }
    public void setDataSprzedazy(String dataSprzedazy) { this.dataSprzedazy = safe(dataSprzedazy); }
    
    public String getDataWplywu() { return dataWplywu; }
    public void setDataWplywu(String dataWplywu) { this.dataWplywu = safe(dataWplywu); }

    public String getDataObowiazkuPodatkowego() { return dataObowiazkuPodatkowego; }
    public void setDataObowiazkuPodatkowego(String dataObowiazkuPodatkowego) { this.dataObowiazkuPodatkowego = safe(dataObowiazkuPodatkowego); }

    public String getDataPrawaOdliczenia() { return dataPrawaOdliczenia; }
    public void setDataPrawaOdliczenia(String dataPrawaOdliczenia) { this.dataPrawaOdliczenia = safe(dataPrawaOdliczenia); }
    
    public String getTermin() { return termin; }
    public void setTermin(String termin) { this.termin = safe(termin); }

    public String getNumer() { return numer; }
    public void setNumer(String numer) { this.numer = safe(numer); }

    public String getKorekta() { return korekta; }
    public void setKorekta(String korekta) { this.korekta = korekta; }

    public String getKorektaNumer() { return korektaNumer; }
    public void setKorektaNumer(String korektaNumer) { this.korektaNumer = safe(korektaNumer); }
    
    //public String getTerminPlatnosci() {return terminPlatnosci;}
	//public void setTerminPlatnosci(String terminPlatnosci) {this.terminPlatnosci = terminPlatnosci;}

    // --- PODMIOT ---
    public String getPodmiotAkronim() { return podmiotAkronim; }
    public void setPodmiotAkronim(String podmiotAkronim) { this.podmiotAkronim = safe(podmiotAkronim); }
    
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

    public String getKrajCode() { return krajCode; }
    public void setKrajCode(String krajCode) { this.krajCode = safe(krajCode); }
    
    public String getWojewodztwo() { return wojewodztwo; }
    public void setWojewodztwo(String wojewodztwo) { this.wojewodztwo = safe(wojewodztwo); }

    public String getGmina() { return gmina; }
    public void setGmina(String gmina) { this.gmina = safe(gmina); }
    
    public String getPowiat() { return powiat; }
    public void setPowiat(String powiat) { this.powiat = safe(powiat); }

    public String getMiasto() { return miasto; }
    public void setMiasto(String miasto) { this.miasto = safe(miasto); }

    public String getPoczta() { return poczta; }
    public void setPoczta(String poczta) { this.poczta = safe(poczta); }
    
    public String getKodPocztowy() { return kodPocztowy; }
    public void setKodPocztowy(String kodPocztowy) { this.kodPocztowy = safe(kodPocztowy); }

    public String getUlica() { return ulica; }
    public void setUlica(String ulica) { this.ulica = safe(ulica); }

    public String getNrDomu() { return nrDomu; }
    public void setNrDomu(String nrDomu) { this.nrDomu = safe(nrDomu); }
   
    public String getNrLokalu() { return nrLokalu; }
    public void setNrLokalu(String nrLokalu) { this.nrLokalu = safe(nrLokalu); }
	public String getCzynny() { return czynny;}
	public void setCzynny(String czynny) { this.czynny = safe(czynny); }
    // --- FAKTURA / META DODATKOWE ---
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = safe(documentType); }
    
    public String getDocumentWewne() { return documentWewne; }
    public void setDocumentWewne(String documentWewne) { this.documentWewne = safe(documentWewne); }

    public String getRozszerzone() { return rozszerzone; }
    public void setRozszerzone(String rozszerzone) { this.rozszerzone = safe(rozszerzone); }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = safe(invoiceNumber); }

    public String getInvoiceShortNumber() { return invoiceShortNumber; }
    public void setInvoiceShortNumber(String invoiceShortNumber) { this.invoiceShortNumber = safe(invoiceShortNumber); } 
    
    public String getNumerKsef() { return numerKsef; }
    public void setNumerKsef(String numerKsef) { this.numerKsef = safe(numerKsef); }
    
    public String getDokumentFiskalny() { return dokumentFiskalny; }
    public void setDokumentFiskalny(String dokumentFiskalny) { this.dokumentFiskalny = safe(dokumentFiskalny); }

    public String getDokumentDetaliczny() { return dokumentDetaliczny; }
    public void setDokumentDetaliczny(String dokumentDetaliczny) { this.dokumentDetaliczny = safe(dokumentDetaliczny); }
    // --- VAT ---
    public String getVatRate() { return vatRate; }
    public void setVatRate(String vatRate) { this.vatRate = safe(vatRate); }

    public String getVatBase() { return vatBase; }
    public void setVatBase(String vatBase) { this.vatBase = safe(vatBase); }

    public String getVatAmount() { return vatAmount; }
    public void setVatAmount(String vatAmount) { this.vatAmount = safe(vatAmount); }
    // --- OPISY / DODATKOWE ---
    public String getDodatkowyOpis() { return dodatkowyOpis; }
    public void setDodatkowyOpis(String dodatkowyOpis) { this.dodatkowyOpis = safe(dodatkowyOpis); }

    public String getOpis() { return dodatkowyOpis; } // alias
    public void setOpis(String opis) { this.dodatkowyOpis = safe(opis); }

    // --- Księgowe / importowe pola pomocnicze ---
    public String getFormaPlatnosciId() { return formaPlatnosciId; }
    public void setFormaPlatnosciId(String formaPlatnosciId) { this.formaPlatnosciId = safe(formaPlatnosciId); }

    public String getWaluta() { return waluta; }
    public void setWaluta(String waluta) { this.waluta = safe(waluta); }

    public String getAkcyzaNaWegiel() { return akcyzaNaWegiel; }
    public void setAkcyzaNaWegiel(String akcyzaNaWegiel) { this.akcyzaNaWegiel = safe(akcyzaNaWegiel); }

    public String getAkcyzaKolumnaKpr() { return akcyzaKolumnaKpr; }
    public void setAkcyzaKolumnaKpr(String akcyzaKolumnaKpr) { this.akcyzaKolumnaKpr = safe(akcyzaKolumnaKpr); }

    public String getMpp() { return mpp; }
    public void setMpp(String mpp) { this.mpp = safe(mpp); }

    public String getNrKsef() { return nrKsef; }
    public void setNrKsef(String nrKsef) { this.nrKsef = safe(nrKsef); }

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
	public String getPesel() {
		// TODO Auto-generated method stub
		return null;
	}
	public String getDeklaracjaVat7() {
		// TODO Auto-generated method stub
		return null;
	}
	public String getDeklaracjaVatUE() {
		// TODO Auto-generated method stub
		return null;
	}

}

