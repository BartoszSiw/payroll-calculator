package pl.edashi.converter.servlet;
import pl.edashi.common.dao.RejestrDao;
import pl.edashi.common.dao.RejestrDaoImpl;
import pl.edashi.common.logging.AppLogger;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pl.edashi.converter.repository.DocumentRepository;
import pl.edashi.converter.service.CardReportAssembler;
import pl.edashi.converter.service.CashReportAssembler;
import pl.edashi.converter.service.ConverterService;
import pl.edashi.converter.service.DateFilterRegistry;
import pl.edashi.converter.service.DocumentOutGenerator;
import pl.edashi.converter.service.ParserRegistry;
import pl.edashi.converter.service.ProcessingResult;
import pl.edashi.converter.service.SkippedDocument;
import pl.edashi.dms.mapper.DmsToDmsMapper;
import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsParsedContractorList;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.model.DocumentMetadata;
import pl.edashi.dms.xml.CashReportXmlBuilder;
import pl.edashi.dms.xml.DmsOfflinePurchaseBuilder;
import pl.edashi.dms.xml.DmsOfflineXmlBuilder;
import pl.edashi.dms.xml.RootXmlBuilder;
import pl.edashi.dms.xml.RozliczeniaXmlBuilder;
import pl.edashi.optima.builder.ContractorsXmlBuilder;
import pl.edashi.optima.mapper.ContractorMapper;
import pl.edashi.optima.model.OfflineContractor;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.apache.commons.fileupload2.core.DiskFileItemFactory;
import org.apache.commons.fileupload2.jakarta.JakartaServletFileUpload;
import org.slf4j.MDC;
import org.apache.commons.fileupload2.core.FileItem;

