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
            this.converterService = (ConverterService) svc;
        } else {
            // fallback: create one (but prefer the listener approach)
            RejestrDao rejestrDao = new RejestrDaoImpl();
            this.converterService = new ConverterService(new DocumentRepository(), rejestrDao);
            ctx.setAttribute("converterService", this.converterService);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
    	//log.info("1 Odebrano żądanie konwersji wielu plików XML");
    	DiskFileItemFactory factory = DiskFileItemFactory.builder().get();
    JakartaServletFileUpload upload = new JakartaServletFileUpload(factory);
        upload.setFileCountMax(-1);
        upload.setSizeMax(1024 * 1024 * 500); // 500 MB
        upload.setFileSizeMax(1024 * 1024 * 50); // 50 MB

        List<FileItem> items;
        try {
            items = upload.parseRequest(req);
            //log.info("Upload: items count = " + (items == null ? 0 : items.size()));
            for (FileItem it : items) {
            }
        } catch (Exception e) {
            throw new ServletException("Błąd parsowania uploadu: " + e.getMessage(), e);
        }
        List<String> results = new ArrayList<>();

        Path uploadDir = Paths.get("uploads");
        Files.createDirectories(uploadDir);
        RootXmlBuilder root = new RootXmlBuilder();
        //String outputName = "DMS_IMPORT_" + System.currentTimeMillis();
        List<DmsParsedDocument> allParsedDocs = new ArrayList<>();
        Set<String> filtrRejestry = new HashSet<>();
        String filtrOddzial = "01"; 
        String IdKsiegOddzial = "DMS_1"; 
        boolean allowUpdate = false;
        boolean simulateScheduledRun = false;
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
            //log.info(String.format("Selected name: %s", name.toUpperCase()));
            //log.info(String.format("Selected rejestr value: %s", value.toUpperCase()));
            if ("rejestr".equals(name)) {
                if (!value.isEmpty()) {
                	filtrRejestry.add(value.toUpperCase());
                    //log.info(String.format("Selected rejestr value: %s", value.toUpperCase()));
                } else {
                    //log.info("Selected rejestr value is empty (treated as all)");
                }
            } else if ("oddzial".equals(name)) {
            	filtrOddzial = value.isBlank() ? "01" : value;
            	IdKsiegOddzial = "DMS_" + ( "02".equals(filtrOddzial) ? "2" : "1" );
                //log.info(String.format("Selected oddzial='%s'", filtrOddzial));
            } else if ("fromDate".equals(name)) {
                DateFilterRegistry.getInstance().setFromDate(
                        value.isBlank() ? null : LocalDate.parse(value)
                    );
                } else if ("toDate".equals(name)) {
                    DateFilterRegistry.getInstance().setToDate(
                        value.isBlank() ? null : LocalDate.parse(value)
                    );
                } else if ("allowUpdate".equals(name)) {
                    // checkbox w multipart zwróci zwykle "on" gdy zaznaczony; może też być "true" lub "1"
                	allowUpdate = "true".equals(item.getString(StandardCharsets.UTF_8));
                	simulateScheduledRun = allowUpdate;
                }	else if ("simulateScheduledRun".equals(name)) {
                        simulateScheduledRun = "on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "1".equals(value);
                        log.info("Form: simulateScheduledRun -> " + simulateScheduledRun);
                } else {
            	log.debug(String.format("Ignored form field: %s", name));
            }
        }
	    log.info(String.format("After parsing: simulateScheduledRun=%s allowUpdate=%s",simulateScheduledRun,allowUpdate));
	    Object watcherAttr = getServletContext().getAttribute("dailyXmlWatcher");
	    
	    log.info(String.format("Servlet: Watcher Attribute = " + watcherAttr));
	    if (watcherAttr != null) log.info("Servlet: dailyXmlWatcher class = " + watcherAttr.getClass().getName());
	 // zakładam, że simulateScheduledRun i allowUpdate są już ustawione
	    if (simulateScheduledRun) {
	    	String waitMillisParam = req.getParameter("waitMillis");
		    long waitMillis = 0L;
		    if (waitMillisParam != null && !waitMillisParam.isBlank()) {
		        try {
		            waitMillis = Long.parseLong(waitMillisParam);
		            if (waitMillis < 0) {
		                log.warn("Negative waitMillis provided, using 0");
		                waitMillis = 0L;
		            }
		            // opcjonalnie limituj maksymalną wartość
		            long max = 60_000L * 10; // np. 10 minut
		            if (waitMillis > max) {
		                log.warn("waitMillis too large, capping to " + max);
		                waitMillis = max;
		            }
		        } catch (NumberFormatException e) {
		            log.warn("Invalid waitMillis value: " + waitMillisParam + " -> using 0");
		            waitMillis = 0L;
		        }
		    }
	        Object o = getServletContext().getAttribute("dailyXmlWatcher");
	        log.info("About to trigger watcher: attr=" + o);
	        if (o instanceof pl.edashi.converter.service.DailyXmlWatcher) {
	            pl.edashi.converter.service.DailyXmlWatcher watcher = (pl.edashi.converter.service.DailyXmlWatcher) o;
	            try {
	                log.info("Servlet: calling watcher.triggerTemporaryRun(" + allowUpdate + ")");
	                watcher.triggerTemporaryRun(allowUpdate,waitMillis);
	                log.info("Servlet: called watcher.triggerTemporaryRun");
	            } catch (Throwable t) {
	                log.error("Servlet: exception while calling watcher.triggerTemporaryRun", t);
	            }
	        } else {
	            log.warn("Servlet: dailyXmlWatcher not found or wrong type: " + (o == null ? "null" : o.getClass().getName()));
	        }
	    }

        /*if (!allowFieldSeen) {
            allowUpdate = false;
            log.info("Form: allowUpdate absent -> treating as false");
        }
        setAllowUpdate(allowUpdate);*/
        root.setIdKsiegOddzial(IdKsiegOddzial);
        // log.info(String.format("Collected rejestry: %s", filtrRejestry.isEmpty() ? "ALL" : filtrRejestry.toString()));
        for (FileItem item : items) {
            if (item.isFormField()) continue; // pomijamy pola formularza
            if (!"xmlFile".equals(item.getFieldName())) continue;
            if (item.getSize() == 0) continue;

            String fileName = Paths.get(item.getName()).getFileName().toString();
            Path savedFile = uploadDir.resolve(fileName);
            try (InputStream input = item.getInputStream()) {
                Files.copy(input, savedFile, StandardCopyOption.REPLACE_EXISTING);
                //log.info(String.format("Saved uploaded file: %s", savedFile.toString()));
            } catch (IOException e) {
                log.error("Failed to save uploaded file: " + fileName, e);
                results.add("Błąd zapisu pliku: " + fileName);
                continue;
            }
        //String xml = Files.readString(savedFile, StandardCharsets.UTF_8);
        //log.info(String.format("fileName='%s'", fileName));
        try {
        	//zamiast ręcznego czytania i wywoływania processSingleDocument
        	ProcessingResult result = converterService.processFile(savedFile.toFile(), allowUpdate, filtrRejestry, filtrOddzial);
        	
        	/*Object parsed = converterService.processSingleDocument(xml, fileName,filtrRejestry, filtrOddzial, allowUpdate);
        	if (parsed instanceof SkippedDocument skipped) {
        	    results.add("Pominięto: " + fileName + " (typ=" + skipped.getType() + ")");
        	    continue;
        	}*/
        	//log.info(String.format("Parsed object type: %s, file: %s",parsed.getClass().getSimpleName(), fileName));
        	// jeśli serwis zwróci pusty wynik => pominięto lub błąd
            if (result.getStatus() == ProcessingResult.Status.ERROR) {
                log.error("Processing failed for file " + fileName);
                results.add("Błąd w pliku " + fileName);
                // optionally move savedFile to error folder here
                continue;
            }
            if (result.getStatus() == ProcessingResult.Status.SKIPPED) {
                // If SkippedDocument contains a reason, you can extract it:
                result.getParsedObject().ifPresent(obj -> {
                    if (obj instanceof SkippedDocument sd) {
                        results.add("Pominięto: " + fileName + " (typ=" + sd.getType() + ")");
                    } else {
                        results.add("Pominięto: " + fileName);
                    }
                });
                continue;
            }

            // OK: we have a parsed object
            Optional<Object> parsedOpt = result.getParsedObject();
            if (parsedOpt.isEmpty()) {
                results.add("Pominięto lub błąd: " + fileName);
                continue;
            }

            Object parsed = parsedOpt.get();
        	// mamy sparsowany dokument — kontynuuj Twoją istniejącą logikę
        	//DmsParsedDocument d = result.getParsedDocument().get();
        	if (parsed instanceof DmsParsedDocument d) {
        		String docRejestr = d.getDaneRejestr() != null ? d.getDaneRejestr().trim().toUpperCase() : "";
                String docOddzial = d.getOddzial() != null ? d.getOddzial().trim() : "";
                if (d.getDocumentType() != null) {
                    d.setDocumentType(d.getDocumentType().trim().toUpperCase());
                }
                //log.info(String.format("filtrRejestru=%s filtrOddzial=%s docRejestr=%s docOddzial=%s",filtrRejestry, filtrOddzial, docRejestr, docOddzial));
                if (!filtrRejestry.isEmpty() && (docRejestr.isEmpty() || !filtrRejestry.contains(docRejestr))) {
                    //log.info(String.format("Skipping file: %s — docRejestr='%s' not in filter %s",fileName, docRejestr, filtrRejestry));
                    continue;
                }
                if (filtrOddzial != null && !filtrOddzial.isBlank() && !filtrOddzial.equals(docOddzial)) {
                    //log.info(String.format("Skipping file: %s — docOddzial='%s' != filter '%s'",fileName, docOddzial, filtrOddzial));
                    continue;
                }
        	    allParsedDocs.add(d);


        	    //AtomicReference<Set<String>> filtersRef = (AtomicReference<Set<String>>) getServletContext().getAttribute("watcherFiltersRef");
        	    //AtomicReference<String> oddzialRef = (AtomicReference<String>) getServletContext().getAttribute("watcherOddzialRef");
        	    //if (filtersRef != null) filtersRef.set(new HashSet<>(filtrRejestry));
        	    //if (oddzialRef != null) oddzialRef.set(filtrOddzial);
        	 // po zebraniu filtrRejestry i filtrOddzial z pól formularza
        	    ParserRegistry.getInstance().setFilters(filtrRejestry);
        	    ParserRegistry.getInstance().setOddzial(filtrOddzial);

        	    //log.info("Parsed doc id=" + d.getId() + " type=" + d.getDocumentType() + " file=" + fileName + " class=" + parsed.getClass().getName());
        	    DmsDocumentOut docOut = null;
        	    try {
        	    	docOut = new DmsToDmsMapper().map(d);
        	    } catch (Exception e) {
        	        //log.error("Błąd mapowania dokumentu " + fileName + ": " + e.getMessage(), e);
        	        //results.add("Błąd mapowania: " + fileName + " -> " + e.getMessage());
        	        continue; // przejdź do następnego pliku
        	    }
        	    if (docOut == null) {
        	        log.warn(String.format("docOut is null for file: %s", fileName));
        	        continue;
        	    }
        	 String invoice = "";
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
        	 invoice = invoice.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        	 String rawDocType = d.getDocumentType();
        	 String docType = rawDocType != null ? rawDocType.trim().toUpperCase() : "";
        	 String msg = String.format("Processing: file=%s, rawDocType='%s', docType='%s', invoice='%s'",fileName, rawDocType, docType, invoice);
        	 //log.info(msg);
        	 Set<String> DS_TYPES = Set.of("DS", "FV", "PR", "FZL", "FVK", "RWS", "PRK", "FZLK", "FVU", "FVM", "FVG", "FH", "FHK");
 		    Set<String> DK_TYPES = Set.of("KO", "KZ", "DK", "RO", "RZ", "RD"); //"02"
 		   Set<String> DD_TYPES = Set.of("DM","PO"); //"02"
 		   Set<String> DZ_TYPES = Set.of("DZ","FVZ","FVZK","FVZk", "FZK", "FZk","FS", "FK","UMUZ");
 		   //log.info("SERVLET POSITIONS COUNT: " + d.getPositions().size()+ " file=" + d.getSourceFileName());
 		    if (DZ_TYPES.contains(docType)) {
 		        try {
 		        	//log.info("DZ positions for fileName='%s '" + fileName + " docType='%s ' " + docType + " DZ_TYPES='%s ' " + DZ_TYPES);
 		            root.addSection(new DmsOfflinePurchaseBuilder(docOut));
 		           outputGroup = "DZ_DS";
 		            results.add("Dodano zakup" + invoice);
 		        } catch (Exception e) {
 		            results.add("Błąd DZ: " + fileName + " -> " + e.getMessage());
 		        }
 		    }else if (DS_TYPES.contains(docType)) {
        		    try {
        		        root.addSection(new DmsOfflineXmlBuilder(docOut));
        		        outputGroup = "DZ_DS";
        		        results.add("Dodano sprzedaż: " + invoice);
        		    } catch (Exception e) {
        		        log.error("Błąd budowy sekcji DS dla " + fileName + ": " + e.getMessage(), e);
        		        results.add("Błąd DS: " + fileName + " -> " + e.getMessage());
        		    }
        	 } else if (DK_TYPES.contains(docType)) {
        		// zamiast mapowania i natychmiastowego dodawania buildera dla KO/KZ/DK:
        		 if (DK_TYPES.contains(docType)) {
        		     // DO NOT create CashReportXmlBuilder here
        		     // We only collect parsed documents (allParsedDocs already contains 'd')
        			 
        		     results.add("Zarejestrowano dokument kasowy (KO/KZ/DK): " + fileName + " typ=" + docType);
        		     // optionally still map docOut if you need docOut fields elsewhere, but do not call root.addSection(...)
        		     continue;
        		 }        		    
        	} else if (DD_TYPES.contains(docType)) {
        		//log.info(String.format("Skipping file DD %s: docType='%s' not enabled (enabled=%s)",fileName, docType, DD_TYPES));
                results.add("Pominięto: " + fileName + " typ=" + docType);
                continue;
        		}         	 
        	 else {
        		    results.add("Pominięto: <--" + (docType.isBlank() ? "UNKNOWN" : docType) + "-->: " + invoice);
        		} 
        	}
        	else if (parsed instanceof DmsParsedContractorList sl) {
        	    List<OfflineContractor> mapped = new ContractorMapper().map(sl.contractors);
        	    root.addSection(new ContractorsXmlBuilder(mapped));
        	    outputGroup = "SL";
        	    results.add("Dodano SL: " +  sl.fileName.replaceAll("[\\\\/:*?\"<>|]", "_"));
        	}        	
        } catch (Exception e) {
        	log.error("EXCEPTION in processSingleDocument for file " + fileName, e);
        	results.add("Błąd w pliku " + fileName + ": " + e.getMessage());
        	continue;
        }
    }
        //log.info(String.format("Parsed documents count allParsedDocs.size: "+ allParsedDocs.size()));
        //log.info(String.format("Results entries results.size: " + results.size()));
        //log.info("ALL PARSED DOCS COUNT = " + allParsedDocs.size());
        //allParsedDocs.forEach(d -> log.info("PARSED: type=" + d.getDocumentType() + " reportNr=" + d.getReportNumber() + " reportNrPos=" + d.getReportNumberPos() + " file=" + d.getSourceFileName()));
     // --- assemble cash reports after all files are parsed ---
        CashReportAssembler assemblerCash = new CashReportAssembler(allParsedDocs);
        CardReportAssembler assemblerCard = new CardReportAssembler(allParsedDocs);

        // Zbierz unikalne numery raportów z KO (możesz też zebrać z DK jeśli KO może nie występować)
     // Zbierz unikalne numery raportów z KO, RO oraz RD (żeby nie pominąć raportów, które mają tylko RD/RO)
        Set<String> reportNumbers = new HashSet<>();

        reportNumbers.addAll(
            allParsedDocs.stream()
                .filter(d -> d.getDocumentType() != null)
                .filter(d -> "KO".equalsIgnoreCase(d.getDocumentType())
                          || "RO".equalsIgnoreCase(d.getDocumentType()))
                .map(DmsParsedDocument::getReportNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
        );
        //log.info("Collected reportNumbers from KO: " + reportNumbers);
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
                    root.addSection(new CashReportXmlBuilder(rapCash));
                    root.addSection(new RozliczeniaXmlBuilder(rapCash));
                    outputGroup = "KO_RO";
                    results.add("Dodano RAPORT_KB: " + nr);
                } catch (Exception e) {
                    log.error("Błąd budowy RAPORT_KB dla " + nr, e);
                    results.add("Błąd RAPORT_KB: " + nr + " -> " + e.getMessage());
                }
            } else {
                log.info("Raport kasowy " + nr + " pominięty - brak KO lub KZ lub raport niekompletny");
                results.add("Pominięto raport: " + nr + " (niekompletny)");
            }
        
        DmsDocumentOut rapCard = assemblerCard.buildSingleReport(nr);
        if (rapCard != null) {
            try {
                // Używamy tego samego buildera XML (CashReportXmlBuilder) jeśli potrafi obsłużyć RD/RO
                root.addSection(new CashReportXmlBuilder(rapCard));
                root.addSection(new RozliczeniaXmlBuilder(rapCard));
                outputGroup = "KO_RO";
                results.add("Dodano RAPORT_KARTOWY: " + nr);
            } catch (Exception e) {
                log.error("Błąd budowy RAPORT_KARTOWY dla " + nr, e);
                results.add("Błąd RAPORT_KARTOWY: " + nr + " -> " + e.getMessage());
            }
        } else {
            log.debug("Raport kartowy " + nr + " pominięty - brak RO/RZ lub niekompletny");
        }
        }
        // --- koniec składania raportów ---
        Document finalDoc = null;
        ////////////////////////////////////
        try {
            finalDoc = root.build();
        } catch (Exception e) {
        	if (finalDoc != null) {
                try {
                    // 1. root info
                    Element roott = finalDoc.getDocumentElement();
                    log.info(String.format("root nodeName='%s' localName='%s' namespace='%s'", roott.getNodeName(), roott.getLocalName(), roott.getNamespaceURI()));
                } catch (Exception ex) {
                    log.error("Failed to extract info from finalDoc", ex);
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
        log.info("Servlet: finalDoc root element = " + (finalDoc.getDocumentElement() == null ? "null" : finalDoc.getDocumentElement().getNodeName()));

        //////////////////////////////////////
		try {
	    	TransformerFactory tf = TransformerFactory.newInstance();
	    	 Transformer transformer = tf.newTransformer();
	    	transformer.setOutputProperty(OutputKeys.INDENT, "yes");
	    	transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
	    	Path out = outputDirPath.resolve(String.format("out_%s_%s.xml", outputGroup, ts));
	    	//Path out = Paths.get("C:/Users/Administrator.DSI/OneDrive/OPTIMA/ImportyPR/" + outputName + ".xml");
	    	try {
				transformer.transform(new DOMSource(finalDoc), new StreamResult(out.toFile()));
			} catch (TransformerException e) {
				e.printStackTrace();
			}
	    	results.add("OK → " + outputName + ".xml");
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		}
		String relationsHtml = converterService.analyzeRelationsAndGenerateHtml(allParsedDocs);
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
}
