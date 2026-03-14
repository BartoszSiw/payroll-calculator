package pl.edashi.dms.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.Rozliczenie;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Collections;
import java.util.List;

public class RozliczeniaXmlBuilder implements XmlSectionBuilder {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RozliczeniaXmlBuilder.class);
    private static final String NS = "http://www.comarch.pl/cdn/optima/offline";
    private final DmsDocumentOut docRoz;
    
    public RozliczeniaXmlBuilder(DmsDocumentOut docRoz) {
        if (docRoz == null) {
            throw new IllegalArgumentException("RozliczeniaXmlBuilder: docRoz is null");
        }
        this.docRoz = docRoz;
    }
    @Override
    public void build(Document docXml, Element root) {
        if (docXml == null || root == null) return;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            // Sekcja ROZLICZENIA (nagłówek sekcji)
            Element sekcja = docXml.createElementNS(NS, "ROZLICZENIA");
            root.appendChild(sekcja);

            sekcja.appendChild(makeCdata(docXml, "WERSJA", "2.00"));
            sekcja.appendChild(makeCdata(docXml, "BAZA_ZRD_ID", "KSIEG"));
            sekcja.appendChild(makeCdata(docXml, "BAZA_DOC_ID", "DMS_1"));

            List<Rozliczenie> list = docRoz.getRozliczenia();
            if (list == null) list = Collections.emptyList();

            for (Rozliczenie r : list) {
                try {
                    Element el = docXml.createElementNS(NS, "ROZLICZENIE");
                    sekcja.appendChild(el);

                    el.appendChild(makeCdata(docXml, "ID_ZRODLA", safe(r.getIdZrodla())));
                    el.appendChild(makeCdata(docXml, "NUMER_LEWEGO_DOKUMENTU", safe(r.getNumerLewegoDokumentu())));
                    el.appendChild(makeCdata(docXml, "ROWID_LEWEGO", safe(r.getRowIdLewego())));
                    el.appendChild(makeCdata(docXml, "NUMER_PRAWEGO_DOKUMENTU", safe(r.getNumerPrawegoDokumentu())));
                    el.appendChild(makeCdata(docXml, "ROWID_PRAWEGO", safe(r.getRowIdPrawego())));
                    el.appendChild(makeCdata(docXml, "DATA_DOKUMENTU", safe(r.getDataDokumentu())));
                    el.appendChild(makeCdata(docXml, "TYP_LEWEGO_DOKUMENTU", safe(r.getTypLewegoDokumentu())));
                    el.appendChild(makeCdata(docXml, "TYP_PRAWEGO_DOKUMENTU", safe(r.getTypPrawegoDokumentu())));
                    el.appendChild(makeCdata(docXml, "KWOTA", safe(r.getKwotaRk())));
                    el.appendChild(makeCdata(docXml, "OPIS", safe(r.getOpis())));
                    // dodatkowe pola, jeśli potrzebne, np. KONTRAHENT_NIP, KONTRAHENT_NAZWA
                    //el.appendChild(makeCdata(docXml, "KONTRAHENT_NIP", safe(r.getKontrahentNip())));
                    //el.appendChild(makeCdata(docXml, "KONTRAHENT_NAZWA", safe(r.getKontrahentNazwa())));
                } catch (Exception e) {
                    LOG.error("Error building ROZLICZENIE for report " + safe(docRoz.getReportNumber()) + " pos=" + safe(r.getSourcePositionLp()), e);
                }
            }
        } catch (Exception ex) {
            LOG.error("RozliczeniaXmlBuilder build failed for report " + safe(docRoz.getReportNumber()), ex);
        }
    }

    // pomocnicze metody (zakładam, że makeCdata istnieje globalnie; jeśli nie, zaimplementuj podobnie)
    private Element makeCdata(Document doc, String tag, String value) {
        Element el = doc.createElementNS(NS, tag);
        el.appendChild(doc.createCDATASection(value == null ? "" : value));
        return el;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}


