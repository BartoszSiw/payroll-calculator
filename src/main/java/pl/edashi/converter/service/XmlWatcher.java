package pl.edashi.converter.service;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.dms.mapper.DmsToDmsMapper;
import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsParsedContractorList;
import pl.edashi.dms.model.DmsParsedDocument;
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

            System.out.println("Watching directory: " + directory);

            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path fileName = (Path) event.context();
                    if (fileName.toString().endsWith(".xml")) {
                        Path fullPath = directory.resolve(fileName);
                        System.out.println("New XML file: " + fullPath);
                        handleNewXmlFile(fullPath);
                    }
                }
                key.reset();
            }
        } catch (Exception e) {
            e.printStackTrace();
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
                String finalXml = new DmsOfflineXmlBuilder().build(dms);

                // Walidacja XSD
                DmsXmlValidator.validate(finalXml, "xsd/optima_offline.xsd");

                // Zapis do katalogu output/
                Path outputDir = Paths.get("output");
                if (!Files.exists(outputDir)) {
                    Files.createDirectories(outputDir);
                }

                Path outFile = outputDir.resolve(ds.metadata.getGenDocId() + ".xml");
                Files.writeString(outFile, finalXml);

                System.out.println("Processed DMS file: " + file.getFileName()
                        + " → saved as " + outFile.getFileName());

                return;
            }

            // ============================
            // 4. Obsługa SL (słownik kontrahentów)
            // ============================
            if (parsed instanceof DmsParsedContractorList sl) {
                System.out.println("Plik SL wykryty: " + file.getFileName()
                        + " — słownik kontrahentów nie jest przetwarzany przez XmlWatcher.");
                return;
            }

            // ============================
            // 5. Nieznany typ
            // ============================
            System.out.println("Nieobsługiwany typ dokumentu w watcherze: " + parsed.getClass());


        } catch (Exception e) {
            System.err.println("Error processing file " + file.getFileName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

}

