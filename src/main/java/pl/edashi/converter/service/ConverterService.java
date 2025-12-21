package pl.edashi.converter.service;
import pl.edashi.common.logging.AppLogger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import pl.edashi.converter.model.*;
import pl.edashi.converter.repository.DocumentRepository;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.StringReader;
import java.io.StringWriter;

import org.xml.sax.InputSource;

public class ConverterService {

    private final DocumentRepository repository;
    private final XmlStructureComparator structureComparator;
    private final AppLogger log = new AppLogger("CONVERTER");

    public ConverterService(DocumentRepository repository) {
        this.repository = repository;
        this.structureComparator = new XmlStructureComparator();
    }

    public ConversionResult processSingleDocument(String xml, String sourceFile) throws Exception {
        // 1. parsowanie
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));

        Element root = doc.getDocumentElement(); // <DMS ...>

        String genDocId = root.getAttribute("gen_doc_id");
        String id = root.getAttribute("id");
        String trans = root.getAttribute("trans");
        Element daty = (Element) doc.getElementsByTagName("daty").item(0);

        String date = daty.getAttribute("data");
        String dateSale = daty.getAttribute("data_sprzed");
        String dateApproval = daty.getAttribute("data_zatw");
        DocumentMetadata metadata = new DocumentMetadata(genDocId, id, trans, sourceFile,date,
                dateSale,
                dateApproval);

        // 2. budowa struktury
        DocumentStructure structure = structureComparator.buildStructure(doc);

        // 3. sprawdzenie, czy mamy już dokument o tym gen_doc_id
        DocumentStructure existing = repository.findStructureByGenDocId(genDocId);

        if (existing == null) {
            // pierwsze wystąpienie tego dokumentu → zapisujemy i mapujemy
            repository.save(genDocId, metadata, structure);

            String convertedXml = mapToTargetXml(doc, metadata);

            return new ConversionResult(
                    metadata,
                    ConflictStatus.OK,
                    convertedXml,
                    "Duplicate structure, processed normally."
            );
        } else {
            // istnieje już dokument o tym ID → porównujemy strukturę
            boolean same = structureComparator.areStructuresEqual(existing, structure);

            if (same) {
                String convertedXml = mapToTargetXml(doc, metadata);

                return new ConversionResult(
                        metadata,
                        ConflictStatus.OK,
                        convertedXml,
                        "Duplicate structure, processed normally."
                );
            } else {
                // konflikt → nie konwertujemy, tylko oznaczamy
                // (tu możesz dodać logikę zrzutu do katalogu buffer/)
                return new ConversionResult(
                        metadata,
                        ConflictStatus.CONFLICT,
                        null,
                        "Structure conflict for gen_doc_id=" + genDocId
                );
            }
        }
    }

    // Tu robisz docelowe mapowanie do swojego standardu XML (np. payroll / inne)
    private String mapToTargetXml(Document sourceDoc, DocumentMetadata meta) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document target = builder.newDocument();

        Element root = target.createElement("TargetDocument");
        target.appendChild(root);
        
        // --- META ---
        Element idEl = target.createElement("GenDocId");
        idEl.setTextContent(meta.getGenDocId());
        root.appendChild(idEl);

        Element typeEl = target.createElement("SourceType");
        typeEl.setTextContent(meta.getId());
        root.appendChild(typeEl);

     // ============================================================
        // 1. MAPOWANIE DOKUMENTU typ="03" (materiały handlowe)
        // ============================================================

        NodeList docs03 = sourceDoc.getElementsByTagName("document");
        Element materialValues = target.createElement("MaterialValues");

        for (int i = 0; i < docs03.getLength(); i++) {
            Element docEl = (Element) docs03.item(i);

            if ("03".equals(docEl.getAttribute("typ"))) {

                Element dane = (Element) docEl.getElementsByTagName("dane").item(0);

                // <numer kod_dok="091" nr="4071" rok="2025">091/04071/2025</numer>
                Element numer = (Element) dane.getElementsByTagName("numer").item(0);

                String kodDok = numer.getAttribute("kod_dok");
                String nr = numer.getAttribute("nr");
                String rok = numer.getAttribute("rok");
                String fullNumber = numer.getTextContent();

                // <wartosci netto_sprzed="101.63" netto_zakup="66.77" upust_dlr="0.00"/>
                Element wartosci = (Element) dane.getElementsByTagName("wartosci").item(0);

                String nettoSprzed = wartosci.getAttribute("netto_sprzed");
                String nettoZakup = wartosci.getAttribute("netto_zakup");
                String upust = wartosci.getAttribute("upust_dlr");

                // --- budujemy XML ---
                materialValues.appendChild(makeEl(target, "DocumentNumber", fullNumber));
                materialValues.appendChild(makeEl(target, "Code", kodDok));
                materialValues.appendChild(makeEl(target, "Year", rok));
                materialValues.appendChild(makeEl(target, "NetSale", nettoSprzed));
                materialValues.appendChild(makeEl(target, "NetPurchase", nettoZakup));
                materialValues.appendChild(makeEl(target, "Discount", upust));

                break; // tylko jeden dokument typ 03
            }
        }

        root.appendChild(materialValues);

        // ============================================================
        // 2. MAPOWANIE DOKUMENTU typ="94" (fiskalizacja)
        // ============================================================

        Element fiscal = target.createElement("Fiscalization");

        for (int i = 0; i < docs03.getLength(); i++) {
            Element docEl = (Element) docs03.item(i);

            if ("94".equals(docEl.getAttribute("typ"))) {

                Element dane = (Element) docEl.getElementsByTagName("dane").item(0);

                // <numer nr="244">EAH1901348177/244</numer>
                Element numer = (Element) dane.getElementsByTagName("numer").item(0);
                String nr = numer.getAttribute("nr");
                String fullFiscalNumber = numer.getTextContent();

                // <daty data="2025-12-09"/>
                Element daty = (Element) dane.getElementsByTagName("daty").item(0);
                String data = daty.getAttribute("data");

                // <wartosci brutto="150.00"/>
                Element wartosci = (Element) dane.getElementsByTagName("wartosci").item(0);
                String brutto = wartosci.getAttribute("brutto");

                // <rozszerzone nr="EAH1901348177"/>
                Element rozszerzone = (Element) dane.getElementsByTagName("rozszerzone").item(0);
                String deviceNumber = rozszerzone.getAttribute("nr");

                // --- budujemy XML ---
                fiscal.appendChild(makeEl(target, "FiscalNumber", fullFiscalNumber));
                fiscal.appendChild(makeEl(target, "ShortNumber", nr));
                fiscal.appendChild(makeEl(target, "Date", data));
                fiscal.appendChild(makeEl(target, "Gross", brutto));
                fiscal.appendChild(makeEl(target, "DeviceNumber", deviceNumber));

                break;
            }
        }

        root.appendChild(fiscal);
        
     // ============================================================
     // 3. MAPOWANIE DOKUMENTU typ="06" (VAT)
     // ============================================================

     Element vatEl = target.createElement("VAT");

     for (int i = 0; i < docs03.getLength(); i++) {
         Element docEl = (Element) docs03.item(i);

         if ("06".equals(docEl.getAttribute("typ"))) {

             Element dane = (Element) docEl.getElementsByTagName("dane").item(0);

             // <dane lp="1" kod="02" stawka="23.00">
             String rate = dane.getAttribute("stawka");

             // <wartosci podstawa="121.95" vat="28.05"/>
             Element wartosci = (Element) dane.getElementsByTagName("wartosci").item(0);
             String base = wartosci.getAttribute("podstawa");
             String vatAmount = wartosci.getAttribute("vat");

             vatEl.appendChild(makeEl(target, "Rate", rate));
             vatEl.appendChild(makeEl(target, "Base", base));
             vatEl.appendChild(makeEl(target, "VatAmount", vatAmount));

             break;
         }
     }

     root.appendChild(vatEl);
  // ============================================================
  // 4. MAPOWANIE DOKUMENTU typ="40" (Płatności) — wersja bezpieczna
  // ============================================================

  Element payments = target.createElement("Payments");

  for (int i = 0; i < docs03.getLength(); i++) {
      Element docEl = (Element) docs03.item(i);

      if ("40".equals(docEl.getAttribute("typ"))) {

          Element dane = (Element) docEl.getElementsByTagName("dane").item(0);

          // <klasyfikatory kod="61" wyr="N"/>
          Element klasyf = (Element) dane.getElementsByTagName("klasyfikatory").item(0);
          String code = klasyf != null ? klasyf.getAttribute("kod") : "";

          // <daty data="2025-12-09" termin="0"/>
          Element daty = (Element) dane.getElementsByTagName("daty").item(0);
          String date = daty != null ? daty.getAttribute("data") : "";

          // <wartosci kwota="150.00"/>
          Element wartosci = (Element) dane.getElementsByTagName("wartosci").item(0);
          String amount = wartosci != null ? wartosci.getAttribute("kwota") : "";

          // dokument 43 (opcjonalny)
          String bankNumber = "";
          String operationDate = "";

          Element rozszerzone = (Element) dane.getElementsByTagName("rozszerzone").item(0);
          if (rozszerzone != null) {

              NodeList docList = rozszerzone.getElementsByTagName("document");
              if (docList != null && docList.getLength() > 0) {

                  Element doc43 = (Element) docList.item(0);
                  if (doc43 != null) {

                      Element dane43 = (Element) doc43.getElementsByTagName("dane").item(0);
                      if (dane43 != null) {

                          Element numer43 = (Element) dane43.getElementsByTagName("numer").item(0);
                          if (numer43 != null) {
                              bankNumber = numer43.getAttribute("nr");
                          }

                          Element daty43 = (Element) dane43.getElementsByTagName("daty").item(0);
                          if (daty43 != null) {
                              operationDate = daty43.getAttribute("data_operacji");
                          }
                      }
                  }
              }
          }

          // budujemy XML
          Element payment = target.createElement("Payment");
          payment.appendChild(makeEl(target, "Code", code));
          payment.appendChild(makeEl(target, "Date", date));
          payment.appendChild(makeEl(target, "Amount", amount));
          payment.appendChild(makeEl(target, "BankNumber", bankNumber));
          payment.appendChild(makeEl(target, "OperationDate", operationDate));

          payments.appendChild(payment);

          break;
      }
  }

  root.appendChild(payments);

