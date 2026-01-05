package pl.edashi.converter.service;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.dms.mapper.DmsToDmsMapper;
import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsParsedContractorList;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.model.DocumentMetadata;
import pl.edashi.dms.xml.DmsOfflineXmlBuilder;
import pl.edashi.dms.xml.DmsXmlValidator;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

public class XmlWatcher implements Runnable {

    private final Path directory;
    private final XmlSplitter splitter;
    private final ConverterService converterService;
    private final AppLogger log = new AppLogger("WATCHER");
    public XmlWatcher(String dir, ConverterService converterService) {
        this.directory = Paths.get(dir);
        this.splitter = new XmlSplitter();
        this.converterService = converterService;
    }

    @Override
    public void run() {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

            log.info("Watching directory: " + directory.toAbsolutePath());

            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key;
                try {
                    key = watchService.take(); // blokuje do momentu zdarzenia
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.info("XmlWatcher interrupted, stopping.");
                    break;
                }
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        log.warn("WatchService overflow event");
                        continue;
                    }
                    Path fileName = (Path) event.context();
                    if (fileName != null && fileName.toString().toLowerCase().endsWith(".xml")) {
                        Path fullPath = directory.resolve(fileName);
                        log.info("New XML file detected: " + fullPath);
                        handleNewXmlFile(fullPath);
                    }
                }
                boolean valid = key.reset();
                if (!valid) {
                    log.warn("WatchKey no longer valid, stopping watcher for directory: " + directory);
                    break;
                }
            }
        } catch (IOException e) {
            log.error("I/O error while watching directory: " + directory + " - " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error in XmlWatcher: " + e.getMessage(), e);
        }
    }

    private void handleNewXmlFile(Path file) {
        try {
            // 1. Wczytanie całego pliku XML jako String
            String xml = Files.readString(file);

            // 2. Parsowanie dokumentu DMS (DS, KO, DK, WZ, KZ, SL ...)
            Object parsed =
                    converterService.processSingleDocument(xml, file.getFileName().toString());

         // ============================
            // 3. Obsługa DS (sprzedaż)
            // ============================
            if (parsed instanceof DmsParsedDocument ds) {

                // Mapowanie DS → DMS
                DmsDocumentOut dms = new DmsToDmsMapper().map(ds);

                // Generowanie finalnego XML zgodnego z Comarch OFFLINE
                // Uwaga: dopasuj wywołanie do rzeczywistej sygnatury DmsOfflineXmlBuilder
                // Jeśli klasa ma konstruktor bezargumentowy i metodę build(DmsDocumentOut) — poniżej OK.
                // Jeśli konstruktor wymaga parametrów, użyj odpowiedniej sygnatury.
                String finalXml;
                try {
                    DmsOfflineXmlBuilder builder = new DmsOfflineXmlBuilder(dms); // sprawdź, czy istnieje konstruktor bez-arg
                    // Utwórz nowy Document do wypełnienia przez buildera
                    javax.xml.parsers.DocumentBuilderFactory dbFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                    dbFactory.setNamespaceAware(true);
                    javax.xml.parsers.DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                    org.w3c.dom.Document docXml = dBuilder.newDocument();

                    // Stwórz root element (builder może oczekiwać konkretnego root; używamy namespace z buildera)
                    // Jeśli DmsOfflineXmlBuilder sam tworzy root, możesz przekazać null lub pusty element — sprawdź implementację.
                    org.w3c.dom.Element root = docXml.createElementNS("http://www.comarch.pl/cdn/optima/offline", "DMS");
                    docXml.appendChild(root);

                    // Wypełnij dokument przez buildera
                    builder.build(docXml, root);

                    // Serializacja Document -> String
                    javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
                    javax.xml.transform.Transformer transformer = tf.newTransformer();
                    transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");
                    transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");

                    java.io.StringWriter writer = new java.io.StringWriter();
                    transformer.transform(new javax.xml.transform.dom.DOMSource(docXml), new javax.xml.transform.stream.StreamResult(writer));
                    finalXml = writer.toString();
                } catch (IllegalArgumentException iae) {
                    log.error("DmsOfflineXmlBuilder rejected document: " + iae.getMessage(), iae);
                    return;
                } catch (javax.xml.parsers.ParserConfigurationException pce) {
                    log.error("Parser configuration error while building offline XML: " + pce.getMessage(), pce);
                    return;
                } catch (javax.xml.transform.TransformerException te) {
                    log.error("Error serializing offline XML: " + te.getMessage(), te);
                    return;
                } catch (Exception ex) {
                    log.error("Unexpected error while building offline XML: " + ex.getMessage(), ex);
                    return;
                }

                // Walidacja XSD
                //DmsXmlValidator.validate(finalXml, "xsd/optima_offline.xsd");

                // Zapis do katalogu output/
                Path outputDir = Paths.get("output");
                if (!Files.exists(outputDir)) {
                    Files.createDirectories(outputDir);
                }
                // Pobierz genDocId bezpośrednio z metadata (bez dostępu do pola)
                DocumentMetadata meta = ds.getMetadata();
                String baseName = (meta != null && meta.getGenDocId() != null && !meta.getGenDocId().isBlank())
                        ? meta.getGenDocId()
                        : file.getFileName().toString().replaceAll("\\.xml$", "");
                Path outFile = outputDir.resolve(baseName + ".xml");
                Files.writeString(outFile, finalXml);

                log.info("Processed DMS file: " + file.getFileName() + " → saved as " + outFile.getFileName());

                // opcjonalnie: przenieś oryginalny plik do katalogu processed/
                try {
                    Path processedDir = directory.resolve("processed");
                    if (!Files.exists(processedDir)) Files.createDirectories(processedDir);
                    Files.move(file, processedDir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    log.info("Moved original file to: " + processedDir.resolve(file.getFileName()));
                } catch (IOException moveEx) {
                    log.warn("Could not move original file to processed/: " + moveEx.getMessage());
                }
                return;
            }

            // ============================
            // 4. Obsługa SL (słownik kontrahentów)
            // ============================
            if (parsed instanceof DmsParsedContractorList) {
                log.info("Plik SL wykryty: " + file.getFileName() + " — słownik kontrahentów nie jest przetwarzany przez XmlWatcher.");
                // opcjonalnie: przenieś plik do innego katalogu lub usuń
                return;
            }

            // ============================
            // 5. Nieznany typ
            // ============================
            log.warn("Nieobsługiwany typ dokumentu w watcherze: " + (parsed != null ? parsed.getClass() : "null"));
        } catch (IOException e) {
            log.error("I/O error processing file " + file.getFileName() + ": " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error processing file " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

}

