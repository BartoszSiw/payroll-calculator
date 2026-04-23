package pl.edashi.dms.mapper;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.common.util.MappingIdDocs;
import pl.edashi.converter.service.CashReportAssembler;
import pl.edashi.converter.servlet.DocTypeConstants;
import pl.edashi.dms.model.*;
import pl.edashi.dms.model.DmsParsedDocument.DmsVatEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
public class DmsToDmsMapper {
	private final AppLogger log = new AppLogger("DmsToDmsMapper");
    Map<String,Integer> countersd = new HashMap<>();
    public DmsDocumentOut map(DmsParsedDocument src) {
        DmsDocumentOut doc = new DmsDocumentOut();
        doc.setIdZrodla(UUID.randomUUID().toString());
        doc.setModul("Rejestr Vat");
        String srcType = safe(src.getDocumentType()).toUpperCase();
        // Output type must follow the accounting direction, not whatever gen_info guessed.
        // Example: some purchase documents (root id="DZ") come with gen_info type like "FZL"
        // which would incorrectly route them into REJESTR_SPRZEDAZY_VAT.
        MappingTarget mt = src.getMappingTarget();
        if (mt == MappingTarget.PURCHASES && !DocTypeConstants.DZ_TYPES.contains(srcType)) {
            srcType = "DZ";
        } else if (mt == MappingTarget.SALES && !DocTypeConstants.DS_TYPES.contains(srcType)) {
            srcType = "DS";
        }
        String srcWewne = safe(src.getDocumentWewne());
        doc.setTyp(srcType);
        doc.setDocumentType(srcType);
        doc.setDocumentWewne(srcWewne);
        doc.setRejestr(src.getDaneRejestr());
        doc.setNrKsef(safe(src.getNrKsef()));
        if (doc.getRejestr() != null) {
            String mapped = mapDaneRejestr(doc.getRejestr());
            doc.setRejestr(mapped);
        }
        // DATY - null-safe
        DocumentMetadata meta = src.getMetadata();
        String date = meta != null ? stripTime(safe(meta.getDate())) : "";
        String dateSale = meta != null ? stripTime(safe(meta.getDateSale())) : "";
        String dateOperation = meta != null ? stripTime(safe(meta.getDateOperation())) : "";
        String walutaWarto = meta != null ? safe(meta.getWaluta()) : "";
        /*if (safe(src.getKorekta()) == "Tak") {
        	doc.setDataWplywu(date);
            doc.setDataObowiazkuPodatkowego(dateSale);
            doc.setDataPrawaOdliczenia(date);
        }*/
        doc.setDataWystawienia(date);
        doc.setDataSprzedazy(dateSale);
        doc.setTermin(date);
        doc.setDataOperacji(dateOperation);
        doc.setDataWplywu(date);
        doc.setDataObowiazkuPodatkowego(dateSale);
        doc.setDataPrawaOdliczenia(date);
        doc.setWaluta(walutaWarto);
        // NUMER
        doc.setInvoiceNumber(safe(src.getInvoiceNumber()));
        doc.setInvoiceShortNumber(safe(src.getInvoiceShortNumber()));
        doc.setReportNumber(safe(src.getReportNumber()));
        doc.setNrRKB(safe(src.getNrRKB()));
        doc.setNrRep(safe(src.getNrRep()));
        doc.setReportNumberPos(safe(src.getReportNumberPos()));
        doc.setDataOtwarcia(date);
        doc.setDataZamkniecia(date);
        Contractor c = src.getContractor();
        if (c != null) {
            doc.setPodmiotId(safe(c.getId()));
            doc.setPodmiotAkronim(c.getPodmiot());
            doc.setPodmiotNip(safe(c.getNip()));
            doc.setNazwa1(safe(c.getName1()));
            doc.setNazwa2(safe(c.getName2()));
            doc.setNazwa3(safe(c.getName3()));
            doc.setPodmiot(safe(c.getPodmiot()));
            doc.setKraj(safe(c.getCountry()));
            doc.setWojewodztwo(safe(c.getRegion()));
            doc.setPowiat(safe(c.getDistrict()));
            doc.setMiasto(safe(c.getCity()));
            doc.setKodPocztowy(safe(c.getZip()));
            doc.setUlica(safe(c.getStreet()));
            doc.setNrDomu(safe(c.getHouseNumber()));
            doc.setCzynny(safe(c.getCzynny()));
            doc.setExpKrajowy(safe(c.getExpKrajowy()));
        }
        else {
            doc.setExpKrajowy("Nie");
        }
        //log.info(String.format("1 Mapper c krajowy='%s'",c.getExpKrajowy()));
        doc.setDokumentFiskalny(safe(src.getDokumentFiskalny())); 
        doc.setDokumentDetaliczny(safe(src.getDokumentDetaliczny()));
        doc.setKorekta(safe(src.getKorekta()));
        doc.setKorektaNumer(safe(src.getKorektaNumer()));
        doc.setNrIdPlat(safe(src.getNrIdPlat()));
        doc.setFullKey(safe(src.getFullKey()));
        doc.setDocKey(safe(src.getDocKey()));
        doc.setHash(safe(src.getHash()));
        doc.setNrBank(safe(src.getNrBank()));
        doc.setVin(safe(src.getVin()));
        log.info(String.format("2 doc vin='%s'",doc.getVin()));
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
                outPos.setOdliczenia(safe(p.getOdliczenia()));
                outPos.setOpis(safe(p.getOpis()));
                outPos.setKontoMa(safe(p.getKontoMa()));
                outPos.setKontoWn(safe(p.getKontoWn()));
                outPos.setNettoDlRob(safe(p.getNettoDlRob()));
                outPos.setNettoGwMat(safe(p.getNettoGwMat()));
                outPos.setNettoKoMat(safe(p.getNettoKoMat()));
                outPos.setKwotyDodatkowe(p.getKwotyDodatkowe());
                //log.info(String.format("Mapper pos odliczenia='%s'",p.getOdliczenia()));
                log.info(String.format("3 DmsOutputPosition vin='%s'",outPos.getVin()));
                doc.getPozycje().add(outPos);
            }
        }
