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
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.xml.DmsOfflineXmlBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

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
            // 1. Parsowanie dokumentu DMS (DS, KO, DK, WZ...)
            DmsParsedDocument parsed = converterService.processSingleDocument(xml, fileName);

            // 2. Mapowanie DS → DMS (tylko dla DS)
            DmsDocumentOut doc = new DmsToDmsMapper().map(parsed);

            // 3. Generowanie finalnego XML zgodnego z Comarch OFFLINE
            String finalXml = new DmsOfflineXmlBuilder().build(doc);
            Path out = Paths.get("C:/Users/Administrator.DSI/OneDrive/OPTIMA/ImportyPR/" + doc.numer + ".xml");
            Files.writeString(out, finalXml, StandardCharsets.UTF_8);

            // 4. Walidacja XSD
            //DmsXmlValidator.validate(finalXml, "xsd/optima_offline.xsd");

            // 5. Przekazanie finalnego XML do JSP
            req.setAttribute("xml", finalXml);
            req.getRequestDispatcher("converter/converterResult.jsp").forward(req, resp);

        } catch (Exception e) {
            throw new ServletException("Błąd podczas przetwarzania XML: " + e.getMessage(), e);
        }

    }
}
