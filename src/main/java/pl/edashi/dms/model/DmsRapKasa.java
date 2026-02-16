package pl.edashi.dms.model;
import java.util.ArrayList;
import java.util.List;
//import pl.edashi.dms.parser.DmsParserDS.DmsKwotaDodatkowa;
public class DmsRapKasa {

	//private List<DmsKwotaDodatkowa> kwotyDodatkowe = new ArrayList<>();
	public String type = ""; 
    public String kategoria = "";
    public String netto = "";
	public String kanal = "";
	public String description = "";
	public String opis = "";
	public String numer = "";
	public String nrRKB = "";
	public String kodVat = "";
	public String typKO = "";
	public String opis1 = "";
	public String kierunek = "";
	public String lp;
	public String nrKonta;
	public String kwota = "";
	public String reportNumber = "";
    public DmsRapKasa() { }

    // --- Gettery ---
		
	public String getType() {
		return type;
	}
    public String getKategoria() { return kategoria; }
    public String getNetto() { return netto; }
    public String getKanal() { return kanal; }
    public String getDecsription() { return description; }
    public String getOpis() { return opis; }
	public String getNumer() { return numer; }
	public String getReportNumber() { return reportNumber; }
	public String getNrRKB() { return nrRKB; }
	public String getKodVat() { return kodVat; }
	public String getOpis1() { return opis1; };
	public String getNrKonta() { return nrKonta; };
	public String getKierunek() { return kierunek; }
	public String getLp() { return lp; }
	public String getKwota() { return kwota; }
	//public List<DmsKwotaDodatkowa> getKwotyDodatkowe() {return kwotyDodatkowe;}
    // --- Settery (null-safe) ---
    public void setType(String type) { this.type = type != null ? type : ""; }
    public void setKategoria(String kategoria) { this.kategoria = kategoria != null ? kategoria : ""; }
    public void setNetto(String netto) { this.netto = netto != null ? netto : ""; }
    public void setKanal(String kanal) { this.kanal = kanal != null ? kanal : ""; }
    public void setDecsription(String description) { this.description = description != null ? description : ""; }
	public void setOpis(String opis) { this.opis = opis != null ? opis : ""; }
	public void setNumer(String numer) { this.numer = numer != null ? numer : ""; }
	public void setReportNumber(String reportNumber) { this.reportNumber = reportNumber != null ? reportNumber : ""; }
	public void setNrRKB(String nrRKB) { this.nrRKB = nrRKB != null ? nrRKB : ""; }
	public void setKodVat(String kodVat) {this.kodVat = kodVat != null ? kodVat: ""; }
	public void setOpis1(String opis1) { this.opis1 = opis1 != null ? opis1: "";}
	public void setNrKonta(String nrKonta) { this.nrKonta = nrKonta != null ? nrKonta: "";}
	public void setKierunek(String kierunek) { this.kierunek = kierunek != null ? kierunek : ""; }
	public void setLp(String lp) { this.lp = lp != null ? lp : ""; }
	public void setKwota(String kwota) { this.kwota = kwota != null ? kwota : ""; }
	//public void setKwotyDodatkowe(List<DmsKwotaDodatkowa> list) {this.kwotyDodatkowe = list;}
	
    @Override
    public String toString() {
        return "DmsPosition{" +
                "type='" + type + '\'' +
                ", kategoria='" + kategoria + '\'' +
                ", netto='" + netto + '\'' +
                ", kanal='" + kanal + '\'' +
                ", description='" + description + '\'' +
                ", opis='" + opis + '\'' +
                ", nrKonta='" + nrKonta + '\'' +
                ", kierunek='" + kierunek + '\'' +
                '}';
    }

}