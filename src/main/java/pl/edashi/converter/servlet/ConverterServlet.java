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
import pl.edashi.dms.xml.DmsOfflineXmlBuilder;
import pl.edashi.dms.xml.RootXmlBuilder;
import pl.edashi.optima.builder.ContractorsXmlBuilder;
import pl.edashi.optima.mapper.ContractorMapper;
import pl.edashi.optima.model.OfflineContractor;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
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
;


        JakartaServletFileUpload upload = new JakartaServletFileUpload(factory);

        // BRAK LIMITU LICZBY PLIKÓW
        upload.setFileCountMax(-1);

        // BRAK LIMITU ROZMIARU
        upload.setSizeMax(1024 * 1024 * 500); // 500 MB
        upload.setFileSizeMax(1024 * 1024 * 50); // 50 MB

        List<FileItem> items;
        try {
            items = upload.parseRequest(req);
            //log.info("Upload: items count = " + (items == null ? 0 : items.size()));
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
        for (FileItem item : items) {

            if (item.isFormField()) continue; // pomijamy pola formularza
            if (!"xmlFile".equals(item.getFieldName())) continue;
            if (item.getSize() == 0) continue;

            String fileName = Paths.get(item.getName()).getFileName().toString();
            Path savedFile = uploadDir.resolve(fileName);

            try (InputStream input = item.getInputStream()) {
                Files.copy(input, savedFile, StandardCopyOption.REPLACE_EXISTING);
            }
        // Wczytaj zawartość XML
        String xml = Files.readString(savedFile);
        try {
            // 1. Parsowanie dokumentu DMS (DS, KO, DK, SL WZ...)
            //DmsParsedDocument parsed = converterService.processSingleDocument(xml, fileName);
        	Object parsed = converterService.processSingleDocument(xml, fileName);
        	if (parsed instanceof DmsParsedDocument d) {
        	    // dodajemy do listy wszystkich sparsowanych dokumentów
        	    allParsedDocs.add(d);

        	    // debug: pokaż typ dokumentu i nazwę pliku
        	    //log.info("Parsed doc id=" + d.getId() + " type=" + d.getDocumentType() + " file=" + fileName + " class=" + parsed.getClass().getName());

        	    DmsDocumentOut docOut = null;
        	    try {
        	        docOut = new DmsToDmsMapper().map(d);
        	    } catch (Exception e) {
        	        log.error("Błąd mapowania dokumentu " + fileName + ": " + e.getMessage(), e);
        	        results.add("Błąd mapowania: " + fileName + " -> " + e.getMessage());
        	        continue; // przejdź do następnego pliku
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

        	 // debug log — pokaże co dokładnie mamy przed warunkami
        	 String msg = String.format("Processing: file=%s, rawDocType='%s', normalizedDocType='%s', invoice='%s'",
                     fileName, rawDocType, docType, invoice);
        	 //log.info(msg);
        	 Set<String> DS_TYPES = Set.of("DS", "FV", "PR");//, "FZL", "FVK", "RWS"
 		    Set<String> DK_TYPES = Set.of("DK", "02");
                // ============================
                // ŚCIEŻKA DS (sprzedaż)
                // ============================
        	 if (DS_TYPES.contains(docType)) {
        		    try {
        		        root.addSection(new DmsOfflineXmlBuilder(docOut));
        		        results.add("Dodano DS: " + invoice);
        		    } catch (Exception e) {
        		        log.error("Błąd budowy sekcji DS dla " + fileName + ": " + e.getMessage(), e);
        		        results.add("Błąd DS: " + fileName + " -> " + e.getMessage());
        		    }
                    // ============================
                    // ŚCIEŻKA DK (dokument kasowy)
                    // ============================
        	 } else if (DK_TYPES.contains(docType)) {
        		    results.add("Dodano DK: " + invoice);
        		} else {
        		    results.add("Dodano (" + (docType.isBlank() ? "UNKNOWN" : docType) + "): " + invoice);
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
            // 4. Walidacja XSD
            //DmsXmlValidator.validate(finalXml, "xsd/optima_offline.xsd");
        	
        } catch (Exception e) {
        	results.add("Błąd w pliku " + fileName + ": " + e.getMessage());
        }

    }
    	// 5. BUDUJEMY JEDEN WSPÓLNY DOKUMENT XML
    	
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
