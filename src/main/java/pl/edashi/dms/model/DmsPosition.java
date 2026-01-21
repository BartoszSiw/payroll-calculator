package pl.edashi.dms.model;

public class DmsPosition {
	public String type = ""; 
    public String kategoria = "";
    public String stawkaVat = "";
    public String statusVat = "";
    public String netto = "";
    public String vat = "";
    public String rodzajSprzedazy = "";
	public String vin = "";
	public String kanal = "";
	public String kanalKategoria = "";
    private String description = "";
    private String opis = "";
    private String klasyfikacja = "";
    private String numer = "";
	public String kodVat = "";
	public String typDZ = "";
	public String jm = "";
	public String jmSymb = "";
	public String opis1 = "";
	public String cenaNetto = "";
	public String brutto = "";
	public String kierunek = "";
    public DmsPosition() { }

    // --- Gettery ---
		
	public String getType() {
		return type;
	}
    public String getKategoria() { return kategoria; }
    public String getStawkaVat() { return stawkaVat; }
    public String getStatusVat() { return statusVat; }
    public String getNetto() { return netto; }
    public String getVat() { return vat; }
    public String getRodzajSprzedazy() { return rodzajSprzedazy; }
    public String getVin() { return vin; }
    public String getKanal() { return kanal; }
    public String getKanalKategoria() { return kanalKategoria; }
    public String getDecsription() { return description; }
    public String getOpis() { return opis; }
	public String getKlasyfikacja() { return klasyfikacja;}
	public String getNumer() { return numer; }
	public String getKodVat() { return kodVat; }
	public String getTypDZ() { return typDZ; }
	public String getJm() { return jm; }
	public String getJmSymb() { return jmSymb; }
	public String getOpis1() { return opis1; };
	public String getKierunek() { return kierunek; }
    // --- Settery (null-safe) ---
    public void setType(String type) { this.type = type != null ? type : ""; }
    public void setKategoria(String kategoria) { this.kategoria = kategoria != null ? kategoria : ""; }
    public void setStawkaVat(String stawkaVat) { this.stawkaVat = stawkaVat != null ? stawkaVat : ""; }
    public void setStatusVat(String statusVat) { this.stawkaVat = statusVat != null ? statusVat : ""; }
    public void setNetto(String netto) { this.netto = netto != null ? netto : ""; }
    public void setVat(String vat) { this.vat = vat != null ? vat : ""; }
    public void setRodzajSprzedazy(String rodzajSprzedazy) { this.rodzajSprzedazy = rodzajSprzedazy != null ? rodzajSprzedazy : ""; }
    public void setVin(String vin) { this.vin = vin != null ? vin : ""; }
    public void setKanal(String kanal) { this.kanal = kanal != null ? kanal : ""; }
    public void setKanalKategoria(String kanalKategoria) { this.kanalKategoria = kanalKategoria != null ? kanalKategoria : ""; }
	public void setDecsription(String description) { this.description = description != null ? description : ""; }
	public void setOpis(String opis) { this.opis = opis != null ? opis : ""; }
	public void setKlasyfikacja(String klasyfikacja) { this.klasyfikacja = klasyfikacja != null ? klasyfikacja : ""; }
	public void setNumer(String numer) { this.numer = numer != null ? numer : ""; }
	public void setKodVat(String kodVat) {this.kodVat = kodVat != null ? kodVat: ""; }
	public void setTypDZ(String typDZ) { this.typDZ = typDZ != null ? typDZ:""; }
	public void setJm(String jm) { this.jm = jm != null ? jm: "";}
	public void setJmSymb(String jmSymb) { this.jmSymb = jmSymb != null ? jmSymb:""; }
	public void setOpis1(String opis1) { this.opis1 = opis1 != null ? opis1: "";}
	public void setKierunek(String kierunek) { this.kierunek = kierunek != null ? kierunek : ""; }
    @Override
    public String toString() {
        return "DmsPosition{" +
                "type='" + type + '\'' +
                ", kategoria='" + kategoria + '\'' +
                ", stawkaVat='" + stawkaVat + '\'' +
                ", netto='" + netto + '\'' +
                ", vat='" + vat + '\'' +
                ", rodzajSprzedazy='" + rodzajSprzedazy + '\'' +
                ", vin='" + vin + '\'' +
                ", kanal='" + kanal + '\'' +
                ", kanalKategoria='" + kanalKategoria + '\'' +
                ", description='" + description + '\'' +
                ", opis='" + opis + '\'' +
                ", kierunek='" + kierunek + '\'' +
                '}';
    }

}
