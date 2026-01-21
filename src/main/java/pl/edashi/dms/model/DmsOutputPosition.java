package pl.edashi.dms.model;

public class DmsOutputPosition {

    private String kategoria = "";
    private String stawkaVat = "";
    private String netto = "";
    private String vat = "";
    private String vatZ = "";
    private String rodzajSprzedazy = "";
    private String vin = "";
    private String kanal = "";
    private String kanalKategoria = "";
    private String kierunek = "";
    private String statusVat= "";

    public DmsOutputPosition() { }

    // --- Gettery ---
    public String getKategoria() { return kategoria; }
    public String getStawkaVat() { return stawkaVat; }
	public String getStatusVat() {return statusVat;	}
    public String getNetto() { return netto; }
    public String getVat() { return vat; }
    public String getVatZ() { return vatZ; }
    public String getRodzajSprzedazy() { return rodzajSprzedazy; }
    public String getVin() { return vin; }
    public String getKanal() { return kanal; }
    public String getKanalKategoria() { return kanalKategoria; }
    public String getKierunek() { return kierunek; }

    // --- Settery (null-safe) ---
    public void setKategoria(String kategoria) { this.kategoria = safe(kategoria); }
    public void setStawkaVat(String stawkaVat) { this.stawkaVat = safe(stawkaVat); }
    public void setStatusVat(String statusVat) { this.statusVat = safe(statusVat); }
    public void setNetto(String netto) { this.netto = safe(netto); }
    public void setVat(String vat) { this.vat = safe(vat); }
    public void setVatZ(String vatZ) { this.vatZ = safe(vatZ); }
    public void setRodzajSprzedazy(String rodzajSprzedazy) { this.rodzajSprzedazy = safe(rodzajSprzedazy); }
    public void setVin(String vin) { this.vin = safe(vin); }
    public void setKanal(String kanal) { this.kanal = safe(kanal); }
    public void setKanalKategoria(String kanalKategoria) { this.kanalKategoria = safe(kanalKategoria); }
    public void setKierunek(String kierunek) { this.kierunek = kierunek != null ? kierunek : ""; }
    // --- Helper ---
    private static String safe(String s) {
        return s == null ? "" : s;
    }

    @Override
    public String toString() {
        return "DmsOutputPosition{" +
                "kategoria='" + kategoria + '\'' +
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

	public String getOpis1() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getOpis2() {
		// TODO Auto-generated method stub
		return null;
	}
}