@WebServlet("/ConverterServlet")
public class ConverterServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private ConverterService converterService;
	private final AppLogger log = new AppLogger("Converter-Servlet");
	//private static final Path TOGGLE_FILE = Paths.get("C:/XML/Output/allowUpdate.flag");
    @Override
    public void init() throws ServletException {
        super.init();
        ServletContext ctx = getServletContext();
        Object svc = ctx.getAttribute("converterService");
        if (svc instanceof ConverterService) {
        	log.info(String.format("1 if Servlet init svc=%s",svc.toString()));
            this.converterService = (ConverterService) svc;
        } else {
            // fallback: create one (but prefer the listener approach)
        	log.info(String.format("1 else Servlet init svc=%s",svc.toString()));
            RejestrDao rejestrDao = new RejestrDaoImpl();
            this.converterService = new ConverterService(new DocumentRepository(), rejestrDao);
            ctx.setAttribute("converterService", this.converterService);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
    	log.info("2 Servlet doPost");
    	DiskFileItemFactory factory = DiskFileItemFactory.builder().get();
    JakartaServletFileUpload upload = new JakartaServletFileUpload(factory);
        upload.setFileCountMax(-1);
        upload.setSizeMax(1024 * 1024 * 500); // 500 MB
        upload.setFileSizeMax(1024 * 1024 * 50); // 50 MB

        List<FileItem> items;
        try {
            items = upload.parseRequest(req);
            log.info(String.format("3 Servlet Upload: items count=%s ",(items == null ? 0 : items.size())));
            for (FileItem it : items) {
            }
        } catch (Exception e) {
            throw new ServletException("3 Servlet Błąd parsowania uploadu: " + e.getMessage(), e);
        }
        List<String> results = new ArrayList<>();

        Path uploadDir = Paths.get("uploads");
        Files.createDirectories(uploadDir);
        RootXmlBuilder rootSL = new RootXmlBuilder();
        RootXmlBuilder rootDSDZ = new RootXmlBuilder();
        RootXmlBuilder rootCash = new RootXmlBuilder();
        //String outputName = "DMS_IMPORT_" + System.currentTimeMillis();
        List<Object> allParsedDocs = new ArrayList<>();
        Set<String> filtrRejestry = new HashSet<>();
        String filtrOddzial = "01"; 
        String IdKsiegOddzial = "DMS_1"; 
        boolean allowUpdate = false;
        boolean simulateScheduledRun = false;
        boolean overrideWatcher = false;
        String outputGroup = "INNE";
     // in ConverterServlet.doPost after parsing form fields
        /*AtomicReference<Set<String>> filtersRef = (AtomicReference<Set<String>>) getServletContext().getAttribute("watcherFiltersRef");
        AtomicReference<String> oddzialRef = (AtomicReference<String>) getServletContext().getAttribute("watcherOddzialRef");
        if (filtersRef != null) filtersRef.set(new HashSet<>(filtrRejestry)); // filtrRejestry from form
        if (oddzialRef != null) oddzialRef.set(filtrOddzial);*/

        for (FileItem item : items) {
            if (!item.isFormField()) continue;
            String name = item.getFieldName();
            String value = item.getString(StandardCharsets.UTF_8).trim();
            value = value == null ? "" : value.trim();
            log.info(String.format("4 Servlet Selected name=%s", name.toUpperCase()));
            log.info(String.format("4 Servlet Selected rejestr value: %s", value.toUpperCase()));
            if ("rejestr".equals(name)) {
                if (!value.isEmpty()) {
                	filtrRejestry.add(value.toUpperCase());
                    log.info(String.format("5 if Servlet Selected rejestr value: %s", value.toUpperCase()));
                } else {
                    log.info("5 else Servlet Selected rejestr value is empty (treated as all)");
                }
            } else if ("oddzial".equals(name)) {
            	filtrOddzial = value.isBlank() ? "01" : value;
            	IdKsiegOddzial = "DMS_" + ( "02".equals(filtrOddzial) ? "2" : "1" );
                log.info(String.format("6 elsif Servlet Selected filtrOddzial='%s'", filtrOddzial));
            } else if ("fromDate".equals(name)) {
                DateFilterRegistry.getInstance().setFromDate(
                        value.isBlank() ? null : LocalDate.parse(value)
                    );
                log.info(String.format("7 elsif Servlet Selected value='%s'", value));
                } else if ("toDate".equals(name)) {
                    DateFilterRegistry.getInstance().setToDate(
                        value.isBlank() ? null : LocalDate.parse(value)
                    );
                    log.info(String.format("8 elsif Servlet Selected value='%s'", value));
                } else if ("allowUpdate".equals(name)) {
                    // checkbox w multipart zwróci zwykle "on" gdy zaznaczony; może też być "true" lub "1"
                	allowUpdate = "true".equals(item.getString(StandardCharsets.UTF_8));
                	//simulateScheduledRun = allowUpdate;
                	log.info(String.format("9 elsif Servlet Selected allowUpdate='%s'", allowUpdate));
                }	else if ("simulateScheduledRun".equals(name)) {
                        simulateScheduledRun = "on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "1".equals(value);
                        log.info("10 elsif Servlet Form: simulateScheduledRun -> " + simulateScheduledRun);
                } else {
            	log.debug(String.format("11 else Servlet Ignored form field: %s", name));
            }
        }
	    log.info(String.format("12 Servlet After parsing: simulateScheduledRun=%s allowUpdate=%s",simulateScheduledRun,allowUpdate));
	    Object watcherAttr = getServletContext().getAttribute("dailyXmlWatcher");
	    
	    log.info(String.format("12 Servlet: Watcher Attribute = " + watcherAttr));
	    if (watcherAttr != null) log.info("12 Servlet: dailyXmlWatcher class = " + watcherAttr.getClass().getName());
	 // zakładam, że simulateScheduledRun i allowUpdate są już ustawione
	    if (simulateScheduledRun) {
	    	String waitMillisParam = req.getParameter("waitMillis");
		    long waitMillis = 0L;
		    if (waitMillisParam != null && !waitMillisParam.isBlank()) {
		        try {
		            waitMillis = Long.parseLong(waitMillisParam);
		            if (waitMillis < 0) {
		                log.warn("13 Servlet Negative waitMillis provided, using 0");
		                waitMillis = 0L;
		            }
		            // opcjonalnie limituj maksymalną wartość
		            long max = 60_000L * 10; // np. 10 minut
		            if (waitMillis > max) {
		                log.warn("14 Servlet waitMillis too large, capping to " + max);
		                waitMillis = max;
		            }
		        } catch (NumberFormatException e) {
		            log.warn("15 Servlet Invalid waitMillis value: " + waitMillisParam + " -> using 0");
		            waitMillis = 0L;
		        }
		    }
	        Object o = getServletContext().getAttribute("dailyXmlWatcher");
	        log.info("16 Servlet About to trigger watcher: attr=" + o);
	        if (o instanceof pl.edashi.converter.service.DailyXmlWatcher) {
	            pl.edashi.converter.service.DailyXmlWatcher watcher = (pl.edashi.converter.service.DailyXmlWatcher) o;
	            try {
	                log.info("17 Servlet: calling watcher.triggerTemporaryRun(" + allowUpdate + ")");
	                watcher.triggerTemporaryRun(allowUpdate,waitMillis);
	                log.info("18 Servlet: called watcher.triggerTemporaryRun");
	            } catch (Throwable t) {
	                log.error("19 Servlet: exception while calling watcher.triggerTemporaryRun", t);
	            }
	        } else {
	            log.warn("20 Servlet: dailyXmlWatcher not found or wrong type: " + (o == null ? "null" : o.getClass().getName()));
	        }
	    }

        /*if (!allowFieldSeen) {
            allowUpdate = false;
            log.info("Form: allowUpdate absent -> treating as false");
        }
        setAllowUpdate(allowUpdate);*/
        //rootSL.setIdKsiegOddzial(IdKsiegOddzial);
        //rootDSDZ.setIdKsiegOddzial(IdKsiegOddzial);
        //rootCash.setIdKsiegOddzial(IdKsiegOddzial);
        log.info(String.format("21 Servlet Collected rejestry: %s", filtrRejestry.isEmpty() ? "ALL" : filtrRejestry.toString()));
        for (FileItem item : items) {
            if (item.isFormField()) continue; // pomijamy pola formularza
            if (!"xmlFile".equals(item.getFieldName())) continue;
            if (item.getSize() == 0) continue;

            String fileName = Paths.get(item.getName()).getFileName().toString();
            Path savedFile = uploadDir.resolve(fileName);
            try (InputStream input = item.getInputStream()) {
                Files.copy(input, savedFile, StandardCopyOption.REPLACE_EXISTING);
                log.info(String.format("22 Servlet Saved uploaded file: %s", savedFile.toString()));
            } catch (IOException e) {
                log.error("23 Servlet Failed to save uploaded file: " + fileName, e);
                results.add("Błąd zapisu pliku: " + fileName);
                continue;
            }
        //String xml = Files.readString(savedFile, StandardCharsets.UTF_8);
        //log.info(String.format("fileName='%s'", fileName));
         // przygotuj generator jako pole servletu lub lokalnie
            
            DocumentOutGenerator generator = new DocumentOutGenerator(converterService);

            try {
                DocumentOutGenerator.DocOutGenerationResult genRes =
                        generator.generateAndRecord(savedFile.toFile(), allowUpdate, filtrRejestry, filtrOddzial);
                log.info(String.format("24 Servlet processing genRes file=%s filtrRejestry=%s filtrOddzial=%s", fileName, filtrRejestry, filtrOddzial));
                // pobierz komunikaty i dołącz do results
                List<String> genMessages = generator.drainResults();
                for (String m : genMessages) results.add(m);

                if (genRes == null) {
                    results.add("Błąd przetwarzania: " + fileName);
                    continue;
                }

                switch (genRes.status) {
                    case ERROR -> {
                        results.add("Błąd w pliku " + fileName + " -> " + genRes.message);
                        log.info(String.format("25 Servlet: processing error file=%s msg=%s", fileName, genRes.message));
                        continue;
                    }
                    case SKIPPED -> {
                        results.add("Pominięto: " + fileName + " (" + genRes.message + ")");
                        log.info(String.format("26 Servlet: skipped file=%s reason=%s", fileName, genRes.message));
                        // jeśli potrzebujesz parsedDocument do allParsedDocs:
                        if (genRes.parsedDocument != null) allParsedDocs.add(genRes.parsedDocument);
                        continue;
                    }
                    case OK -> {
                        // --- OBSŁUGA SL (SŁOWNIK KONTRAHENTÓW) ---
                        if (genRes.parsedDocument instanceof DmsParsedContractorList sl) {
                            List<OfflineContractor> mapped = new ContractorMapper().map(sl.contractors);
                            rootSL.setIdKsiegOddzial(IdKsiegOddzial);
                            rootSL.addSection(new ContractorsXmlBuilder(mapped));
                            outputGroup = "SL";
                            results.add("Dodano SL: " + sl.fileName.replaceAll("[\\\\/:*?\"<>|]", "_"));
                            allParsedDocs.add(sl);
                            log.info(String.format("27 Servlet: skipped file=%s akronim=%s", fileName, mapped.get(0).akronim));
                            continue; // SL NIE MA docOut → kończymy case OK
                        }

                        // --- NORMALNE DOKUMENTY (DS/DZ/DK/...) ---
                        DmsDocumentOut docOut = genRes.docOut;
                        if (docOut == null) {
                        	log.info(String.format("28 Servlet: skipped file=%s", fileName));
                            results.add("Pominięto lub błąd: " + fileName);
                            continue;
                        }
                        // zachowaj dotychczasową logikę budowy sekcji, używając docOut
                        String rawDocType = docOut.getDocumentType();
                        String docType = rawDocType != null ? rawDocType.trim().toUpperCase() : "";
                        String invoice = Optional.ofNullable(docOut.getInvoiceShortNumber()).orElse("");
                        invoice = invoice.isBlank() ? Optional.ofNullable(docOut.getInvoiceNumber()).orElse(fileName) : invoice;
                        invoice = invoice.trim().replaceAll("[\\\\/:*?\"<>|]", "_");

                        // przykładowe zbiory typów (użyj swoich stałych)
                        Set<String> DS_TYPES = Set.of("DS", "FV", "PR", "FZL", "FVK", "RWS", "PRK", "FZLK", "FVU", "FVM", "FVG", "FH", "FHK");
                        Set<String> DZ_TYPES = Set.of("DZ","FVZ","FVZK","FZK","FS","FK","UMUZ");
                        Set<String> DK_TYPES = Set.of("KO","KZ","DK","RO","RZ","RD");
                        Set<String> DD_TYPES = Set.of("DM","PO");
                        try {
                            if (DZ_TYPES.contains(docType)) {
                                rootDSDZ.setIdKsiegOddzial(IdKsiegOddzial);
                                rootDSDZ.addSection(new DmsOfflinePurchaseBuilder(docOut));
                                outputGroup = "DZ_DS";
                                log.info(String.format("29 Servlet should be DZ docType=%s IdKsiegOddzial=%s", docType,IdKsiegOddzial));
                                results.add("Dodano zakup: " + invoice);
                            } else if (DS_TYPES.contains(docType)) {
                                rootDSDZ.setIdKsiegOddzial(IdKsiegOddzial);
                                rootDSDZ.addSection(new DmsOfflineXmlBuilder(docOut));
                                outputGroup = "DZ_DS";
                                log.info(String.format("29 Servlet should be DS docType: %s", docType));
                                results.add("Dodano sprzedaż: " + invoice);
                            } else if (DK_TYPES.contains(docType)) {
                            	log.info(String.format("29 Servlet should be DK docType: %s", docType));
                                results.add("Zarejestrowano dokument kasowy (KO/KZ/DK): " + fileName + " typ=" + docType);
                                // nie dodajemy sekcji
                            } else if (DD_TYPES.contains(docType)) {
                            	log.info(String.format("29 Servlet should be DD docType: %s", docType));
                                results.add("Pominięto: " + fileName + " typ=" + docType);
                            } else {
                            	log.info(String.format("29 Servlet should be UNKNOWN docType: %s", docType));
                                results.add("Pominięto: <--" + (docType.isBlank() ? "UNKNOWN" : docType) + "-->: " + invoice);
                            }
                        }  catch (Exception e) {
                            log.info(String.format("29 catch Servlet: error building section for file=%s err=%s", fileName, e.getMessage()));
                            results.add("Błąd budowy sekcji: " + fileName + " -> " + e.getMessage());
                        }

                        // zachowaj parsedDocument jeśli chcesz
                        if (genRes.parsedDocument != null) allParsedDocs.add(genRes.parsedDocument);
                    }
                }
            } catch (Exception ex) {
                log.info(String.format("30 Servlet: EXCEPTION processing file=%s err=%s", fileName, ex.getMessage()));
                results.add("Błąd w pliku " + fileName + ": " + ex.getMessage());
                continue;
            }

    }
        //log.info(String.format("Parsed documents count allParsedDocs.size: "+ allParsedDocs.size()));
        //log.info(String.format("Results entries results.size: " + results.size()));
        //log.info("ALL PARSED DOCS COUNT = " + allParsedDocs.size());
        //allParsedDocs.forEach(d -> log.info("PARSED: type=" + d.getDocumentType() + " reportNr=" + d.getReportNumber() + " reportNrPos=" + d.getReportNumberPos() + " file=" + d.getSourceFileName()));
     // --- assemble cash reports after all files are parsed ---
        List<DmsParsedDocument> parsedDocsForReports = allParsedDocs.stream()
                .filter(o -> o instanceof DmsParsedDocument)
                .map(o -> (DmsParsedDocument) o)
                .collect(Collectors.toList());
        CashReportAssembler assemblerCash = new CashReportAssembler(parsedDocsForReports);
        CardReportAssembler assemblerCard = new CardReportAssembler(parsedDocsForReports);

        // Zbierz unikalne numery raportów z KO (możesz też zebrać z DK jeśli KO może nie występować)
     // Zbierz unikalne numery raportów z KO, RO oraz RD (żeby nie pominąć raportów, które mają tylko RD/RO)
        Set<String> reportNumbers = new HashSet<>();

        reportNumbers.addAll(
            allParsedDocs.stream()
            .filter(d -> d instanceof DmsParsedDocument) //włączone dodatkowe filtrowanie tymczasowo
            .map(d -> (DmsParsedDocument) d) //włączone dodatkowe filtrowanie tymczasowo
            .filter(d -> d.getDocumentType() != null)
                .filter(d -> "KO".equalsIgnoreCase(d.getDocumentType())
                          || "RO".equalsIgnoreCase(d.getDocumentType()))
                .map(DmsParsedDocument::getReportNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
        );
        log.info(String.format("31 Servlet Collected reportNumbers from KO: " + reportNumbers));
        // Jeśli chcesz uwzględnić raporty, które mają tylko DK (bez KO) — dodaj też numery z DK po normalizacji
        // reportNumbers.addAll(allParsedDocs.stream()
//             .filter(d -> "DK".equalsIgnoreCase(d.getDocumentType()))
//             .map(DmsParsedDocument::getCashReportNumber)
//             .filter(Objects::nonNull)
//             .collect(Collectors.toSet()));
        for (String nr : reportNumbers) {
            DmsDocumentOut rapCash = assemblerCash.buildSingleReport(nr);
            if (rapCash != null) {
                try {
                    rootCash.setIdKsiegOddzial(IdKsiegOddzial);
                    rootCash.addSection(new CashReportXmlBuilder(rapCash));
                    rootCash.addSection(new RozliczeniaXmlBuilder(rapCash));
                    outputGroup = "KO_RO";
                    log.info(String.format("32 Servlet RAPORT_KB nr=%s",nr));
                    results.add("Dodano RAPORT_KB: " + nr);
                } catch (Exception e) {
                    log.error("32 Servlet Błąd budowy RAPORT_KB dla " + nr, e);
                    results.add("Błąd RAPORT_KB: " + nr + " -> " + e.getMessage());
                }
            } else {
                log.info("32 Servlet Raport kasowy " + nr + " pominięty - brak KO lub KZ lub raport niekompletny");
                results.add("Pominięto raport: " + nr + " (niekompletny)");
            }
        
        DmsDocumentOut rapCard = assemblerCard.buildSingleReport(nr);
        if (rapCard != null) {
            try {
                // Używamy tego samego buildera XML (CashReportXmlBuilder) jeśli potrafi obsłużyć RD/RO
                rootCash.setIdKsiegOddzial(IdKsiegOddzial);
                rootCash.addSection(new CashReportXmlBuilder(rapCard));
                rootCash.addSection(new RozliczeniaXmlBuilder(rapCard));
                outputGroup = "KO_RO";
                log.info(String.format("33 Servlet RAPORT_KARTOWY nr=%s",nr));
                results.add("Dodano RAPORT_KARTOWY: " + nr);
            } catch (Exception e) {
                log.error("33 Servlet Błąd budowy RAPORT_KARTOWY dla " + nr, e);
                results.add("Błąd RAPORT_KARTOWY: " + nr + " -> " + e.getMessage());
            }
        } else {
            log.debug("33 Servlet Raport kartowy " + nr + " pominięty - brak RO/RZ lub niekompletny");
        }
        }
        // --- koniec składania raportów ---
        Document finalSL = null;
        Document finalDSDZ = null;
        Document finalCash = null;
        ////////////////////////////////////
        try {
            finalSL = rootSL.build();
            finalDSDZ = rootDSDZ.build();
            //finalCash = rootCash.build();
        } catch (Exception e) {
        	if (finalSL != null || finalDSDZ != null) {
                try {
                    // 1. root info
                    Element roott1 = finalSL.getDocumentElement();
                    Element roott2 = finalDSDZ.getDocumentElement();
                    log.info(String.format("34 Servlet SL root nodeName='%s' localName='%s' namespace='%s'", roott1.getNodeName(), roott1.getLocalName(), roott1.getNamespaceURI()));
                    log.info(String.format("35 Servlet DSDZ root nodeName='%s' localName='%s' namespace='%s'", roott2.getNodeName(), roott2.getLocalName(), roott2.getNamespaceURI()));
                } catch (Exception ex) {
                    log.error("34 35 Servlet Failed to extract info from finalDoc", ex);
                }
        	}
            throw new ServletException("Błąd budowania XML: " + e.getMessage(), e);
        }
        //////////////////////////////////////
        /// // przygotowanie nazwy pliku i katalogu
        String outputName = "out_" + outputGroup; // outputGroup np. "DS_DZ" lub inna logika
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String ts = LocalDateTime.now().format(fmt);
        Path outputDirPath = Paths.get("C:/XML/Output"); // lub pobierz z kontekstu/parametru
        Files.createDirectories(outputDirPath);
        log.info(String.format("36 Servlet: finalDoc outputName=%s", outputName));
        //////////////////////////////////////
        try {
            // przygotuj transformer raz
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            String xmlSL = null;
            String xmlDSDZ = null;
            String xmlCash = null;
            try {
                xmlSL = serializeDoc(finalSL);
                log.info(String.format("DBG servlet: xmlSL length=%s", xmlSL == null ? "null" : xmlSL.length()));
            } catch (Exception e) {
                log.error("serializeDoc(finalSL) failed: " + e.getMessage(), e);
                results.add("Błąd serializacji SL: " + e.getMessage());
            }
            try {
                xmlDSDZ = serializeDoc(finalDSDZ);
                log.info(String.format("DBG servlet: xmlDSDZ length=%s", xmlDSDZ == null ? "null" : xmlDSDZ.length()));
            } catch (Exception e) {
                log.error("serializeDoc(finalDSDZ) failed: " + e.getMessage(), e);
                results.add("Błąd serializacji DS_DZ: " + e.getMessage());
            }
            try {
                xmlCash = serializeDoc(finalCash);
                log.info(String.format("DBG servlet: xmlCash length=%s", xmlCash == null ? "null" : xmlCash.length()));
            } catch (Exception e) {
                log.error("serializeDoc(finalCash) failed: " + e.getMessage(), e);
                results.add("Błąd serializacji CASH: " + e.getMessage());
            }

            // write + publish
            writeAndPublish(xmlSL, "SL", ts, outputDirPath);
            writeAndPublish(xmlDSDZ, "DS_DZ", ts, outputDirPath);
            writeAndPublish(xmlCash, "CASH", ts, outputDirPath);
            results.add("OK → " + "out files created");
        } catch (TransformerConfigurationException e) {
            log.error("40 catch TransformerFactory: Transformer configuration failed", e);
            throw new ServletException("Transformer configuration error", e);
        }

		List<DmsParsedDocument> parsedDocsForRelations = allParsedDocs.stream()
		        .filter(o -> o instanceof DmsParsedDocument)
		        .map(o -> (DmsParsedDocument) o)
		        .collect(Collectors.toList());

		String relationsHtml = converterService.analyzeRelationsAndGenerateHtml(parsedDocsForRelations);
		if (relationsHtml == null || relationsHtml.isBlank()) {
		    relationsHtml = "<div>Brak powiązań do wyświetlenia</div>";
		}
		ParserRegistry registry = ParserRegistry.getInstance();
		Set<String> enabled = registry.enabledTypes();
		req.setAttribute("enabledTypes", enabled);
		String enabledStr = String.join(",", enabled);
		req.setAttribute("enabledStr", enabledStr);
		
		req.setAttribute("relationsHtml", relationsHtml);
        req.setAttribute("results", results);
        req.getRequestDispatcher("converter/converterResult.jsp").forward(req, resp);
    }
    /// Helpers
    private String serializeDoc(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter sw = new StringWriter();
        log.info("41 Servlet serializeDoc");
        transformer.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }
    private void writeAndPublish(String xmlString, String prefix, String ts, Path outputDirPath) {
        if (xmlString == null || xmlString.isEmpty()) return;

        String fileName = String.format("out_%s_%s.xml", prefix, ts);
        Path tmp = outputDirPath.resolve(fileName + ".tmp");
        Path target = outputDirPath.resolve(fileName);

        try {
            Files.writeString(tmp, xmlString, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info(String.format("37 writeAndPublish: wrote finalDoc %s", target.toAbsolutePath()));
        } catch (Exception e) {
            log.error(String.format("37 writeAndPublish: failed to write file %s: %s", target, e.getMessage()), e);
            try { Files.deleteIfExists(tmp); } catch (IOException ignore) {}
            return;
        }

        Object genObj = getServletContext().getAttribute("documentOutGenerator");
        DocumentOutGenerator gen;
        if (genObj instanceof DocumentOutGenerator) {
            gen = (DocumentOutGenerator) genObj;
        } else {
            gen = new DocumentOutGenerator(converterService);
            getServletContext().setAttribute("documentOutGenerator", gen);
            log.warn("38 writeAndPublish: documentOutGenerator missing in context — created fallback instance");
        }

        try {
            gen.publishFinalDoc(prefix, xmlString, ts);
            log.info(String.format("39 writeAndPublish: published finalDoc to generator outputGroup=%s ts=%s", prefix, ts));
        } catch (Exception ex) {
            log.error(String.format("39 writeAndPublish: failed to publish finalDoc outputGroup=%s err=%s", prefix, ex.getMessage()), ex);
        }
    }
}

    /*private void setAllowUpdate(boolean allow) {
        try {
            Files.createDirectories(TOGGLE_FILE.getParent());
            Path tmp = Files.createTempFile(TOGGLE_FILE.getParent(), "allowUpdate", ".tmp");
            Files.writeString(tmp, Boolean.toString(allow), StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(tmp, TOGGLE_FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException amnse) {
                Files.move(tmp, TOGGLE_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            // opcjonalnie: zaktualizuj cache w ParserRegistry, jeśli go używasz
            // ParserRegistry.getInstance().setAllowUpdate(allow);
            log.info(String.format("setAllowUpdate: wrote '%s' to %s", allow, TOGGLE_FILE));
        } catch (IOException e) {
            log.error(String.format("setAllowUpdate: failed to write toggle file %s: %s", TOGGLE_FILE, e.getMessage()), e);
        }
    }*/
//}