//============================================================
//5. MAPOWANIE DOKUMENTU typ="98" (Uwagi / Opisy)
//============================================================

Element notes = target.createElement("Notes");

for (int i = 0; i < docs03.getLength(); i++) {
   Element docEl = (Element) docs03.item(i);

   if ("98".equals(docEl.getAttribute("typ"))) {

       NodeList daneList = docEl.getElementsByTagName("dane");

       for (int d = 0; d < daneList.getLength(); d++) {
           Element dane = (Element) daneList.item(d);

           Element rozszerzone = (Element) dane.getElementsByTagName("rozszerzone").item(0);

           if (rozszerzone != null) {
               // opis1, opis2, opis3, opis4
               for (int k = 1; k <= 4; k++) {
                   String attr = "opis" + k;
                   if (rozszerzone.hasAttribute(attr)) {
                       String text = rozszerzone.getAttribute(attr);
                       notes.appendChild(makeEl(target, "Note", text));
                   }
               }
           }
       }

       break;
   }
}

root.appendChild(notes);


        // ============================================================
        // SERIALIZACJA DO STRING
        // ============================================================

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(target), new StreamResult(writer));

        return writer.toString();
    }

    // pomocnicza metoda do tworzenia elementów
    private Element makeEl(Document doc, String name, String value) {
        Element el = doc.createElement(name);
        el.setTextContent(value);
        return el;
    }
}