///      

           //log.info(String.format("2 Mapper c krajowy='%s'",c.getExpKrajowy()));
///
           doc.setRapKasa(new ArrayList<>());
           List<DmsRapKasa> rapKasa = src.getRapKasa();
              if (rapKasa != null && !rapKasa.isEmpty()) {
               for (DmsRapKasa k : rapKasa) {
                   DmsOutputPosition outRk = new DmsOutputPosition();
                   Contractor posContractor = k.getContractor();
                   outRk.setContractor(posContractor);   
                   outRk.setDataWystawienia(date);
                   outRk.setReportNumber(safe(src.getReportNumber()));
                   outRk.setReportNumberPos(safe(src.getReportNumberPos()));
                   outRk.setNrRKB(safe(src.getNrRKB()));
                   outRk.setNrDokumentu(safe(k.getNrDokumentu()));
                   outRk.setKwotaRk(k.getKwotaRk());
                   outRk.setKierunek(k.getKierunek());
                   outRk.setOpis1(k.getOpis1());
                   boolean cardRapLine = "RD".equalsIgnoreCase(srcType) || "DWP".equalsIgnoreCase(srcType);
                   if (cardRapLine) {
                       outRk.setSymbolKPW(CashReportAssembler.symbolCardDokumentuZapisuForRapLine(k.getKierunek()));
                   } else {
                       outRk.setSymbolKPW(CashReportAssembler.symbolDokumentuZapisuForRapLine(k.getDowodNumber(), k.isInvertKpdKwdSymbol()));
                   }
                   outRk.setDocKey(k.getDocKey());
                   String code = null;
                   String mapped;
                   if (cardRapLine) {
                       mapped = safe(CashReportAssembler.mappedCardDowodForRapLine(k.getDowodNumber(), src.getNrRep(), k.getKierunek()));
                   } else {
                       mapped = safe(CashReportAssembler.mappedDowodForRapLine(k.getDowodNumber(), src.getNrRep(), k.isInvertKpdKwdSymbol()));
                   }
                   code = mapped.split("/")[0].trim();
                   String typeKey = null;
                   if ("KWD".equalsIgnoreCase(code)) typeKey = "KWD";
                   else if ("KPD".equalsIgnoreCase(code)) typeKey = "KPD";
                   else if ("DWP".equalsIgnoreCase(code)) typeKey = "DWP";
                   else if ("DW".equalsIgnoreCase(code)) typeKey = "DW";
                   String suffix = "";
                   log.info(String.format("MAPPER BEFORE: k.getDocKey=%s mapped=%s raw=%s getOpis1=%s typeKey=%s countersd=%s", k.getDocKey(),mapped, k.getDowodNumber(), k.getOpis1(), typeKey, countersd));
                   suffix = nextCounter(countersd, typeKey, 3);
                   //log.info(String.format("MAPPER  NrRep()='%s ' suffix='%s '", src.getNrRep(), suffix));
                   String finalMapped = replaceSuffixWithCounter(mapped, suffix);
                   outRk.setDowodNumber(finalMapped);
                   // For assign docKey special cash and card only, not for DS, DZ
   	            String numerFa = finalMapped;
   	            String podmiot = k.getPodmiot();
   	            //String nrIdPlat = MappingIdDocs.generateCandidate(podmiot, numerFa, 36);
   	            String fullKey = MappingIdDocs.buildFullKey(podmiot, numerFa);
   	            //String hash = MappingIdDocs.shortHashFromFullKey(fullKey, 6);
   	            String docKey = MappingIdDocs.generateDocId(podmiot, "D" ,numerFa, 36);
   	            //src.setFullKey(fullKey);//need to update and insert sql
   	            outRk.setFullKey(fullKey);
   	            //src.setDocKey(docKey);
   	            outRk.setDocKey(docKey);
   	            
                   doc.getRapKasa().add(outRk);
                log.info(String.format("MAPPER AFTER: fullKey=%s docKey=%s mapped=%s raw=%s",fullKey, docKey,mapped, k.getDowodNumber()));
                   /*log.info(String.format("MAPPER CHECK: out id='%s' mapped='%s' rapKasaList id='%s' size='%s'",
                		    System.identityHashCode(src), mapped, src.getRapKasa()==null?0:System.identityHashCode(src.getRapKasa()), src.getRapKasa()==null?0:src.getRapKasa().size()));
                		log.info(String.format("MAPPER CHECK: processing k id='%s' nrDokumentu='%s' kwotaRk='%s' dowod='%s' NrRKB='%s'",
                		    System.identityHashCode(k), k.getNrDokumentu(), k.getKwotaRk(), k.getDowodNumber(), k.getNrRKB()));*/

                   //log.info(String.format("MAPPER: doc identity={} file={} dataWystawienia={} nrDokumentu={}",System.identityHashCode(doc), src.getSourceFileName(), doc.getDataWystawienia(), k.getNrDokumentu()));
                   //log.info("Mapper OutRk doc="+doc);
                   //log.info("Mapper: ustawiono k.getNrDokumentu= '" + k.getNrDokumentu() + "'");
                   //log.info(String.format("reportNr='%s ' nrRKB='%s ' kierunek='%s '", k.getReportNumber(), k.getNrRKB(), k.getKierunek()));
               }
           }
              //log.info(String.format("3 Mapper srcType=%s c krajowy='%s'",srcType, c.getExpKrajowy()));
     if ("DZ".equals(srcType) || "FVZ".equals(srcType) || "DS".equals(srcType)) {
    	 //log.info(String.format( "[MAPPER][DZ] file='%s' vatEntries=%d vatBase='%s' vatAmount='%s' vatRate='%s'", safe(src.getSourceFileName()), src.getVatEntries() == null ? -1 : src.getVatEntries().size(), doc.getVatBase(), doc.getVatAmount(), doc.getVatRate() ));
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
        	    //log.info(String.format("[MAPPER][POS] netto=%s vat=%s stawka=%s",op.getNetto(), op.getVat(), op.getStawkaVat(), op.getStatusVat()));
        	}

         // DZ może mieć wiele stawek → Optima wymaga jednej → MIX
         doc.setVatRate("MIX");

     } else {
         // DS → używamy starych pól
         doc.setVatRate(safe(src.getVatRate()));
         doc.setStatusVat(safe(src.getStatusVat()));
         doc.setVatBase(safe(src.getVatBase()));
         doc.setVatAmount(safe(src.getVatAmount()));
     }
        // PŁATNOŚCI (typ 40)
        doc.setPlatnosci(new ArrayList<>());
        List<DmsPayment> payments = src.getPayments();
        if (payments != null && !payments.isEmpty()) {
        	log.info(String.format("payments='%s'",src.getPayments()));
            doc.getPlatnosci().addAll(payments);
        }
        // FAKTURA
        doc.setNumer(safe(src.getInvoiceNumber()));
        doc.setRozszerzone(safe(src.getInvoiceShortNumber()));
        // UWAGI (typ 98)
        doc.setUwagi(src.getNotes() != null ? new ArrayList<>(src.getNotes()) : new ArrayList<>());
        // DODATKOWY OPIS
        doc.setDodatkowyOpis(safe(src.getDodatkowyOpis()));//was doc.setDodatkowyOpis(safe(src.getFiscalNumber()));
        return doc;
    }
    // Null-safe helper
    private static String safe(String s) {
        return s == null ? "" : s;
    }
    private String mapDaneRejestr(String wyr) {
        return switch (wyr) {
            case "EX", "CU" -> "ZK";
            case "PA" -> "Z3";
            case "CD", "CP", "CR" -> "Z1";
            case "CC", "OT" -> "ZAKUP";
            case "001" -> "001";
            case "002" -> "002";
            case "003" -> "003";
            case "040" -> "040" ;
            case "041" -> "041" ;
            case "070" -> "070" ;
            case "100" -> "100" ;
            case "101" -> "101" ;
            case "102" -> "102" ;
            case "110" -> "110" ;
            case "120" -> "120" ;
            case "121" -> "121" ;
            case "122" -> "122" ;
            case "130" -> "130" ;
            case "132" -> "132" ;
            case "139" -> "139" ;
            case "141" -> "141" ;
            case "143" -> "143" ;
            case "191" -> "191" ;
            case "200" -> "200" ;
            case "240" -> "240" ;
            case "290" -> "290" ;
            case "310" -> "310" ;
            case "320" -> "320" ;
            case "330" -> "330" ;
            case "400" -> "400" ;
            default -> "";
        };
    }
    private String stripTime(String dateTime) {
        if (dateTime == null) return null;
        int space = dateTime.indexOf(' ');
        return space > 0 ? dateTime.substring(0, space) : dateTime;
    }
    private double parseDoubleSafe(String s) {
        if (s == null || s.isBlank()) return 0.0;
        try {
            return Double.parseDouble(s.replace(",", "."));
        } catch (Exception e) {
            return 0.0;
        }
    }
    private String nextCounter(Map<String,Integer> countersd, String type, int width) {
        if (type == null) type = "";
        Integer cur = countersd.get(type);
        if (cur == null) cur = 0;
        cur = cur + 1;
        countersd.put(type, cur);
        return String.format("%0" + width + "d", cur); // "001", "002", ...
    }

    /**
     * mappedDowodNumber expected format: TYPE/1/NNNNNN/YYYY
     * We keep first 3 digits of NNNNNN (report prefix) and replace last 3 digits with counterSuffix.
     * If input doesn't match, return original mapped.
     */
    private String replaceSuffixWithCounter(String mappedDowodNumber, String counterSuffix) {
        if (mappedDowodNumber == null || counterSuffix == null) return mappedDowodNumber;
        String s = mappedDowodNumber.trim();
        // prosty split: TYPE / 1 / NNNNNN / YYYY
        String[] parts = s.split("/");
        if (parts.length < 4) return mappedDowodNumber;
        String type = parts[0].trim();      // e.g. "DW"
        String one = parts[1].trim();       // should be "1"
        String six = parts[2].trim();       // e.g. "003019"
        String year = parts[3].trim();      // e.g. "2026"
        if (six.length() != 6) {
            // spróbuj sformatować do 6 cyfr jeśli to możliwe
            String digits = six.replaceAll("\\D", "");
            if (digits.length() > 6) digits = digits.substring(digits.length() - 6);
            six = String.format("%06d", digits.isEmpty() ? 0 : Integer.parseInt(digits));
        }
        String prefix = six.substring(0, 3); // zachowujemy te 3 cyfry (nr raportu)
        String newSix = prefix + String.format("%03d", Integer.parseInt(counterSuffix)); // prefix + counterSuffix(3)
        return String.format("%s/%s/%s/%s", type, one, newSix, year);
    }
}
