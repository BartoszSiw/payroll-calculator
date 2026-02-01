package pl.edashi.converter.servlet;
import pl.edashi.common.logging.AppLogger;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pl.edashi.converter.repository.DocumentRepository;
import pl.edashi.converter.service.ConverterService;
import pl.edashi.dms.mapper.DmsToDmsMapper;
import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsParsedContractorList;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.model.DmsPosition;
import pl.edashi.dms.xml.DmsOfflinePurchaseBuilder;
import pl.edashi.dms.xml.DmsOfflineXmlBuilder;
import pl.edashi.dms.xml.RootXmlBuilder;
import pl.edashi.optima.builder.ContractorsXmlBuilder;
import pl.edashi.optima.mapper.ContractorMapper;
import pl.edashi.optima.model.OfflineContractor;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.jakarta.JakartaServletFileUpload;
import org.apache.commons.fileupload2.core.FileItem;

@WebServlet("/ConverterServlet")
public class ConverterServlet extends HttpServlet {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private ConverterService converterService;
	private final AppLogger log = new AppLogger("CONVERTER-SERVLET");
    @Override
    public void init() throws ServletException {
        converterService = new ConverterService(new DocumentRepository());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
    	log.info("1 Odebrano żądanie konwersji wielu plików XML");
        // ============================
        // 1. KONFIGURACJA UPLOADU
        // ============================
    	DiskFileItemFactory factory = DiskFileItemFactory.builder().get();
    JakartaServletFileUpload upload = new JakartaServletFileUpload(factory);

        // BRAK LIMITU LICZBY PLIKÓW
        upload.setFileCountMax(-1);

        // BRAK LIMITU ROZMIARU
        upload.setSizeMax(1024 * 1024 * 500); // 500 MB
        upload.setFileSizeMax(1024 * 1024 * 50); // 50 MB

        List<FileItem> items;
        try {
            items = upload.parseRequest(req);
            log.info("Upload: items count = " + (items == null ? 0 : items.size()));
            for (FileItem it : items) {
                //log.info(" - fieldName=" + it.getFieldName() + " name=" + it.getName() + " size=" + it.getSize());
            }

        } catch (Exception e) {
            throw new ServletException("Błąd parsowania uploadu: " + e.getMessage(), e);
        }
        List<String> results = new ArrayList<>();

        Path uploadDir = Paths.get("uploads");
        Files.createDirectories(uploadDir);
        // Tworzymy builder główny dla jednego pliku
        RootXmlBuilder root = new RootXmlBuilder();
        // nazwa finalnego pliku
        String outputName = "DMS_IMPORT_" + System.currentTimeMillis();
        // ============================
        // 2. PRZETWARZANIE PLIKÓW
        // ============================
        // List for analyzer
        List<DmsParsedDocument> allParsedDocs = new ArrayList<>();
        Set<String> filtrRejestry = new HashSet<>();
        String filtrOddzial = "01"; // domyślnie

        // 1) Odczyt pól formularza (rejestr, oddzial)
        for (FileItem item : items) {
            if (!item.isFormField()) continue;

            String name = item.getFieldName();
            String value = item.getString(StandardCharsets.UTF_8).trim();
            value = value == null ? "" : value.trim();
            
            if ("rejestr".equals(name)) {
                if (!value.isEmpty()) {
                	filtrRejestry.add(value.toUpperCase());
                    log.info(String.format("Selected rejestr value: %s", value.toUpperCase()));
                } else {
                    log.info("Selected rejestr value is empty (treated as all)");
                }
            } else if ("oddzial".equals(name)) {
            	filtrOddzial = value.isBlank() ? "01" : value;
                log.info(String.format("Selected oddzial='%s'", filtrOddzial));
            } else {
            	log.debug(String.format("Ignored form field: %s", name));
            }
        } // <- tutaj jest ważny nawias zamykający pętli odczytu pól

        // opcjonalnie: log całego zbioru rejestrów
        log.info(String.format("Collected rejestry: %s", filtrRejestry.isEmpty() ? "ALL" : filtrRejestry.toString()));

        // 2) Obsługa plików
        for (FileItem item : items) {
            if (item.isFormField()) continue; // pomijamy pola formularza
            if (!"xmlFile".equals(item.getFieldName())) continue;
            if (item.getSize() == 0) continue;

            String fileName = Paths.get(item.getName()).getFileName().toString();
            Path savedFile = uploadDir.resolve(fileName);

            try (InputStream input = item.getInputStream()) {
                Files.copy(input, savedFile, StandardCopyOption.REPLACE_EXISTING);
                log.info(String.format("Saved uploaded file: %s", savedFile.toString()));
            } catch (IOException e) {
                log.error("Failed to save uploaded file: " + fileName, e);
                results.add("Błąd zapisu pliku: " + fileName);
                continue;
            }
        
        // Wczytaj zawartość XML
        String xml = Files.readString(savedFile, StandardCharsets.UTF_8);
        log.info(String.format("fileName='%s'", fileName));
        try {
        	//log.info(outputName);
            // 1. Parsowanie dokumentu DMS (DS, KO, DK, SL WZ...)
            //DmsParsedDocument parsed = converterService.processSingleDocument(xml, fileName);
        	Object parsed = converterService.processSingleDocument(xml, fileName);
        	//log.info("parsed class = " + (parsed == null ? "null" : parsed.getClass().getName()));
        	log.info(String.format("Parsed object type: %s, file: %s",
        	        parsed.getClass().getSimpleName(), fileName));

        	if (parsed instanceof DmsParsedDocument d) {
        		String docRejestr = d.getDaneRejestr() != null ? d.getDaneRejestr().trim().toUpperCase() : "";
                String docOddzial = d.getOddzial() != null ? d.getOddzial().trim() : "";
                if (d.getDocumentType() != null) {
                    d.setDocumentType(d.getDocumentType().trim().toUpperCase());
                }

                log.info(String.format("filtrRejestru=%s filtrOddzial=%s docRejestr=%s docOddzial=%s",
                        filtrRejestry, filtrOddzial, docRejestr, docOddzial));
                // filtr rejestrów: jeśli zbiór niepusty i dokument nie pasuje → pomiń
                if (!filtrRejestry.isEmpty() && (docRejestr.isEmpty() || !filtrRejestry.contains(docRejestr))) {
                    log.info(String.format("Skipping file: %s — docRejestr='%s' not in filter %s",
                            fileName, docRejestr, filtrRejestry));
                    continue;
                }

                // filtr oddziału
                if (filtrOddzial != null && !filtrOddzial.isBlank() && !filtrOddzial.equals(docOddzial)) {
                    log.info(String.format("Skipping file: %s — docOddzial='%s' != filter '%s'",
                            fileName, docOddzial, filtrOddzial));
                    continue;
                }
        		/*String t = d.getDocumentType();
        		String docRejestr = d.getDaneRejestr() != null ? d.getDaneRejestr().trim().toUpperCase() : "";
        		String docOddzial = d.getOddzial() != null ? d.getOddzial().trim() : "";
        		if (t != null) {
        		    d.setDocumentType(t.trim().toUpperCase());
        		}
        		String tinfo = String.format("filtrRejestru='%s ' filtrOddzial='%s ' d.getOddzial()='%s'", filtrRejestry, filtrOddzial, d.getOddzial());
        		log.info(tinfo);
        		   // 🔥 FILTR REJESTRU — NAJLEPSZE MIEJSCE
        		if (!filtrRejestry.isEmpty()) {
        		    // jeśli dokument nie ma rejestru z wybranych → pomiń
        		    if (!filtrRejestry.contains(d.getDaneRejestr())) {
        		    	 log.info(String.format("1 Skipping file fileName='%s ' oddzial='%s ' filtrOddzial='%s '", fileName, d.getDaneRejestr(), filtrOddzial));
        		        continue;
        		    }
        		}
        		// oddzial: jeśli nie pasuje → pomiń
        		if (filtrOddzial != null && !filtrOddzial.isBlank() && !filtrOddzial.equals(d.getOddzial())) {
        		    log.info("2 Skipping file fileName='%s ' oddzial='%s ' filtrOddzial='%s '" +fileName  +d.getOddzial()  +filtrOddzial);
        		    continue;
        		}*/


        	    // dodajemy do listy wszystkich sparsowanych dokumentów
        	    allParsedDocs.add(d);

        	    // debug: pokaż typ dokumentu i nazwę pliku
        	    log.info("Parsed doc id=" + d.getId() + " type=" + d.getDocumentType() + " file=" + fileName + " class=" + parsed.getClass().getName());

        	    DmsDocumentOut docOut = null;
        	    try {
        	    	docOut = new DmsToDmsMapper().map(d);
        	    } catch (Exception e) {
        	        log.error("Błąd mapowania dokumentu " + fileName + ": " + e.getMessage(), e);
        	        results.add("Błąd mapowania: " + fileName + " -> " + e.getMessage());
        	        continue; // przejdź do następnego pliku
        	    }
        	    if (docOut == null) {
        	        log.warn(String.format("docOut is null for file: %s", fileName));
        	        continue;
        	    }
        	 // bezpieczne pobranie numeru (unikamy NPE)
        	 // preferujemy invoiceShortNumber jeśli jest dostępny (krótsza, znormalizowana forma)
        	 String invoice = "";
        	// preferuj docOut (wynik mapowania) jeśli istnieje i ma short number
        	 if (docOut != null && docOut.getInvoiceShortNumber() != null && !docOut.getInvoiceShortNumber().isBlank()) {
        	     invoice = docOut.getInvoiceShortNumber();
        	 } else if (docOut != null && docOut.getInvoiceNumber() != null && !docOut.getInvoiceNumber().isBlank()) {
        	     invoice = docOut.getInvoiceNumber();
        	 } else if (d.getInvoiceShortNumber() != null && !d.getInvoiceShortNumber().isBlank()) {
        	     invoice = d.getInvoiceShortNumber();
        	 } else if (d.getInvoiceNumber() != null && !d.getInvoiceNumber().isBlank()) {
        	     invoice = d.getInvoiceNumber();
        	 } else {
        	     invoice = d.getSourceFileName() != null ? d.getSourceFileName() : fileName;
        	 }
        	// usuń niedozwolone znaki i trim
        	 invoice = invoice.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        	// bezpieczne pobranie i normalizacja typu dokumentu
        	 String rawDocType = d.getDocumentType();
        	 String docType = rawDocType != null ? rawDocType.trim().toUpperCase() : "";
        	 // debug log — pokaże co dokładnie mamy przed warunkamiDZ01250033458
        	 String msg = String.format("Processing: file=%s, rawDocType='%s', docType='%s', invoice='%s'",
                     fileName, rawDocType, docType, invoice);
        	 log.info(msg);
        	 Set<String> DS_TYPES = Set.of("DS", "FV", "PR", "FZL", "FVK", "RWS", "PRK", "FZLK", "FVU", "FVM", "FVG");
 		    Set<String> DK_TYPES = Set.of("DK"); //"02"
 		   Set<String> DD_TYPES = Set.of("DM","RO","RK","PO"); //"02"
 		   Set<String> DZ_TYPES = Set.of("DZ","FVZ","FVZK","FVZk", "FZK", "FZk","FS", "FK");
 		   log.info("SERVLET POSITIONS COUNT: " + d.getPositions().size() 
 			         + " file=" + d.getSourceFileName());
           // ============================
           // ŚCIEŻKA DZ (zakup)
           // ============================

 		    if (DZ_TYPES.contains(docType)) {
 		        try {
 		        	//dodatek mapujący rejestr zakupu VAT
 		        	//DmsFieldMapper.normalize(docOut);
 		            // używamy dedykowanego buildera zakupowego
 		        	log.info("DZ positions for fileName='%s '" + fileName + " docType='%s ' " + docType + " DZ_TYPES='%s ' " + DZ_TYPES);

 		            root.addSection(new DmsOfflinePurchaseBuilder(docOut));
 		            results.add("Dodano zakup" + invoice);
 		            //log.info("Added DZ: file=" + fileName + " invoice=" + invoice);
 		        } catch (Exception e) {
 		            //log.error("Błąd budowy sekcji DZ dla " + fileName + ": " + e.getMessage(), e);
 		            results.add("Błąd DZ: " + fileName + " -> " + e.getMessage());
 		        }
                // ============================
                // ŚCIEŻKA DS (sprzedaż)
                // ============================
 		    }else if (DS_TYPES.contains(docType)) {
        		    try {
        		        root.addSection(new DmsOfflineXmlBuilder(docOut));
        		        results.add("Dodano sprzedaż: " + invoice);
        		    } catch (Exception e) {
        		        log.error("Błąd budowy sekcji DS dla " + fileName + ": " + e.getMessage(), e);
        		        results.add("Błąd DS: " + fileName + " -> " + e.getMessage());
        		    }
                    // ============================
                    // ŚCIEŻKA DK (dokument kasowy)
                    // ============================
        	 } else if (DK_TYPES.contains(docType)) {
        		    results.add("Pominięto DK: " + fileName + " typ=" + docType);
        	} else if (DD_TYPES.contains(docType)) {
        		log.info(String.format("Skipping file DD %s: docType='%s' not enabled (enabled=%s)",
                        fileName, docType, DD_TYPES));
                results.add("Pominięto: " + fileName + " typ=" + docType);
                continue;
        		}         	 
        	 else {
        		    results.add("Pominięto: (" + (docType.isBlank() ? "UNKNOWN" : docType) + "): " + invoice);
        		} 
 		    
        	}
            // ============================
            // ŚCIEŻKA SL (kontrahenci)
            // ============================
        	else if (parsed instanceof DmsParsedContractorList sl) {
        	    List<OfflineContractor> mapped = new ContractorMapper().map(sl.contractors);
        	    root.addSection(new ContractorsXmlBuilder(mapped));
        	    results.add("Dodano SL: " +  sl.fileName.replaceAll("[\\\\/:*?\"<>|]", "_"));
        	}        	
        } catch (Exception e) {
        	//tutaj wyjątki jakie pliki nie przetwarzają się
        	log.error("EXCEPTION in processSingleDocument for file " + fileName, e);
        	results.add("Błąd w pliku " + fileName + ": " + e.getMessage());
        	continue;
        	//try { String snippet = xml == null ? "<null>" : xml.length() > 20000 ? xml.substring(0,20000) : xml; log.error("XML snippet for " + fileName + ":\n" + snippet); } catch (Exception ignored) {} results.add("Błąd w pliku " + fileName + ": " + e.getMessage());
        }

    }
    	// 5. BUDUJEMY JEDEN WSPÓLNY DOKUMENT XML
        log.info(String.format("Parsed documents count allParsedDocs.size: "+ allParsedDocs.size()));
        log.info(String.format("Results entries results.size: " + results.size()));
        // jeśli RootXmlBuilder nie ma getterów, dodaj tymczasowy licznik sectionsAdded i inkrementuj przy każdym addSection(...)

        Document finalDoc;

        try {
            finalDoc = root.build();
        } catch (Exception e) {
            throw new ServletException("Błąd budowania XML: " + e.getMessage(), e);
        }
    	// 6. SERIALIZACJA DO PLIKU
		try {
	    	TransformerFactory tf = TransformerFactory.newInstance();
	    	 Transformer transformer = tf.newTransformer();
	    	transformer.setOutputProperty(OutputKeys.INDENT, "yes");
	    	transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
	    	Path out = Paths.get("C:/Users/Administrator.DSI/OneDrive/OPTIMA/ImportyPR/" + outputName + ".xml");
	    	try {
				transformer.transform(new DOMSource(finalDoc), new StreamResult(out.toFile()));
			} catch (TransformerException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	results.add("OK → " + outputName + ".xml");
		} catch (TransformerConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        // 5. Przekazanie finalnego XML do JSP
		// po indeksowaniu i wygenerowaniu HTML
		String relationsHtml = converterService.analyzeRelationsAndGenerateHtml(allParsedDocs);
		if (relationsHtml == null || relationsHtml.isBlank()) {
		    relationsHtml = "<div>Brak powiązań do wyświetlenia</div>";
		}

		// opcja A: przekazanie do JSP jako atrybut
		req.setAttribute("relationsHtml", relationsHtml);
        req.setAttribute("results", results);
        req.getRequestDispatcher("converter/converterResult.jsp").forward(req, resp);
    }
}
