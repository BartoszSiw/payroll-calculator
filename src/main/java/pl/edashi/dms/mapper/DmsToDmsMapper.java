package pl.edashi.dms.mapper;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.dms.model.*;
import pl.edashi.dms.model.DmsParsedDocument.DmsVatEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
public class DmsToDmsMapper {
	private final AppLogger log = new AppLogger("DmsToDmsMapper");
    public DmsDocumentOut map(DmsParsedDocument src) {
        DmsDocumentOut doc = new DmsDocumentOut();
        doc.setIdZrodla(UUID.randomUUID().toString());
        doc.setModul("Rejestr Vat");
        String srcType = safe(src.getDocumentType()).toUpperCase();
        String srcWewne = safe(src.getDocumentWewne());
        doc.setTyp(srcType);
        doc.setDocumentType(srcType);
        doc.setDocumentWewne(srcWewne);
        doc.setRejestr(src.getDaneRejestr());
        if (doc.getRejestr() != null) {
            String mapped = mapDaneRejestr(doc.getRejestr());
            doc.setRejestr(mapped);
        }
        // DATY - null-safe
        DocumentMetadata meta = src.getMetadata();
        String date = meta != null ? safe(meta.getDate()) : "";
        String dateSale = meta != null ? safe(meta.getDateSale()) : "";
        String dateoOperation = meta != null ? safe(meta.getDateOperation()) : "";
        String walutaWarto = meta != null ? safe(meta.getWaluta()) : "";
        doc.setDataWystawienia(date);
        doc.setDataSprzedazy(dateSale);
        doc.setTermin(date);
        doc.setDataOperacji(dateoOperation);
        doc.setDataWplywu(date);
        doc.setDataObowiazkuPodatkowego(date);
        doc.setDataPrawaOdliczenia(date);
        doc.setWaluta(walutaWarto);
        // NUMER
        doc.setInvoiceNumber(safe(src.getInvoiceNumber()));
        doc.setInvoiceShortNumber(safe(src.getInvoiceShortNumber()));
        Contractor c = src.getContractor();
        if (c != null) {
            doc.setPodmiotId(safe(c.getId()));
            doc.setPodmiotAkronim(c.getFullName());
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
            doc.setCzynny(safe(c.getCzynny()));
            
        }
        doc.setDokumentFiskalny(safe(src.getDokumentFiskalny())); 
        doc.setDokumentDetaliczny(safe(src.getDokumentDetaliczny()));
        doc.setKorekta(safe(src.getKorekta()));
        doc.setKorektaNumer(safe(src.getKorektaNumer()));
     // POZYCJE - inicjalizacja listy i mapowanie pozycji
        doc.setPozycje(new ArrayList<>());
        List<DmsPosition> positions = src.getPositions();
           if (positions != null && !positions.isEmpty()) {
            for (DmsPosition p : positions) {
                DmsOutputPosition outPos = new DmsOutputPosition();
                /*log.info("Mapper START: src.advanceNet=" + src.getAdvanceNet() 
                + ", src.advanceVat=" + src.getAdvanceVat()
                + ", src identity=" + System.identityHashCode(src));*/
                outPos.setKategoria(safe(p.getKategoria()));
                outPos.setKategoria2(safe(p.getKategoria2()));
                outPos.setStawkaVat(safe(p.getStawkaVat()));
                outPos.setStatusVat(safe(p.getStatusVat()));
                outPos.setNetto(safe(p.getNetto()));
                outPos.setNettoZakup(safe(p.getNettoZakup()));
                outPos.setVat(safe(p.getVat()));
                outPos.setRodzajSprzedazy(safe(p.getRodzajSprzedazy()));
                outPos.setRodzajKoszty(safe(p.getRodzajKoszty()));
                outPos.setVin(safe(p.getVin()));
                outPos.setKanal(safe(p.getKanal()));
                outPos.setKanalKategoria(safe(p.getKanalKategoria()));
                outPos.setKierunek(safe(p.getKierunek()));
                outPos.setOpis(safe(p.getOpis()));
                outPos.setKontoMa(safe(p.getKontoMa()));
                outPos.setKontoWn(safe(p.getKontoWn()));
                outPos.setNettoDlRob(safe(p.getNettoDlRob()));
                outPos.setNettoGwMat(safe(p.getNettoGwMat()));
                outPos.setNettoKoMat(safe(p.getNettoKoMat()));
                outPos.setKwotyDodatkowe(p.getKwotyDodatkowe());
                doc.getPozycje().add(outPos);
            }
        }
           doc.setRapKasa(new ArrayList<>());
           List<DmsRapKasa> rapKasa = src.getRapKasa();
              if (rapKasa != null && !rapKasa.isEmpty()) {
               for (DmsRapKasa k : rapKasa) {
                   DmsOutputPosition outRk = new DmsOutputPosition();
                   outRk.setReportNumber(k.getReportNumber());
                   outRk.setNrRKB(k.getNrRKB());
                   outRk.setKwota(k.getKwota());
                   doc.getRapKasa().add(outRk);
               }
           }
     // po zmapowaniu wszystkich DmsPosition na DmsOutputPosition
        /*log.info("Mapper: before advance mapping, doc.getPozycje().size=" + (doc.getPozycje() == null ? 0 : doc.getPozycje().size()));

        double advNet = parseDoubleSafe(src.getAdvanceNet()); // src to DmsParsedDocument przekazywany do mappera
        double advVat = parseDoubleSafe(src.getAdvanceVat());

        if (Math.abs(advNet) > 0.0001 || Math.abs(advVat) > 0.0001) {
            List<DmsOutputPosition> outPositions = doc.getPozycje();
            if (outPositions == null) {
                outPositions = new ArrayList<>();
                doc.setPozycje(outPositions);
            }

            DmsOutputPosition advOut = new DmsOutputPosition();
            advOut.setOpis("ZALICZKA");
            advOut.setKategoria("");
            advOut.setKanalKategoria("");
            advOut.setStawkaVat("");
            advOut.setStatusVat("");

            advOut.setNetto(String.format(Locale.US, "%.2f", -advNet));
            advOut.setVat(String.format(Locale.US, "%.2f", -advVat));
            advOut.setBrutto(String.format(Locale.US, "%.2f", -(advNet + advVat)));

            // jeśli builder numeruje lp sam, możesz pominąć setLp
            advOut.setLp(String.valueOf(outPositions.size() + 1));

            try { advOut.setAdvance(true); } catch (Throwable ignored) {}
            outPositions.add(advOut);

            log.info("Mapper: appended advance position -> netto=" + advOut.getNetto() + " vat=" + advOut.getVat());
        }

        log.info("Mapper: after advance mapping, doc.getPozycje().size=" + (doc.getPozycje() == null ? 0 : doc.getPozycje().size()));*/
     // VAT (typ 06)
     // ===============================
     // VAT — DS vs DZ
     // ===============================
     if ("DZ".equals(srcType) || "FVZ".equals(srcType)) {
    	 log.info(String.format( "[MAPPER][DZ] file='%s' vatEntries=%d vatBase='%s' vatAmount='%s' vatRate='%s'", safe(src.getSourceFileName()), src.getVatEntries() == null ? -1 : src.getVatEntries().size(), doc.getVatBase(), doc.getVatAmount(), doc.getVatRate() ));
         // DZ → VAT liczymy z vatEntries (typ 06)
         double base = 0.0;
         double vat = 0.0;

         for (DmsVatEntry e : src.getVatEntries()) {
             base += parseDoubleSafe(e.podstawa);
             vat  += parseDoubleSafe(e.vat);
         }

         doc.setVatBase(String.format(Locale.US, "%.2f", base));
         doc.setVatAmount(String.format(Locale.US, "%.2f", vat));
         for (DmsOutputPosition op : doc.getPozycje()) {
        	    log.info(String.format(
        	        "[MAPPER][POS] netto=%s vat=%s stawka=%s",
        	        op.getNetto(), op.getVat(), op.getStawkaVat()
        	    ));
        	}

         // DZ może mieć wiele stawek → Optima wymaga jednej → MIX
         doc.setVatRate("MIX");

     } else {
         // DS → używamy starych pól
         doc.setVatRate(safe(src.getVatRate()));
         doc.setVatBase(safe(src.getVatBase()));
         doc.setVatAmount(safe(src.getVatAmount()));
     }
        // PŁATNOŚCI (typ 40)
        doc.setPlatnosci(new ArrayList<>());
        List<DmsPayment> payments = src.getPayments();
        if (payments != null && !payments.isEmpty()) {
            doc.getPlatnosci().addAll(payments);
        }
        // FAKTURA
        doc.setNumer(safe(src.getInvoiceNumber()));
        doc.setRozszerzone(safe(src.getInvoiceShortNumber()));
        // UWAGI (typ 98)
        doc.setUwagi(src.getNotes() != null ? new ArrayList<>(src.getNotes()) : new ArrayList<>());
        // DODATKOWY OPIS
        doc.setDodatkowyOpis(safe(src.getFiscalNumber()));
        return doc;
    }
    // Null-safe helper
    private static String safe(String s) {
        return s == null ? "" : s;
    }
    private String mapDaneRejestr(String wyr) {
        return switch (wyr) {
            case "EX" -> "ZK";
            case "PA" -> "Z4";
            case "CD", "CP", "CR" -> "Z1";
            case "CC" -> "ZAKUP";
            case "001" -> "001";
            case "040" -> "040" ;
            case "041" -> "041" ;
            case "070" -> "070" ;
            case "100" -> "100" ;
            case "101" -> "101" ;
            case "102" -> "102" ;
            case "110" -> "110" ;
            case "120" -> "120" ;
            case "121" -> "121" ;
            case "141" -> "141" ;
            case "143" -> "143" ;
            case "191" -> "191" ;
            case "200" -> "200" ;
            case "240" -> "240" ;
            case "290" -> "290" ;
            case "310" -> "310" ;
            case "330" -> "330" ;
            case "400" -> "400" ;
            default -> "";
        };
    }
    private double parseDoubleSafe(String s) {
        if (s == null || s.isBlank()) return 0.0;
        try {
            return Double.parseDouble(s.replace(",", "."));
        } catch (Exception e) {
            return 0.0;
        }
    }

}
