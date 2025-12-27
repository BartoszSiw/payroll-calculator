package pl.edashi.dms.model;

public class DmsParsedContractor {
    public String id;            // UUID
    public String akronim;
    public boolean isCompany;
    public String rodzaj;
    public String fullName;      // Imię + Nazwisko (dla O) lub nazwa1/nazwa2/nazwa3 dla F
    public String nazwa1;
    public String nazwa2;
    public String nazwa3;
    public String ulica;
    public String nrDomu;
    public String nrLokalu;
    public String kodPocztowy;
    public String miasto;
    public String kraj;
    public String nip;
    public String regon;
    public String pesel;
    public String wyrRaw;        // oryginalne wyr z DMS ("F" / "O")
}
