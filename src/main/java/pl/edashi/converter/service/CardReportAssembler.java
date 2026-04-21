package pl.edashi.converter.service;
import pl.edashi.common.util.MappingIdDocs;
import pl.edashi.dms.model.Contractor;
import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsOutputPosition;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.model.DmsRapKasa;
import pl.edashi.dms.model.Rozliczenie;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CardReportAssembler {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CardReportAssembler.class);
    private final List<DmsParsedDocument> docs;

    public CardReportAssembler(List<DmsParsedDocument> docs) {
        this.docs = docs == null ? Collections.emptyList() : docs;
    }
    public DmsDocumentOut buildSingleReport(String nrRaportu) {

        // 1. RO – otwarcie raportu kartowego
        DmsParsedDocument ro = docs.stream()
                .filter(d -> "RO".equalsIgnoreCase(d.getDocumentType()))
                .filter(d -> nrRaportu.equals(d.getReportNumber()))
                .findFirst()
                .orElse(null);

        // 2. RZ – zamknięcie raportu kartowego
        DmsParsedDocument rz = docs.stream()
                .filter(d -> "RZ".equalsIgnoreCase(d.getDocumentType()))
                .filter(d -> nrRaportu.equals(d.getReportNumber()))
                .findFirst()
                .orElse(null);

        // raport musi mieć RO + RZ
        if (ro == null || rz == null) {
            LOG.debug("CardAssembler: incomplete report '%s ' RO='%s ' RZ='%s '", nrRaportu, ro != null, rz != null);
            return null;
        }

        // 3. RD – wszystkie zapisy kartowe do tego raportu
        List<DmsParsedDocument> rdList = docs.stream()
                .filter(d -> {
                    String t = d.getDocumentType();
                    return "RD".equalsIgnoreCase(t) || "DWP".equalsIgnoreCase(t);
                })
                .filter(d -> nrRaportu.equals(d.getReportNumber()))
                .collect(Collectors.toList());

        DmsDocumentOut out = new DmsDocumentOut();
        out.setDocumentType(ro.getDocumentType());
        out.setReportNumber(nrRaportu);
        out.setReportNumberPos(nrRaportu);
        out.setNrRep(ro.getNrRep());
        out.setDataOtwarcia(stripTime(ro.getMetadata().getDate()));
        out.setDataZamkniecia(stripTime(rz.getMetadata().getDate()));
        out.setRapKasa(new ArrayList<>());

        // liczniki dla numeracji pozycji (możesz dostosować klucze jeśli potrzebne)
        Map<String,Integer> counters = new HashMap<>();
        counters.put("DW", 0);
        counters.put("DWP", 0);

        // 5. Dodajemy wszystkie RD jako pozycje raportu kartowego
        for (DmsParsedDocument rd : rdList) {
        	List<DmsRapKasa> radList = rd.getRapKasa();
        	///
        	///
        	///
        	///
        	///            
            //String kwoRk = dk.getKwotaRk();
            /*LOG.info("ASM: processing kwoRk='%s' identity='%s' file='%s' dataWystawienia='%s'",
                    System.identityHashCode(dk), kwoRk, dk.getSourceFileName(), dk.getDataWystawienia());*/
            for (DmsRapKasa rdEntry : radList) {
                String kier = rdEntry.getKierunek();
                if ((kier == null || kier.isBlank()) && rdEntry != null) {
                    kier = rdEntry.getKierunek();
                }

            DmsOutputPosition pos = new DmsOutputPosition();
            Contractor posContractor = rd.getContractor();
            pos.setContractor(posContractor);
            pos.setDataWystawienia(stripTime(rd.getMetadata().getDate()));
            pos.setNrRKB(rd.getNrRKB());
            pos.setReportNumber(rd.getReportNumber());
            pos.setReportNumberPos(rd.getReportNumberPos());
            /// dla RD bierzemy kwotę z rapKasa[0] jeśli jest
            ///String kwotaRk = rd.getRapKasa() != null && !rd.getRapKasa().isEmpty()
            ///        ? rd.getRapKasa().get(0).getKwotaRk()
            ///        : rd.getKwotaRk(); // fallback
            pos.setKwotaRk(rdEntry.getKwotaRk());
            pos.setKierunek(kier);
            pos.setOpis(rd.getAdditionalDescription());
            pos.setOpis1(rdEntry.getOpis1());
            pos.setReportDate(rd.getReportDate());
            ///pos.setNrDokumentu(rd.getRapKasa() != null && !rd.getRapKasa().isEmpty()
            ///        ? rd.getRapKasa().get(0).getNrDokumentu()
            ///       : rd.getNrDokumentu());
            pos.setNrDokumentu(rdEntry.getNrDokumentu());
            String rawDowod = rdEntry.getDowodNumber();
            if (rawDowod == null || rawDowod.isBlank()) {
                rawDowod = rd.getDowodNumber();
            }
            pos.setSymbolKPW(CashReportAssembler.symbolCardDokumentuZapisuForRapLine(kier));

            String mapped = safe(CashReportAssembler.mappedCardDowodForRapLine(
                    rawDowod, ro.getNrRep(), kier));
            String code = mapped.split("/")[0].trim();

            String typeKey;
            if ("DWP".equalsIgnoreCase(code)) {
                typeKey = "DWP";
            } else if ("DW".equalsIgnoreCase(code)) {
                typeKey = "DW";
            } else {
                typeKey = "DW";
            }

            String suffix = nextCounter(counters, typeKey, 3);
            String finalMapped = replaceSuffixWithCounter(mapped, suffix);

            pos.setDowodNumber(finalMapped);
            pos.setLp(suffix);
            String numerFa = finalMapped;
            String podmiot = rd.getPodmiot();
            String nrLewyDoc = MappingIdDocs.generateCandidate(podmiot, "D",pos.getNrDokumentu(), 36);
            String fullKey = MappingIdDocs.buildFullKey(podmiot, numerFa);
            String hash = MappingIdDocs.shortHashFromFullKey(fullKey, 6);
            String docKey = MappingIdDocs.generateDocId(podmiot, "K" ,numerFa, 36);
            pos.setDocKey(docKey);
             pos.setFullKey(fullKey);
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
            //LOG.debug(String.format("CardAssembler added pos: typeKey='%s' mapped='%s' suffix='%s' finalMapped='%s' counters='%s'", typeKey, mapped, suffix, finalMapped, counters));
        }
        }
        return out;
    }

    // --- pomocnicze metody ---

    private String stripTime(String dateTime) {
        if (dateTime == null) return null;
        int space = dateTime.indexOf(' ');
        return space > 0 ? dateTime.substring(0, space) : dateTime;
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
        return String.format("%0" + width + "d", cur);
    }

    private String replaceSuffixWithCounter(String mappedDowodNumber, String counterSuffix) {
        if (mappedDowodNumber == null || counterSuffix == null) return mappedDowodNumber;
        String s = mappedDowodNumber.trim();
        String[] parts = s.split("/");
        if (parts.length < 4) return mappedDowodNumber;
        String type = parts[0].trim();
        String one = parts[1].trim();
        String six = parts[2].trim();
        String year = parts[3].trim();
        if (six.length() != 6) {
            String digits = six.replaceAll("\\D", "");
            if (digits.length() > 6) digits = digits.substring(digits.length() - 6);
            six = String.format("%06d", digits.isEmpty() ? 0 : Integer.parseInt(digits));
        }
        String prefix = six.substring(0, 3);
        String newSix = prefix + String.format("%03d", Integer.parseInt(counterSuffix));
        return String.format("%s/%s/%s/%s", type, one, newSix, year);
    }
}

