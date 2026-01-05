package pl.edashi.dms.mapper;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.dms.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
public class DmsToDmsMapper {
	private final AppLogger log = new AppLogger("DmsToDmsMapper");
    public DmsDocumentOut map(DmsParsedDocument src) {
        DmsDocumentOut doc = new DmsDocumentOut();
        // META
       //log.info(String.format("[Mapper] BEFORE mapping: src.documentType='%s', src.invoiceShort='%s'",
                //src.getDocumentType(), src.getInvoiceShortNumber()));
        doc.setIdZrodla(UUID.randomUUID().toString());
        doc.setModul("Rejestr Vat");
     // zamiast doc.setTyp("DS");
        //String srcType = src.getDocumentType();
        //doc.setTyp(srcType != null && !srcType.isBlank() ? srcType.trim().toUpperCase() : "DS");
        //doc.setTyp("DS"); // bo to jest parser DS
        //doc.setDocumentType(safe(src.getDocumentType()));
        String srcType = safe(src.getDocumentType()).toUpperCase();
        doc.setTyp(srcType);
        doc.setDocumentType(srcType);

        doc.setRejestr("SPRZEDAŻ");

        // DATY - null-safe
        DocumentMetadata meta = src.getMetadata();
        String date = meta != null ? safe(meta.getDate()) : "";
        doc.setDataWystawienia(date);
        doc.setDataSprzedazy(date);
        doc.setTermin(date);

        // NUMER
        doc.setInvoiceNumber(safe(src.getInvoiceNumber()));
        doc.setInvoiceShortNumber(safe(src.getInvoiceShortNumber()));
        //doc.setDocumentType(safe(src.getDocumentType())); // zostaw, ale upewnij się, że DmsDocumentOut ma getter
        log.info(String.format("Mapper: src.documentType='%s' -> doc.typ='%s' file=%s InvoiceNumber=%s InvoiceShortNumber=%s ",
                src.getDocumentType(), doc.getTyp(), src.getSourceFileName(), src.getInvoiceNumber(), src.getInvoiceShortNumber()));

        //doc.numer = src.invoiceShortNumber;
        // PODMIOT (null-safe, używamy getterów)
        Contractor c = src.getContractor();
        if (c != null) {
            doc.setPodmiotId(safe(c.getId()));
            doc.setPodmiotNip(safe(c.getNip()));
            doc.setNazwa1(safe(c.getName1()));
            doc.setNazwa2(safe(c.getName2()));
            doc.setNazwa3(safe(c.getName3()));
            doc.setKraj(safe(c.getCountry()));
            doc.setWojewodztwo(safe(c.getRegion()));
            doc.setPowiat(safe(c.getDistrict()));
            doc.setMiasto(safe(c.getCity()));
            doc.setKodPocztowy(safe(c.getZip()));
            doc.setUlica(safe(c.getStreet()));
            doc.setNrDomu(safe(c.getHouseNumber()));
        }
     // POZYCJE - inicjalizacja listy i mapowanie pozycji
        doc.setPozycje(new ArrayList<>());
        List<DmsPosition> positions = src.getPositions();
        if (positions != null && !positions.isEmpty()) {
            for (DmsPosition p : positions) {
                DmsOutputPosition outPos = new DmsOutputPosition();
                outPos.setKategoria(safe(p.getKategoria()));
                outPos.setStawkaVat(safe(p.getStawkaVat()));
                outPos.setNetto(safe(p.getNetto()));
                outPos.setVat(safe(p.getVat()));
                outPos.setRodzajSprzedazy(safe(p.getRodzajSprzedazy()));
                outPos.setVin(safe(p.getVin()));
                outPos.setKanal(safe(p.getKanal()));
                outPos.setKanalKategoria(safe(p.getKanalKategoria()));
                doc.getPozycje().add(outPos);
                //log.info(String.format("[CHECK VAT] Mapping: netto='%s', getVat='%s', getVatZ='%s'",
                		//outPos.getNetto(),outPos.getVat()));
            }
        }

     // VAT (typ 06)
        doc.setVatRate(safe(src.getVatRate()));
        doc.setVatBase(safe(src.getVatBase()));
        doc.setVatAmount(safe(src.getVatAmount()));
        //doc.setVatZ(safe(src.getVatZ())); // odkomentuj jeśli masz pole vatZ i getter w src

        // PŁATNOŚCI (typ 40)
        doc.setPlatnosci(new ArrayList<>());
        List<DmsPayment> payments = src.getPayments();
        if (payments != null && !payments.isEmpty()) {
            // jeśli DmsPayment jest zgodny z doc.platnosci, kopiujemy bezpośrednio
            doc.getPlatnosci().addAll(payments);
            // jeśli trzeba mapować pola DmsPayment -> inny typ, zrób mapowanie tutaj
        }

        // FAKTURA
        doc.setNumer(safe(src.getInvoiceNumber()));
        doc.setRozszerzone(safe(src.getInvoiceShortNumber()));

        // UWAGI (typ 98)
        doc.setUwagi(src.getNotes() != null ? new ArrayList<>(src.getNotes()) : new ArrayList<>());

        // DODATKOWY OPIS
        doc.setDodatkowyOpis(safe(src.getFiscalNumber()));
        //log.info(String.format("[Mapper] AFTER mapping: doc.typ='%s', doc.documentType='%s'",
                //doc.getTyp(), doc.getDocumentType()));
        //log.info(String.format("[CHECK] Mapping: type='%s', docType='%s', netto={}, brutto={}, vat={}",
        	    //doc.getTyp(),src.getDocumentType()));

        return doc;
    }
    // Null-safe helper
    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
