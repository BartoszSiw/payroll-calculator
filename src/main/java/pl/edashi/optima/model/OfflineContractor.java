package pl.edashi.optima.model;

public class OfflineContractor {

    public String idZrodla;
    public String akronim;
    public String rodzaj;           // odbiorca
    public String eksport;          // krajowy
    public String platnikVat;       // Tak/ Nie (dla osoby fizycznej: zwykle Nie)
    public String finalny;          // Nie
    public String medialny;         // Nie
    public String krajIso;          // PL
    public String algorytm;         // netto

    // Adres
    public String nazwa1;
    public String nazwa2;
    public String nazwa3;
    public String kraj;
    public String wojewodztwo;
    public String powiat;
    public String gmina;
    public String ulica;
    public String nrDomu;
    public String nrLokalu;
    public String miasto;
    public String kodPocztowy;
    public String poczta;

    public String nipKraj;
    public String nip;
    public String regon;
    public String pesel;

    // Płatności
    public String formaPlatnosci;
    public String formaPlatnosciId;
    public int termin;

    // rachunki bankowe itp. – na razie pomijamy
}

