package pl.edashi.converter.service;

import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsOutputPosition;
import pl.edashi.dms.model.DmsParsedDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CashReportAssembler {

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
            return null;
        }

        // 3. DK – wszystkie zapisy KP/KW do tego raportu
        List<DmsParsedDocument> dkList = docs.stream()
                .filter(d -> "DK".equals(d.getDocumentType()))
                .filter(d -> nrRaportu.equals(d.getReportNumber()))
                .collect(Collectors.toList());

        // 4. Budujemy DmsDocumentOut (tak jak DS/DZ)
        DmsDocumentOut out = new DmsDocumentOut();
        out.setNrRKB(nrRaportu);
        out.setDataOtwarcia(ko.getDataOtwarcia());
        out.setDataZamkniecia(kz.getDataZamkniecia());
        out.setRapKasa(new ArrayList<>());

        // 5. Dodajemy wszystkie DK jako ZAPIS_KB
        for (DmsParsedDocument dk : dkList) {
            DmsOutputPosition pos = new DmsOutputPosition();
            pos.setNrRKB(dk.getReportNumber()); // 01/00001/2026
            pos.setKwotaRk(dk.getKwotaRk());
            pos.setKierunek(dk.getKierunek());
            pos.setOpis(dk.getAdditionalDescription());
            pos.setReportDate(dk.getReportDate());
            out.getRapKasa().add(pos);
        }

        return out;
    }
}

