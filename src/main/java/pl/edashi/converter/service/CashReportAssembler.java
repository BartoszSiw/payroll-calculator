package pl.edashi.converter.service;

import pl.edashi.dms.model.Contractor;
import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsOutputPosition;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.xml.CashReportXmlBuilder;
import pl.edashi.dms.mapper.DmsToDmsMapper.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CashReportAssembler {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CashReportAssembler.class);
    private final List<DmsParsedDocument> docs;

    public CashReportAssembler(List<DmsParsedDocument> docs) {
        this.docs = docs;
    }

    public DmsDocumentOut buildSingleReport(String nrRaportu) {

        // 1. KO – otwarcie raportu
        DmsParsedDocument ko = docs.stream()
                .filter(d -> "KO".equals(d.getDocumentType()))
                .filter(d -> nrRaportu.equals(d.getReportNumber()))
                .findFirst()
                .orElse(null);

        // 2. KZ – zamknięcie raportu
        DmsParsedDocument kz = docs.stream()
                .filter(d -> "KZ".equals(d.getDocumentType()))
                .filter(d -> nrRaportu.equals(d.getReportNumber()))
                .findFirst()
                .orElse(null);

        // raport musi mieć KO + KZ
        if (ko == null || kz == null) {
        	//LOG.info("Assembler: incomplete report " + nrRaportu + " KO=" + (ko!=null) + " KZ=" + (kz != null ));
            return null;
        }

        // 3. DK – wszystkie zapisy KP/KW do tego raportu
        List<DmsParsedDocument> dkList = docs.stream()
                .filter(d -> "DK".equals(d.getDocumentType()) || "RD".equals(d.getDocumentType()))
                .filter(d -> nrRaportu.equals(d.getReportNumber()))
                .collect(Collectors.toList());
        /*docs.forEach(d -> LOG.info("DOCS LIST: identity={} file={} type={} dataWystawienia={}",
        	    System.identityHashCode(d), d.getSourceFileName(), d.getDocumentType(), d.getDataWystawienia()));*/

        //LOG.info("Assembler: dkList=" + dkList);
        //LOG.info("Assembler: dkList.size=" + dkList.size());
        // 4. Budujemy DmsDocumentOut (tak jak DS/DZ)
        DmsDocumentOut out = new DmsDocumentOut();
        out.setReportNumber(nrRaportu);
        out.setReportNumberPos(nrRaportu);
        out.setNrRep(ko.getNrRep());
        out.setDataOtwarcia(stripTime(ko.getMetadata().getDate()));
        out.setDataZamkniecia(stripTime(kz.getMetadata().getDate()));
       //LOG.info("Assembler: building report " + nrRaportu);
        //LOG.info("Assembler: dkList.size=" + dkList.size());
        out.setRapKasa(new ArrayList<>());
        Map<String,Integer> counters = new HashMap<>();
        counters.put("KWD",0); counters.put("KPD",0); counters.put("DW",0);
        // 5. Dodajemy wszystkie DK jako ZAPIS_KB
        for (DmsParsedDocument dk : dkList) {        	
            String kier = dk.getKierunek();
            String kwoRk = dk.getKwotaRk();
            /*LOG.info("ASM: processing kwoRk='%s' identity='%s' file='%s' dataWystawienia='%s'",
                    System.identityHashCode(dk), kwoRk, dk.getSourceFileName(), dk.getDataWystawienia());*/
            if ((kier == null || kier.isBlank()) && dk.getRapKasa() != null && !dk.getRapKasa().isEmpty()) {
                kier = dk.getRapKasa().get(0).getKierunek(); // lub wybierz regułę wyboru pozycji
            }
           //LOG.info("Assembler: file=" +" | dk.getNrRKB= "+dk.getNrRKB()+ dk.getSourceFileName()+ " | dk.getDataWystawienia= " +dk.getDataWystawienia() + " | entryNr=" + dk.getReportNumber()+ " | amount=" + dk.getKwotaRk()+" | kierunek=" + kier);
            DmsOutputPosition pos = new DmsOutputPosition();
            Contractor posContractor = dk.getContractor();
            pos.setContractor(posContractor);  
            pos.setDataWystawienia(stripTime(dk.getMetadata().getDate()));
            pos.setNrRKB(dk.getNrRKB());
            //LOG.info("Assembler: | dk.getNrRKB="+dk.getNrRKB()+" | ko.getNrRep="+ko.getNrRep());
            //pos.setLp(dk.getRapKasa().get(0).getLp());
            //pos.setDataWystawienia(dk.getDataWystawienia());
            pos.setReportNumber(dk.getReportNumber()); // 01/00001/2026
            pos.setReportNumberPos(dk.getReportNumberPos());
            pos.setKwotaRk(dk.getRapKasa().get(0).getKwotaRk());
            pos.setKierunek(kier);
            pos.setOpis(dk.getAdditionalDescription());
            pos.setReportDate(dk.getReportDate());
            pos.setNrDokumentu(dk.getRapKasa().get(0).getNrDokumentu());
            pos.setSymbolKPW(mapSymbolDokumentuZapisu(dk.getDowodNumber()));
            String code = null;
            String mapped = safe(mapDowodNumber(dk.getDowodNumber(),ko.getNrRep()));
            code = mapped.split("/")[0].trim();
            String typeKey = null;
            if ("KWD".equalsIgnoreCase(code)) typeKey = "KWD";
            else if ("KPD".equalsIgnoreCase(code)) typeKey = "KPD";
            else if ("DW".equalsIgnoreCase(code)) typeKey = "DW";
            String suffix = "";
            LOG.info(String.format("Assembler BEFORE INC: mapped='%s ' raw='%s ' typeKey='%s ' counters='%s '", mapped, dk.getDowodNumber(), typeKey, counters));
            suffix = nextCounter(counters, typeKey, 3);
            LOG.info(String.format("Assembler  NrRep()='%s ' suffix='%s '", ko.getNrRep(), suffix));
            String finalMapped = replaceSuffixWithCounter(mapped, suffix);
            pos.setDowodNumber(finalMapped);
            pos.setLp(suffix);
            //LOG.info("Assembler: list pos=" + pos);
            //LOG.info("Assembler: file=" + dk.getSourceFileName()+ " | dk.getDataWystawienia= " +dk.getDataWystawienia() + " | entryNr=" + dk.getReportNumber()+ " | amount=" + dk.getKwotaRk()+" | kierunek=" + kier);
            //LOG.info("Assembler: pos nrDokumentu='%s', c='%s'" + dk.getRapKasa().get(0).getNrDokumentu(), c.getName1());
            out.getRapKasa().add(pos);
        }
        //LOG.info("Assembler: out.getRapKasa().size=" + (out.getRapKasa()==null?0:out.getRapKasa().size()));
        return out;
    }
    private String stripTime(String dateTime) {
        if (dateTime == null) return null;
        int space = dateTime.indexOf(' ');
        return space > 0 ? dateTime.substring(0, space) : dateTime;
    }
    private static String mapDowodNumber(String src, String nrRKB) {
        if (src == null) return null;
        src = src.trim();
        // jeśli już jest w docelowym formacie, zwróć bez zmian
        if (src.matches("^(KP|KW|DW)/\\d+/\\d{6}/\\d{4}$")) {
            return src;
        }

        String[] parts = src.split("/");
        if (parts.length < 3) return src; // nieznany format -> zwróć oryginał

        String code = parts[0].trim();
        String rawNumber = parts[1].replaceAll("\\D", ""); // zostaw tylko cyfry
        String year = parts[2].trim();

        if (rawNumber.isEmpty()) return src;

        String type;
        switch (code) {
            case "01": type = "KPD"; break;
            case "02": type = "KWD"; break;
            case "03": type = "DW"; break;
            default: return src; // nieznany kod -> zwróć oryginał
        }

        int num;
        try {
            num = Integer.parseInt(rawNumber);
        } catch (NumberFormatException e) {
            return src;
        }

        String padded = String.format("%06d", num);

        // --- normalizacja nrRKB do 3 cyfr (np. "1" -> "001") ---
        String prefix = "";
        if (nrRKB != null) {
            String nr = nrRKB.trim().replaceAll("\\D", "");
            if (!nr.isEmpty()) {
                try {
                    int nrInt = Integer.parseInt(nr);
                    prefix = String.format("%03d", nrInt); // zawsze 3 cyfry
                } catch (NumberFormatException ignored) {
                    // jeśli nie da się sparsować, użyj surowego nr (bez niecyfr)
                    prefix = nr;
                }
            }
        }

        if (!prefix.isEmpty()) {
            int replaceLen = Math.min(prefix.length(), padded.length());
            padded = prefix + padded.substring(replaceLen);
        }

        return String.format("%s/1/%s/%s", type, padded, year);
    }

    private static String mapSymbolDokumentuZapisu(String dowodNumber) {
        if (dowodNumber == null) return null;
        String s = dowodNumber.trim();
        if (s.isEmpty()) return null;

        // oczekiwany format: "NN/xxxxx/YYYY"
        String[] parts = s.split("/");
        if (parts.length < 1) return null;

        switch (parts[0].trim()) {
            case "01": return "KPD";
            case "02": return "KWD";
            case "03": return "DW";
            default:   return null; // nieznany kod -> nie ustawiamy
        }
    }
    private static String safe(String s) {
        return s == null ? "" : s;
    }
    private String nextCounter(Map<String,Integer> counters, String type, int width) {
        if (type == null) type = "";
        Integer cur = counters.get(type);
        if (cur == null) cur = 0;
        cur = cur + 1;
        counters.put(type, cur);
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

