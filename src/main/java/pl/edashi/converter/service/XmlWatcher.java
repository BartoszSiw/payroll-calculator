package pl.edashi.converter.service;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.dms.mapper.DmsToDmsMapper;
import pl.edashi.dms.model.DmsDocumentOut;
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

            // 2. Parsowanie dokumentu DMS (DS, KO, DK, WZ, KZ...)
            DmsParsedDocument parsed =
                    converterService.processSingleDocument(xml, file.getFileName().toString());

            // 3. Mapowanie DS → DMS (tylko DS ma mapowanie do rejestru sprzedaży)
            DmsDocumentOut dms = null;

            if ("DS".equals(parsed.metadata.getId())) {
                dms = new DmsToDmsMapper().map(parsed);
            } else {
                System.out.println("Typ dokumentu " + parsed.metadata.getId() +
                        " nie jest mapowany do DMS XML. Pomijam.");
                return;
            }

            // 4. Generowanie finalnego XML zgodnego z Comarch OFFLINE
            String finalXml = new DmsOfflineXmlBuilder().build(dms);

            // 5. Walidacja XSD
            DmsXmlValidator.validate(finalXml, "xsd/optima_offline.xsd");

            // 6. Zapis do katalogu output/
            Path outputDir = Paths.get("output");
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            Path outFile = outputDir.resolve(parsed.metadata.getGenDocId() + ".xml");
            Files.writeString(outFile, finalXml);

            System.out.println("Processed DMS file: " + file.getFileName()
                    + " → saved as " + outFile.getFileName());

        } catch (Exception e) {
            System.err.println("Error processing file " + file.getFileName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

}

