package pl.edashi.dms.model;

import java.util.ArrayList;
import java.util.List;

import pl.edashi.dms.parser.DmsParserDS.DmsKwotaDodatkowa;

public class DmsOutputPosition {

    private String kategoria2 = "";
    private String kategoria = "";
    private String stawkaVat = "";
    private String netto = "";
    private String kwota = "";
    private String kwotaRk = "";
    private String nettoRob = "";
    private String nettoZakup = "";
    private String vat = "";
    private String vatZ = "";
    private String rodzajSprzedazy = "";
    private String rodzajKoszty = "";
    private String vin = "";
    private String kanal = "";
    private String kanalKategoria = "";
    private String kierunek = "";
    private String statusVat= "";
    private String opis="";
    private String opis1="";
    private String nrKonta="";
    private String kontoMa="";
    private String kontoWn="";
    private String nettoDlRob;
    private String nettoKoMat;
    private String nettoGwMat;
    private String lp;
    private String brutto;
    private String nrRKB="";
    private String numer = "";
    private String reportDate = "";
    private String reportNumber = "";
    private String reportNumberPos = "";
    private String dowodNumber = "";
    private boolean advance = false;
	private List<DmsKwotaDodatkowa> kwotyDodatkowe = new ArrayList<>();

    public DmsOutputPosition() { }

    // --- Gettery ---
    public String getKategoria2() { return kategoria2; }
    public String getKategoria() { return kategoria; }
    public String getStawkaVat() { return stawkaVat; }
	public String getStatusVat() {return statusVat;	}
    public String getNetto() { return netto; }
    public String getKwota() { return kwota; }
    public String getKwotaRk() { return kwotaRk; }
    public String getNettoRob() { return nettoRob; }
    public String getNettoZakup() { return nettoZakup; }
    public String getVat() { return vat; }
    public String getVatZ() { return vatZ; }
    public String getRodzajSprzedazy() { return rodzajSprzedazy; }
    public String getRodzajKoszty() { return rodzajKoszty; }
    public String getVin() { return vin; }
    public String getKanal() { return kanal; }
    public String getKanalKategoria() { return kanalKategoria; }
    public String getKierunek() { return kierunek; }
    public String getOpis() { return opis; }
    public String getOpis1() { return opis1; }
    public String getNrKonta() { return nrKonta; }
	public String getKontoMa() { return kontoMa;}
	public String getKontoWn() { return kontoWn;}
	public String getNettoDlRob() { return nettoDlRob; }
	public String getNettoKoMat() { return nettoKoMat; }
	public String getNettoGwMat() { return nettoGwMat; }
	public String getLp() { return lp; }
	public String getBrutto() { return brutto; }
	public String getNrRKB() { return nrRKB; }
    public String getNumer() { return numer; }
    public String getReportDate() {return reportDate; }
    public String getReportNumber() { return reportNumber; }
    public String getReportNumberPos() { return reportNumberPos; }
    public String getDowodNumber () { return dowodNumber; }
   	public boolean isAdvance() { return advance; }
	public List<DmsKwotaDodatkowa> getKwotyDodatkowe() {
	    return kwotyDodatkowe;
	}


    // --- Settery (null-safe) ---
    public void setKategoria2(String kategoria2) { this.kategoria2 = safe(kategoria2); }
    public void setKategoria(String kategoria) { this.kategoria = safe(kategoria); }
    public void setStawkaVat(String stawkaVat) { this.stawkaVat = safe(stawkaVat); }
    public void setStatusVat(String statusVat) { this.statusVat = safe(statusVat); }
    public void setNetto(String netto) { this.netto = safe(netto); }
    public void setKwota(String kwota) { this.kwota = safe(kwota); }
    public void setKwotaRk(String kwotaRk) { this.kwotaRk = safe(kwotaRk); }
    public void setNettoRob(String nettoRob) { this.nettoRob = safe(nettoRob); }
    public void setNettoZakup(String nettoZakup) { this.nettoZakup = safe(nettoZakup); }
    public void setVat(String vat) { this.vat = safe(vat); }
    public void setVatZ(String vatZ) { this.vatZ = safe(vatZ); }
    public void setRodzajSprzedazy(String rodzajSprzedazy) { this.rodzajSprzedazy = safe(rodzajSprzedazy); }
    public void setRodzajKoszty(String rodzajKoszty) { this.rodzajKoszty = safe(rodzajKoszty); }
    public void setVin(String vin) { this.vin = safe(vin); }
    public void setKanal(String kanal) { this.kanal = safe(kanal); }
    public void setKanalKategoria(String kanalKategoria) { this.kanalKategoria = safe(kanalKategoria); }
    public void setKierunek(String kierunek) { this.kierunek = kierunek != null ? kierunek : ""; }
    public void setOpis(String opis) { this.opis = safe(opis); }
    public void setOpis1(String opis1) { this.opis1 = safe(opis1); }
    public void setNrKonta(String nrKonta) { this.nrKonta = safe(nrKonta); }
	public void setKontoMa(String kontoMa) {this.kontoMa = safe(kontoMa);}
	public void setKontoWn(String kontoWn) {this.kontoWn = safe(kontoWn);}
	public void setNettoDlRob(String nettoDlRob) { this.nettoDlRob = nettoDlRob != null ? nettoDlRob : "";}
	public void setNettoKoMat(String nettoKoMat) { this.nettoKoMat = nettoKoMat != null ? nettoKoMat : "";}
	public void setNettoGwMat(String nettoGwMat) { this.nettoGwMat = nettoGwMat != null ? nettoGwMat : "";}
	public void setKwotyDodatkowe(List<DmsKwotaDodatkowa> kwotyDodatkowe) {this.kwotyDodatkowe = kwotyDodatkowe;}
	public void setBrutto(String brutto) { this.brutto = safe(brutto); }
	public void setLp(String lp) { this.lp = safe(lp); }
	public void setNrRKB(String nrRKB) { this.nrRKB = safe(nrRKB); }
    public void setNumer(String numer) { this.numer = safe(numer); }
    public void setReportDate(String reportDate) {this.reportDate = safe(reportDate) ;}
    public void setReportNumber(String reportNumber) { this.reportNumber = safe(reportNumber); }
    public void setReportNumberPos(String reportNumberPos) { this.reportNumberPos = safe(reportNumberPos); }
	public void setAdvance(boolean advance) { this.advance = advance; }
	public void setDowodNumber(String dowodNumber) {this.dowodNumber = safe(dowodNumber); }
    // --- Helper ---
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    @Override
    public String toString() {
        return "DmsOutputPosition{" +
                "kategoria='" + kategoria2 + '\'' +
                ", stawkaVat='" + stawkaVat + '\'' +
                ", netto='" + netto + '\'' +
                ", vat='" + vat + '\'' +
                ", vatZ='" + vatZ + '\'' +
                ", rodzajSprzedazy='" + rodzajSprzedazy + '\'' +
                ", vin='" + vin + '\'' +
                ", kanal='" + kanal + '\'' +
                ", kanalKategoria='" + kanalKategoria + '\'' +
                '}';
    }

	public String getKategoriaId() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getOdliczeniaVat() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getKolumnaKpr() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getKolumnaRyczalt() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getOpis2() {
		// TODO Auto-generated method stub
		return null;
	}
}

