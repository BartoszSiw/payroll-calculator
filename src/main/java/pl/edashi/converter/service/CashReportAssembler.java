package pl.edashi.converter.service;

import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsOutputPosition;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.xml.CashReportXmlBuilder;

import java.util.ArrayList;
import java.util.List;
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
        	LOG.info("Assembler: incomplete report " + nrRaportu + " KO=" + (ko!=null) + " KZ=" + (kz != null ));
            return null;
        }

        // 3. DK – wszystkie zapisy KP/KW do tego raportu
        List<DmsParsedDocument> dkList = docs.stream()
                .filter(d -> "DK".equals(d.getDocumentType()) || "RD".equals(d.getDocumentType()))
                .filter(d -> nrRaportu.equals(d.getReportNumber()))
                .collect(Collectors.toList());
        LOG.info("Assembler: building report " + nrRaportu);
        LOG.info("Assembler: dkList.size=" + dkList.size());
        // 4. Budujemy DmsDocumentOut (tak jak DS/DZ)
        DmsDocumentOut out = new DmsDocumentOut();
        out.setReportNumber(nrRaportu);
        out.setReportNumberPos(nrRaportu);
        out.setNrRKB(ko.getNrRKB());
        out.setDataOtwarcia(stripTime(ko.getMetadata().getDate()));
        out.setDataZamkniecia(stripTime(kz.getMetadata().getDate()));
        LOG.info("Assembler: building report " + nrRaportu);
        LOG.info("Assembler: dkList.size=" + dkList.size());
        out.setRapKasa(new ArrayList<>());

        // 5. Dodajemy wszystkie DK jako ZAPIS_KB
        for (DmsParsedDocument dk : dkList) {
            String kier = dk.getKierunek();
            String kwoRk = dk.getKwotaRk();
            if ((kier == null || kier.isBlank()) && dk.getRapKasa() != null && !dk.getRapKasa().isEmpty()) {
                kier = dk.getRapKasa().get(0).getKierunek(); // lub wybierz regułę wyboru pozycji
            }
            LOG.info("Assembler: DK file=" + dk.getSourceFileName()+ " entryNr=" + dk.getReportNumber()+ " amount=" + dk.getKwotaRk()+" kierunek=" + kier);
            DmsOutputPosition pos = new DmsOutputPosition();
            pos.setReportNumber(dk.getReportNumber()); // 01/00001/2026
            pos.setReportNumberPos(dk.getReportNumberPos());
            pos.setKwotaRk(dk.getRapKasa().get(0).getKwotaRk());
            pos.setKierunek(kier);
            pos.setOpis(dk.getAdditionalDescription());
            pos.setReportDate(dk.getReportDate());
            pos.setDowodNumber(dk.getDowodNumber());
            out.getRapKasa().add(pos);
        }
        LOG.info("Assembler: out.getRapKasa().size=" + (out.getRapKasa()==null?0:out.getRapKasa().size()));
        return out;
    }
    private String stripTime(String dateTime) {
        if (dateTime == null) return null;
        int space = dateTime.indexOf(' ');
        return space > 0 ? dateTime.substring(0, space) : dateTime;
    }
}

