package pl.edashi.dms.model;

import java.util.ArrayList;
import java.util.List;

import pl.edashi.dms.parser.DmsParserDS.DmsKwotaDodatkowa;

public class DmsPosition {
	private List<DmsKwotaDodatkowa> kwotyDodatkowe = new ArrayList<>();
	public String type = ""; 
    public String kategoria = "";
    public String kategoria2;
    public String stawkaVat = "";
    public String statusVat = "";
    public String netto = "";
    public String nettoZakup = "";
    public String nettoRob;
    public String vat = "";
    public String rodzajSprzedazy = "";
    public String rodzajKoszty = "";
	public String vin = "";
	public String kanal = "";
	public String kanalKategoria = "";
	public String description = "";
	public String opis = "";
	public String klasyfikacja = "";
	public String numer = "";
	public String kodVat = "";
	public String typDZ = "";
	public String jm = "";
	public String jmSymb = "";
	public String opis1 = "";
	public String cenaNetto = "";
	public String brutto = "";
	public String kierunek = "";
	public String lp;
	public String nrKonta;
	public String podstawaVat;
	public String kwota;
	public String kontoMa;
	public String kontoWn;
	public String nettoDlRob;
	public String nettoKoMat;
	public String nettoGwMat;
	public String nettoKoszt;
	public String kodKlasyfikatora;
    public DmsPosition() { }

    // --- Gettery ---
		
	public String getType() {
		return type;
	}
    public String getKategoria() { return kategoria; }
    public String getKategoria2() { return kategoria2; }
    public String getStawkaVat() { return stawkaVat; }
    public String getStatusVat() { return statusVat; }
    public String getNetto() { return netto; }
    public String getNettoRob() { return nettoRob; }
    public String getBrutto() { return brutto; }
    public String getNettoZakup() { return nettoZakup; }
    public String getVat() { return vat; }
    public String getRodzajSprzedazy() { return rodzajSprzedazy; }
    public String getRodzajKoszty() { return rodzajKoszty; }
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
	public String getNrKonta() { return nrKonta; };
	public String getKierunek() { return kierunek; }
	public String getLp() { return lp; }
	public String getPodstawaVat() { return podstawaVat; }
	public String getKwota() { return kwota; }
	public String getKontoMa() { return kontoMa; }
	public String getKontoWn() { return kontoWn; }
	public String getNettoDlRob() { return nettoDlRob; }
	public String getNettoKoMat() { return nettoKoMat; }
	public String getNettoKoszt() { return nettoKoszt; }
	public String getNettoGwMat() { return nettoGwMat; }
	public String getKodKlasyfikatora() { return kodKlasyfikatora; }
	public List<DmsKwotaDodatkowa> getKwotyDodatkowe() {return kwotyDodatkowe;}
    // --- Settery (null-safe) ---
    public void setType(String type) { this.type = type != null ? type : ""; }
    public void setKategoria(String kategoria) { this.kategoria = kategoria != null ? kategoria : ""; }
    public void setKategoria2(String kategoria2) { this.kategoria2 = kategoria2 != null ? kategoria2 : ""; }
    public void setStawkaVat(String stawkaVat) { this.stawkaVat = stawkaVat != null ? stawkaVat : ""; }
    public void setStatusVat(String statusVat) { this.stawkaVat = statusVat != null ? statusVat : ""; }
    public void setNetto(String netto) { this.netto = netto != null ? netto : ""; }
    public void setNettoRob(String nettoRob) { this.nettoRob = nettoRob != null ? nettoRob : ""; }
    public void setBrutto(String brutto) { this.brutto = brutto != null ? brutto : ""; }
    public void setNettoZakup(String nettoZakup) { this.nettoZakup = nettoZakup != null ? nettoZakup : ""; }
    public void setVat(String vat) { this.vat = vat != null ? vat : ""; }
    public void setRodzajSprzedazy(String rodzajSprzedazy) { this.rodzajSprzedazy = rodzajSprzedazy != null ? rodzajSprzedazy : ""; }
    public void setRodzajKoszty(String rodzajKoszty) { this.rodzajKoszty = rodzajKoszty != null ? rodzajKoszty : ""; }
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
	public void setNrKonta(String nrKonta) { this.nrKonta = nrKonta != null ? nrKonta: "";}
	public void setKierunek(String kierunek) { this.kierunek = kierunek != null ? kierunek : ""; }
	public void setLp(String lp) { this.lp = lp != null ? lp : ""; }
	public void setPodstawaVat(String lp) { this.podstawaVat = podstawaVat != null ? podstawaVat : ""; }
	public void setKwota(String kwota) { this.kwota = kwota != null ? kwota : ""; }
	public void setKontoMa(String kontoMa) { this.kontoMa = kontoMa != null ? kontoMa : ""; }
	public void setKontoWn(String kontoWn) { this.kontoWn = kontoWn != null ? kontoWn : ""; }
	public void setNettoDlRob(String nettoDlRob) { this.nettoDlRob = nettoDlRob != null ? nettoDlRob : "";}
	public void setNettoKoMat(String nettoKoMat) { this.nettoKoMat = nettoKoMat != null ? nettoKoMat : "";}
	public void setNettoGwMat(String nettoGwMat) { this.nettoGwMat = nettoGwMat != null ? nettoGwMat : "";}
	public void setNettoKoszt(String nettoKoszt) { this.nettoKoszt = nettoKoszt!= null ? nettoKoszt : "";}
	public void setKodKlasyfikatora(String kodKlasyfikatora) { this.kodKlasyfikatora = kodKlasyfikatora!= null ? kodKlasyfikatora : "";}
	public void setKwotyDodatkowe(List<DmsKwotaDodatkowa> list) {this.kwotyDodatkowe = list;}

    @Override
    public String toString() {
        return "DmsPosition{" +
                "type='" + type + '\'' +
                ", kategoria='" + kategoria + '\'' +
                ", kategoria2='" + kategoria2 + '\'' +
                ", stawkaVat='" + stawkaVat + '\'' +
                ", netto='" + netto + '\'' +
                ", btutto='" + brutto + '\'' +
                ", nettoZakup='" + nettoZakup + '\'' +
                ", vat='" + vat + '\'' +
                ", rodzajSprzedazy='" + rodzajSprzedazy + '\'' +
                ", vin='" + vin + '\'' +
                ", kanal='" + kanal + '\'' +
                ", kanalKategoria='" + kanalKategoria + '\'' +
                ", description='" + description + '\'' +
                ", opis='" + opis + '\'' +
                ", nrKonta='" + nrKonta + '\'' +
                ", kierunek='" + kierunek + '\'' +
                '}';
    }
}
