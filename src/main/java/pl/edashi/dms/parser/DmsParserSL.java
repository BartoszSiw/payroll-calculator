package pl.edashi.dms.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import pl.edashi.dms.model.DmsParsedContractor;
import pl.edashi.dms.model.DmsParsedContractorList;

public class DmsParserSL {
	public DmsParsedContractorList parse(Document doc, String fileName) {

        List<DmsParsedContractor> result = new ArrayList<>();

        NodeList docs = doc.getElementsByTagName("document");

        for (int i = 0; i < docs.getLength(); i++) {
            Element el = (Element) docs.item(i);

            if ("04".equals(el.getAttribute("typ")) && "055".equals(el.getAttribute("kod"))) {

                NodeList daneList = el.getElementsByTagName("dane");

                for (int j = 0; j < daneList.getLength(); j++) {
                    Element d = (Element) daneList.item(j);

                    DmsParsedContractor c = new DmsParsedContractor();
                    c.id = UUID.randomUUID().toString();

                    String wyr = d.getAttribute("wyr");
                    c.wyrRaw = wyr;
                    c.isCompany = "F".equals(wyr);
                    c.rodzaj = "odbiorca";


                    if (c.isCompany) {
                        // Firma
                        c.nazwa1 = d.getAttribute("nazwa1");
                        c.nazwa2 = d.getAttribute("nazwa2");
                        c.nazwa3 = d.getAttribute("nazwa3");
                        c.fullName = (c.nazwa1 + " " + c.nazwa2 + " " + c.nazwa3).trim();

                        c.nip = d.getAttribute("nip").trim();
                        c.regon = d.getAttribute("regon").trim();
                        c.pesel = "";
                        c.akronim = c.nip;//d.getAttribute("kod_klienta");
                    } else {
                        // Osoba fizyczna
                        String n1 = d.getAttribute("nazwa1");
                        String n2 = d.getAttribute("nazwa2");

                        c.fullName = (n1 + "_" + n2).trim();
                        c.nazwa1 = c.fullName;
                        c.nazwa2 = "";
                        c.nazwa3 = "";

                        c.nip = "";
                        c.regon = "";
                        c.pesel = d.getAttribute("pesel").trim();
                        c.akronim = c.fullName;
                    }

                    c.ulica = d.getAttribute("ulica").trim();
                    c.nrDomu = "";
                    c.nrLokalu = "";

                    c.kodPocztowy = d.getAttribute("kod_poczta").trim();
                    c.miasto = d.getAttribute("miejscowosc").trim();

                    String krajKod = d.getAttribute("kod_kraju");
                    c.kraj = "PL".equalsIgnoreCase(krajKod) ? "Polska" : krajKod;

                    result.add(c);
                }
            }
        }
     // ZWRACAMY OBIEKT, NIE LISTĘ 
        DmsParsedContractorList out = new DmsParsedContractorList(); 
        out.contractors = result; 
        out.fileName = fileName; 
        return out;
    }
}