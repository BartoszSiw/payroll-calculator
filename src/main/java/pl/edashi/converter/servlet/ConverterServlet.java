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
import pl.edashi.optima.builder.ContractorsXmlBuilder;
import pl.edashi.optima.mapper.ContractorMapper;
import pl.edashi.optima.model.OfflineContractor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

@WebServlet("/ConverterServlet")
@MultipartConfig
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
    	log.info("Odebrano żądanie konwersji pliku XML");
        Part filePart = req.getPart("xmlFile");

        if (filePart == null || filePart.getSize() == 0) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Nie wybrano pliku XML.");
            return;
        }

        // Zapis pliku do katalogu uploads/
        Path uploadDir = Paths.get("uploads");
        Files.createDirectories(uploadDir);

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
        	    String finalXml = new DmsOfflineXmlBuilder().build(doc);
                // 3. Generowanie finalnego XML zgodnego z Comarch OFFLINE
                String safeNumber = doc.invoiceNumber.replaceAll("[\\\\/:*?\"<>|]", "_");

                Path out = Paths.get("C:/Users/Administrator.DSI/OneDrive/OPTIMA/ImportyPR/" + safeNumber + ".xml");
                Files.writeString(out, finalXml, StandardCharsets.UTF_8);
                // 5. Przekazanie finalnego XML do JSP
                req.setAttribute("xml", finalXml);
                req.getRequestDispatcher("converter/converterResult.jsp").forward(req, resp);
                return;
        	}
            // ============================
            // ŚCIEŻKA SL (kontrahenci)
            // ============================
        	if (parsed instanceof DmsParsedContractorList sl) {
        	    List<OfflineContractor> mapped = new ContractorMapper().map(sl.contractors);
        	    String finalXml = new ContractorsXmlBuilder().build(mapped);
        	    String safeName = sl.fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        	    Path out = Paths.get("C:/Users/Administrator.DSI/OneDrive/OPTIMA/ImportyPR/" + safeName + ".xml");
                Files.writeString(out, finalXml, StandardCharsets.UTF_8);

                req.setAttribute("xml", finalXml);
                req.getRequestDispatcher("converter/converterResult.jsp").forward(req, resp);
                return;
        	}

            // 4. Walidacja XSD
            //DmsXmlValidator.validate(finalXml, "xsd/optima_offline.xsd");



        } catch (Exception e) {
            throw new ServletException("Błąd podczas przetwarzania XML: " + e.getMessage(), e);
        }

    }
}
