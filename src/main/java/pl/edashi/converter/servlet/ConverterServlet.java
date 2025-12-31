package pl.edashi.converter.servlet;
import pl.edashi.common.logging.AppLogger;
import jakarta.servlet.*;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import pl.edashi.converter.repository.DocumentRepository;
import pl.edashi.converter.service.ConverterService;
import pl.edashi.dms.mapper.DmsToDmsMapper;
import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsParsedContractor;
import pl.edashi.dms.model.DmsParsedContractorList;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.xml.DmsOfflineXmlBuilder;
import pl.edashi.dms.xml.RootXmlBuilder;
import pl.edashi.optima.builder.ContractorsXmlBuilder;
import pl.edashi.optima.mapper.ContractorMapper;
import pl.edashi.optima.model.OfflineContractor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

@WebServlet("/ConverterServlet")
@MultipartConfig(
	    //fileSizeThreshold = 1024 * 1024,      // 1MB w pamięci
	    maxFileSize = 10256L,                    // bez limitu
	    maxRequestSize = 702561L                  // bez limitu
	)
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
    	log.info("Odebrano żądanie konwersji wielu plików XML");
    	Collection<Part> parts = req.getParts();
        List<String> results = new ArrayList<>();

        Path uploadDir = Paths.get("uploads");
        Files.createDirectories(uploadDir);
        // Tworzymy builder główny dla jednego pliku
        RootXmlBuilder root = new RootXmlBuilder();
        // nazwa finalnego pliku
        String outputName = "WSPOLNY_IMPORT_" + System.currentTimeMillis();
        for (Part filePart : parts) {
            if (!"xmlFile".equals(filePart.getName())) continue;
            if (filePart.getSize() == 0) continue;

        String fileName = Path.of(filePart.getSubmittedFileName()).getFileName().toString();
        Path savedFile = uploadDir.resolve(fileName);

        try (InputStream input = filePart.getInputStream()) {
            Files.copy(input, savedFile, StandardCopyOption.REPLACE_EXISTING);
        }
        // Wczytaj zawartość XML
        String xml = Files.readString(savedFile);
        try {
            // 1. Parsowanie dokumentu DMS (DS, KO, DK, SL WZ...)
            //DmsParsedDocument parsed = converterService.processSingleDocument(xml, fileName);
        	Object parsed = converterService.processSingleDocument(xml, fileName);
            // ============================
            // ŚCIEŻKA DS (sprzedaż)
            // ============================
        	if (parsed instanceof DmsParsedDocument ds) {
        	    // 2. DS → działa jak dotychczas, Mapowanie DS → DMS (tylko dla DS)
        	    DmsDocumentOut doc = new DmsToDmsMapper().map(ds);
                // 3. Generowanie finalnego XML zgodnego z Comarch OFFLINE
                // dodajemy sekcję DS
                root.addSection(new DmsOfflineXmlBuilder(doc));
                results.add("Dodano DS: " + doc.invoiceNumber.replaceAll("[\\\\/:*?\"<>|]", "_"));
        	}
            // ============================
            // ŚCIEŻKA SL (kontrahenci)
            // ============================
        	else if (parsed instanceof DmsParsedContractorList sl) {
        	    List<OfflineContractor> mapped = new ContractorMapper().map(sl.contractors);
        	    root.addSection(new ContractorsXmlBuilder(mapped));
        	    results.add("Dodano DS: " +  sl.fileName.replaceAll("[\\\\/:*?\"<>|]", "_"));
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
        req.setAttribute("results", results);
        req.getRequestDispatcher("converter/converterResult.jsp").forward(req, resp);
    }
}
