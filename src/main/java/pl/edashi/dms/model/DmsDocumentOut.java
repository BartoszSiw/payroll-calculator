package pl.edashi.dms.model;
import java.util.ArrayList;
import java.util.List;
public class DmsDocumentOut {
	public List<DmsOutputPosition> pozycje = new ArrayList<>();
	// META
    public String idZrodla;
    public String modul;
    public String typ;
    public String rejestr;
    public String dataWystawienia;
    public String dataSprzedazy;
    public String termin;
    public String numer;
    public boolean korekta;
    // PODMIOT
    public String podmiotId;
    public String podmiotNip;
    public String nazwa1;
    public String nazwa2;
    public String nazwa3;
    public String kraj;
    public String wojewodztwo;
    public String powiat;
    public String miasto;
    public String kodPocztowy;
    public String ulica;
    public String nrDomu;
    //FAKTURA
    public String rozszerzone;
    //public String invoiceNumber;
    public String invoiceShortNumber;
    // POZYCJE
 
    // PŁATNOŚCI
    public List<DmsPayment> platnosci;
    // VAT
    public String vatRate;
    public String vatBase;
    public String vatAmount;

    // OPISY
    public String dodatkowyOpis;
    public List<String> uwagi;
}

