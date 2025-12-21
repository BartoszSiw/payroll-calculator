package pl.edashi.dms.mapper;
import pl.edashi.converter.model.*;
import pl.edashi.dms.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
public class DsToDmsMapper {

    public DmsDocument map(DsParsedDocument src) {
    	
        DmsDocument doc = new DmsDocument();

        // META
        doc.idZrodla = UUID.randomUUID().toString();
        doc.modul = "Handel";
        doc.typ = "Faktura sprzedaży";
        doc.rejestr = "SPRZEDAŻ";

        // DATY
        doc.dataWystawienia = src.metadata.getDate(); // musisz dodać w metadata
        doc.dataSprzedazy = src.metadata.getDate();
        doc.termin = src.metadata.getDate();
        doc.numer = src.metadata.getGenDocId();
        // NUMER
        doc.numer = src.metadata.getGenDocId();

        // PODMIOT
        if (src.contractor != null) {
            doc.podmiotId = src.contractor.id;
            doc.podmiotNip = src.contractor.nip;
            doc.nazwa1 = src.contractor.name1;
            doc.nazwa2 = src.contractor.name2;
            doc.nazwa3 = src.contractor.name3;
            doc.kraj = src.contractor.country;
            doc.wojewodztwo = src.contractor.region;
            doc.powiat = src.contractor.district;
            doc.miasto = src.contractor.city;
            doc.kodPocztowy = src.contractor.zip;
            doc.ulica = src.contractor.street;
            doc.nrDomu = src.contractor.houseNumber;
        }

        // POZYCJE (typ 03)
        doc.pozycje = new ArrayList<>();
        if (src.positions != null) {
            for (var p : src.positions) {
                DmsPosition pos = new DmsPosition();
                pos.kategoria = p.kategoria;
                pos.stawkaVat = p.stawkaVat;
                pos.netto = p.netto;
                pos.vat = p.vat;
                pos.rodzajSprzedazy = p.rodzajSprzedazy;
                doc.pozycje.add(pos);
            }
        }
        // VAT (typ 06)
        doc.vatRate = src.vatRate;
        doc.vatBase = src.vatBase;
        doc.vatAmount = src.vatAmount;
        // PŁATNOŚCI (typ 40)
        doc.platnosci = new ArrayList<>();
        if (src.payments != null) {
            for (var p : src.payments) {
                DmsPayment pay = new DmsPayment();
                pay.termin = p.termin;
                pay.forma = p.forma;
                pay.kwota = p.kwota;
                pay.kierunek = p.kierunek;
                pay.opis = p.opis;
                doc.platnosci.add(pay);
            }
        }

        // UWAGI (typ 98)
        doc.uwagi = src.notes;

        // DODATKOWY OPIS
        doc.dodatkowyOpis = src.additionalDescription;

        return doc;
    }
}
