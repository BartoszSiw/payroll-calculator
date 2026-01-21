package pl.edashi.dms.model;

public class DmsPayment {
    private String termin = "";
    private String forma = "";
    public String kwota = "";
    private String opis = "";
    private String nrBank = "";
    private String vat = "";
    private String vatZ = "";
    private String operatorName = "";
    private String description = "";
    private String idPlatn = "";
	private String kierunek = "";
	private String terminPlatnosci = "";
	private boolean advance; // true = zaliczka, false = normalna płatność
    public DmsPayment() { }

    // --- Gettery ---
    public String getTermin() { return termin; }
    public String getForma() { return forma; }
    public String getKwota() { return kwota; }
    public String getOpis() { return opis; }
    public String getNrBank() { return nrBank; }
    public String getVat() { return vat; }
    public String getVatZ() { return vatZ; }
    public String operatorName() { return operatorName;}
    public String getDecsription() { return description; }
    public String getIdPlatn() { return idPlatn; }
    public String getKierunek() { return kierunek; }
	public String getTerminPlatnosci() {return terminPlatnosci;}
	public boolean isAdvance() { return advance; }
   
    // --- Settery (null-safe) ---
    public void setTermin(String termin) { this.termin = termin != null ? termin : ""; }
    public void setForma(String forma) { this.forma = forma != null ? forma : ""; }
    public void setKwota(String kwota) { this.kwota = kwota != null ? kwota : ""; }
    public void setOpis(String opis) { this.opis = opis != null ? opis : ""; }
    public void setNrBank(String nrBank) { this.nrBank = nrBank != null ? nrBank : ""; }
    public void setVat(String vat) { this.vat = vat != null ? vat : ""; }
    public void setVatZ(String vatZ) { this.vatZ = vatZ != null ? vatZ : ""; }
	public void setOperatorName(String operatorName) {this.operatorName = operatorName != null ? operatorName : "";	}
	public void setDecsription(String description) { this.description = description != null ? description : ""; }
	public void setIdPlatn(String idPlatn) { this.idPlatn = idPlatn != null ? idPlatn : ""; }
	public void setKierunek(String kierunek) { this.kierunek = kierunek != null ? kierunek : ""; }
	public void setAdvance(boolean advance) { this.advance = advance; }
	public void setTerminPlatnosci(String  terminPlatnosci) { this.terminPlatnosci = terminPlatnosci; }

    @Override
    public String toString() {
        return "DmsPayment{" +
                "termin='" + termin + '\'' +
                ", forma='" + forma + '\'' +
                ", kwota='" + kwota + '\'' +
                ", opis='" + opis + '\'' +
                ", nrBank='" + nrBank + '\'' +
                ", vat='" + vat + '\'' +
                ", vatZ='" + vatZ + '\'' +
                ", description='" + description + '\'' +
                '}';
    }

	public String getIdZrodla() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getFormaId() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getWaluta() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getPodlegaRozliczeniu() {
		// TODO Auto-generated method stub
		return null;
	}

	public String getSplitPayment() {
		// TODO Auto-generated method stub
		return null;
	}



	}