package pl.edashi.dms.mapper;
import pl.edashi.dms.model.*;
import java.util.ArrayList;
import java.util.UUID;
public class DmsToDmsMapper {

    public DmsDocumentOut map(DmsParsedDocument src) {
    	
        DmsDocumentOut doc = new DmsDocumentOut();

        // META
        doc.idZrodla = UUID.randomUUID().toString();
        doc.modul = "Rejestr Vat";
        doc.typ = "DS";
        doc.rejestr = "SPRZEDAŻ";

        // DATY
        doc.dataWystawienia = src.metadata.getDate(); // musisz dodać w metadata
        doc.dataSprzedazy = src.metadata.getDate();
        doc.termin = src.metadata.getDate();

        // NUMER
        //doc.numer = src.metadata.getGenDocId();
        //doc.invoiceShortNumber = src.invoiceShortNumber;
        doc.invoiceNumber = src.invoiceNumber;
        doc.documentType = src.documentType;
        //doc.numer = src.invoiceShortNumber;
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

        doc.pozycje = new ArrayList<>();
        //doc.pozycjeRobocizna = new ArrayList<>();

        // POZYCJE – 03 i 04 (wszystkie z listy)
        if (src.positions != null) {
            for (DmsPosition p : src.positions) {
                DmsOutputPosition outPos = new DmsOutputPosition();
                outPos.kategoria       = p.kategoria;
                outPos.stawkaVat       = p.stawkaVat;
                outPos.netto           = p.netto;
                outPos.vat             = p.vat;
                outPos.rodzajSprzedazy = p.rodzajSprzedazy;
                outPos.vin             = p.vin;
                outPos.kanal		   = p.kanal;
                outPos.kanalKategoria  = p.kanalKategoria;
                doc.pozycje.add(outPos);
            }
        }

        // VAT (typ 06)
        doc.vatRate = src.vatRate;
        doc.vatBase = src.vatBase;
        doc.vatAmount = src.vatAmount;
        //doc.vatZ = src.vatZ;
        // PŁATNOŚCI (typ 40)
        doc.platnosci = new ArrayList<>();
        if (src.payments != null) {
            for (DmsPayment p : src.payments) {
                doc.platnosci.add(p);
            }
        }
        // FAKTURA
        doc.numer = src.invoiceNumber;
        doc.rozszerzone = src.invoiceShortNumber;
        //doc.identyfikatorKsiegowy = src.invoiceNumber;
        //doc.platSplitNrDokumentu = src.invoiceNumber;

        // UWAGI (typ 98)
        doc.uwagi = src.notes;

        // DODATKOWY OPIS
        doc.dodatkowyOpis = src.fiscalNumber;;
        return doc;
    }
}
