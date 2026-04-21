package pl.edashi.converter.service;

import pl.edashi.dms.model.Contractor;
import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsOutputPosition;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.model.DmsRapKasa;
import pl.edashi.dms.model.Rozliczenie;
import pl.edashi.dms.xml.CashReportXmlBuilder;
import pl.edashi.common.util.MappingIdDocs;
import pl.edashi.dms.mapper.DmsToDmsMapper.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class CashReportAssembler {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CashReportAssembler.class);
    private final List<DmsParsedDocument> docs;

    public CashReportAssembler(List<DmsParsedDocument> docs) {
    	this.docs = docs == null ? Collections.emptyList() : docs;
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
                .filter(d -> "DK".equals(d.getDocumentType()))
                .filter(d -> nrRaportu.equals(d.getReportNumber()))
                .collect(Collectors.toList());
        /*docs.forEach(d -> LOG.info("DOCS LIST: identity={} file={} type={} dataWystawienia={}",
        	    System.identityHashCode(d), d.getSourceFileName(), d.getDocumentType(), d.getDataWystawienia()));*/

        //LOG.info("Assembler: dkList=" + dkList);
        //LOG.info("Assembler: dkList.size=" + dkList.size());
        // 4. Budujemy DmsDocumentOut (tak jak DS/DZ)
        DmsDocumentOut out = new DmsDocumentOut();
        out.setDocumentType(ko.getDocumentType());
        out.setReportNumber(nrRaportu);
        out.setReportNumberPos(nrRaportu);
        out.setNrRep(ko.getNrRep());
        out.setDataOtwarcia(stripTime(ko.getMetadata().getDate()));
        out.setDataZamkniecia(stripTime(kz.getMetadata().getDate()));
       //LOG.info("Assembler: building report " + nrRaportu);
        //LOG.info("Assembler: dkList.size=" + dkList.size());
        out.setRapKasa(new ArrayList<>());
        Map<String,Integer> counters = new HashMap<>();
        counters.put("KWD",0); counters.put("KPD",0);
        // 5. Dodajemy wszystkie DK jako ZAPIS_KB
        for (DmsParsedDocument dk : dkList) { 
        	List<DmsRapKasa> rapList = dk.getRapKasa();
            /*if (rapList == null || rapList.isEmpty()) {
                LOG.debug("Assembler: DK has no rapKasa entries: dowod=" + safe(dk.getDowodNumber()));
                continue;
            }*/
            for (DmsRapKasa rkEntry : rapList) {
            String kier = rkEntry.getKierunek();
            //String kwoRk = dk.getKwotaRk();
            /*LOG.info("ASM: processing kwoRk='%s' identity='%s' file='%s' dataWystawienia='%s'",
                    System.identityHashCode(dk), kwoRk, dk.getSourceFileName(), dk.getDataWystawienia());*/
            if ((kier == null || kier.isBlank()) && rkEntry != null) {
                kier = rkEntry.getKierunek();
            }
            
           //LOG.info("Assembler: file=" +" | dk.getNrRKB= "+dk.getNrRKB()+ dk.getSourceFileName()+ " | dk.getDataWystawienia= " +dk.getDataWystawienia() + " | entryNr=" + dk.getReportNumber()+ " | amount=" + dk.getKwotaRk()+" | kierunek=" + kier);
            DmsOutputPosition pos = new DmsOutputPosition();
            Contractor posContractor = dk.getContractor();
            pos.setContractor(posContractor);  
            pos.setDataWystawienia(stripTime(dk.getMetadata().getDate()));
            // Nr rejestru raportu (np. 038) — z KO/DK dokumentu, NIE z atrybutu nr przy <numer> w typ 48
            // (ten atrybut to często lp/slot w obrębie dowodu → 009 zamiast 038 w NUMER_ZAPISU).
            pos.setNrRKB(dk.getNrRKB());
            //LOG.info("Assembler: | dk.getNrRKB="+dk.getNrRKB()+" | ko.getNrRep="+ko.getNrRep());
            //pos.setLp(dk.getRapKasa().get(0).getLp());
            //pos.setDataWystawienia(dk.getDataWystawienia());
            pos.setReportNumber(dk.getReportNumber()); // 01/00001/2026
            pos.setReportNumberPos(dk.getReportNumberPos());
            pos.setKwotaRk(rkEntry.getKwotaRk());
            pos.setKierunek(kier);
            pos.setOpis(dk.getAdditionalDescription());
            pos.setOpis1(rkEntry.getOpis1());
            pos.setReportDate(dk.getReportDate());
            pos.setNrDokumentu(rkEntry.getNrDokumentu());
            String rawDowod = rkEntry.getDowodNumber();
            if (rawDowod == null || rawDowod.isBlank()) {
                rawDowod = dk.getDowodNumber();
            }
            String symKpw = mapSymbolDokumentuZapisu(rawDowod);
            if (rkEntry.isInvertKpdKwdSymbol()) {
                symKpw = invertKpdKwdSymbol(symKpw);
            }
            pos.setSymbolKPW(symKpw);
            //pos.setDocKey(dk.getDocKey());
            //LOG.info("Cash AsMbl dk.getDocKey="+dk.getDocKey());
            String code = null;
            String mapped = safe(mapDowodNumber(rawDowod, ko.getNrRep()));
            if (rkEntry.isInvertKpdKwdSymbol()) {
                mapped = swapMappedKpdKwdPrefix(mapped);
            }
            code = mapped.split("/")[0].trim();
            String typeKey = null;
            if ("KWD".equalsIgnoreCase(code)) typeKey = "KWD";
            else if ("KPD".equalsIgnoreCase(code)) typeKey = "KPD";
            String suffix = "";
            suffix = nextCounter(counters, typeKey, 3);
            String finalMapped = replaceSuffixWithCounter(mapped, suffix);
            //LOG.info(String.format("Assembler BEFORE: docKey=%s mapped='%s ' raw='%s ' opis1='%s ' typeKey='%s ' finalMapped='%s ' counters='%s ' NrRep()='%s ' suffix='%s '", dk.getDocKey(), mapped, dk.getDowodNumber(), dk.getOpis1(),typeKey, finalMapped, counters,ko.getNrRep(), suffix));
            pos.setDowodNumber(finalMapped);
            pos.setLp(suffix);
            String numerFa = finalMapped;
	            String podmiot = dk.getPodmiot();
	            String nrLewyDoc = MappingIdDocs.generateCandidate(podmiot, "D",pos.getNrDokumentu(), 36);
	            String fullKey = MappingIdDocs.buildFullKey(podmiot, numerFa);
	            String hash = MappingIdDocs.shortHashFromFullKey(fullKey, 6);
	            String docKey = MappingIdDocs.generateDocId(podmiot, "K" ,numerFa, 36);
	            
	            pos.setDocKey(docKey);
	            //out.setDocKey(docKey);
	             pos.setFullKey(fullKey);
	             //out.setFullKey(fullKey);
	             LOG.info(String.format("Assembler AFTER fullKey=%s docKey=%s", fullKey, docKey));
            //LOG.info("Assembler: list pos=" + pos);
            //LOG.info("Assembler: file=" + dk.getSourceFileName()+ " | dk.getDataWystawienia= " +dk.getDataWystawienia() + " | entryNr=" + dk.getReportNumber()+ " | amount=" + dk.getKwotaRk()+" | kierunek=" + kier);
            //LOG.info("Assembler: pos nrDokumentu='%s', c='%s'" + dk.getRapKasa().get(0).getNrDokumentu(), c.getName1());
            out.getRapKasa().add(pos);
            Rozliczenie r = new Rozliczenie();
            r.setKwotaRk(pos.getKwotaRk());
            r.setDataDokumentu(pos.getDataWystawienia());
            r.setNumerLewegoDokumentu(pos.getDowodNumber());
            r.setRowIdLewego(pos.getDocKey());
            r.setNumerPrawegoDokumentu(pos.getNrDokumentu());
            r.setRowIdPrawego(nrLewyDoc);
            r.setTypLewegoDokumentu("zapis");
            r.setTypPrawegoDokumentu("zdarzenie");
            r.setIdZrodla("");
            out.getRozliczenia().add(r);

        }
        }
        //LOG.info("Assembler: out.getRapKasa().size=" + (out.getRapKasa()==null?0:out.getRapKasa().size()));
        return out;
    }
    private String stripTime(String dateTime) {
        if (dateTime == null) return null;
        int space = dateTime.indexOf(' ');
        return space > 0 ? dateTime.substring(0, space) : dateTime;
    }
    /** Gotówka: tylko 01/02. Karta (RD) używa {@link #mapCardDowodFrom03(String, String, boolean)} — sam kod 03 nie rozróżnia DW od DWP. */
    public static String mapDowodNumber(String src, String nrRKB) {
        if (src == null) return null;
        src = src.trim();
        if (src.matches("^(KPD|KWD)/\\d+/\\d{6}/\\d{4}$")) {
            return src;
        }
        if (src.matches("^(KP|KW)/\\d+/\\d{6}/\\d{4}$")) {
            return src;
        }

        String[] parts = src.split("/");
        if (parts.length < 3) return src;

        String code = parts[0].trim();
        String rawNumber = parts[1].replaceAll("\\D", "");
        String year = parts[2].trim();

        if (rawNumber.isEmpty()) return src;

        String type;
        switch (code) {
            case "01": type = "KPD"; break;
            case "02": type = "KWD"; break;
            default: return src;
        }

        int num;
        try {
            num = Integer.parseInt(rawNumber);
        } catch (NumberFormatException e) {
            return src;
        }

        String padded = String.format("%06d", num);

        String prefix = "";
        if (nrRKB != null) {
            String nr = nrRKB.trim().replaceAll("\\D", "");
            if (!nr.isEmpty()) {
                try {
                    int nrInt = Integer.parseInt(nr);
                    prefix = String.format("%03d", nrInt);
                } catch (NumberFormatException ignored) {
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

    /**
     * Raport kartowy: w DMS jest tylko {@code 03/…}; symbol DW (rozchód) vs DWP (przychód) wynika z kierunku (Z/W, ujemna kwota w parserze).
     */
    public static String mapCardDowodFrom03(String src, String nrRKB, boolean przychodDwp) {
        if (src == null) return null;
        src = src.trim();
        String head = przychodDwp ? "DWP" : "DW";
        if (src.matches("^(DW|DWP)/\\d+/\\d{6}/\\d{4}$")) {
            return src;
        }
        String[] parts = src.split("/");
        if (parts.length < 3) return src;
        if (!"03".equals(parts[0].trim())) {
            return src;
        }
        String rawNumber = parts[1].replaceAll("\\D", "");
        String year = parts[2].trim();
        if (rawNumber.isEmpty()) return src;
        int num;
        try {
            num = Integer.parseInt(rawNumber);
        } catch (NumberFormatException e) {
            return src;
        }
        String padded = String.format("%06d", num);
        String prefix = "";
        if (nrRKB != null) {
            String nr = nrRKB.trim().replaceAll("\\D", "");
            if (!nr.isEmpty()) {
                try {
                    int nrInt = Integer.parseInt(nr);
                    prefix = String.format("%03d", nrInt);
                } catch (NumberFormatException ignored) {
                    prefix = nr;
                }
            }
        }
        if (!prefix.isEmpty()) {
            int replaceLen = Math.min(prefix.length(), padded.length());
            padded = prefix + padded.substring(replaceLen);
        }
        return String.format("%s/1/%s/%s", head, padded, year);
    }

    /** {@code true} gdy kierunek to wpływ / przychód (np. Z na typ 48 po parsowaniu). */
    public static boolean isPrzychodKierunek(String kierunek) {
        if (kierunek == null || kierunek.isBlank()) {
            return false;
        }
        String t = kierunek.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("przych");
    }

    public static String symbolCardFromKierunek(String kierunek) {
        return isPrzychodKierunek(kierunek) ? "DWP" : "DW";
    }

    /** RD: symbol z kierunku (Z/W na typ 48), nie z segmentu 03/04. */
    public static String symbolCardDokumentuZapisuForRapLine(String kierunek) {
        return symbolCardFromKierunek(kierunek);
    }

    public static String mappedCardDowodForRapLine(String rawDowod, String nrRKB, String kierunek) {
        return mapCardDowodFrom03(rawDowod, nrRKB, isPrzychodKierunek(kierunek));
    }

    public static String mapSymbolDokumentuZapisu(String dowodNumber) {
        if (dowodNumber == null) return null;
        String s = dowodNumber.trim();
        if (s.isEmpty()) return null;

        String[] parts = s.split("/");
        if (parts.length < 1) return null;

        switch (parts[0].trim()) {
            case "01": return "KPD";
            case "02": return "KWD";
            default:   return null;
        }
    }

    /**
     * Używane też w {@link pl.edashi.dms.mapper.DmsToDmsMapper} dla pojedynczej pozycji raportu kasowego.
     */
    public static String symbolDokumentuZapisuForRapLine(String rawDowodFromXml, boolean invertKpdKwd) {
        String sym = mapSymbolDokumentuZapisu(rawDowodFromXml);
        if (invertKpdKwd) {
            sym = invertKpdKwdSymbol(sym);
        }
        return sym;
    }

    /** Wynik {@link #mapDowodNumber(String, String)} z opcjonalną zamianą KPD↔KWD (ujemna kwota w parserze). */
    public static String mappedDowodForRapLine(String src, String nrRKB, boolean invertKpdKwd) {
        String mapped = safe(mapDowodNumber(src, nrRKB));
        if (invertKpdKwd) {
            mapped = swapMappedKpdKwdPrefix(mapped);
        }
        return mapped;
    }

    /** Po ujemnej kwocie: KPD↔KWD (gotówka). */
    private static String invertKpdKwdSymbol(String sym) {
        if (sym == null) return null;
        String t = sym.trim();
        if ("KWD".equalsIgnoreCase(t)) return "KPD";
        if ("KPD".equalsIgnoreCase(t)) return "KWD";
        return sym;
    }

    /** KWD/…↔KPD/… */
    private static String swapMappedKpdKwdPrefix(String mapped) {
        if (mapped == null || mapped.isBlank()) {
            return mapped;
        }
        int slash = mapped.indexOf('/');
        if (slash <= 0) {
            return mapped;
        }
        String head = mapped.substring(0, slash).trim();
        String rest = mapped.substring(slash);
        if ("KWD".equalsIgnoreCase(head)) {
            return "KPD" + rest;
        }
        if ("KPD".equalsIgnoreCase(head)) {
            return "KWD" + rest;
        }
        return mapped;
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

