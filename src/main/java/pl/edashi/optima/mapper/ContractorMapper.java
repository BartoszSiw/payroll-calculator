package pl.edashi.optima.mapper;

import pl.edashi.dms.model.DmsParsedContractor;
import pl.edashi.optima.model.OfflineContractor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ContractorMapper {

    // przykładowe stałe – dopasujesz pod swoją instalację
    private static final String DEFAULT_FORMA_PLATNOSCI = "gotówka";
    private static final String DEFAULT_FORMA_PLATNOSCI_ID = " "; //FA1FD00E-70D3-4DDA-8984-5F47EAF93854

    public OfflineContractor map(DmsParsedContractor src) {

        OfflineContractor c = new OfflineContractor();

        c.idZrodla = src.id;
        c.akronim = src.akronim;

        c.rodzaj = "odbiorca";
        c.eksport = "krajowy";
        c.finalny = "Nie";
        c.medialny = "Nie";
        c.krajIso = "PL";
        c.algorytm = "netto";

        if (src.isCompany) {
            c.platnikVat = "Tak";
            c.nip = src.nip;
            c.regon = src.regon;
            c.pesel = "";
        } else {
            c.platnikVat = "Nie";
            c.nip = "";
            c.regon = "";
            c.pesel = src.pesel;
        }

        c.nazwa1 = src.nazwa1;
        c.nazwa2 = src.nazwa2;
        c.nazwa3 = src.nazwa3;

        c.kraj = src.kraj;
        c.ulica = src.ulica;
        c.nrDomu = src.nrDomu;
        c.nrLokalu = src.nrLokalu;
        c.miasto = src.miasto;
        c.kodPocztowy = src.kodPocztowy;
        c.poczta = src.miasto;

        c.formaPlatnosci = DEFAULT_FORMA_PLATNOSCI;
        c.formaPlatnosciId = DEFAULT_FORMA_PLATNOSCI_ID;
        c.termin = 0;

        return c;
    }
    public List<OfflineContractor> map(List<DmsParsedContractor> list) {
        List<OfflineContractor> out = new ArrayList<>();
        for (DmsParsedContractor c : list) {
            out.add(map(c)); // używa istniejącej metody map(DmsParsedContractor)
        }
        return out;
    }

}

