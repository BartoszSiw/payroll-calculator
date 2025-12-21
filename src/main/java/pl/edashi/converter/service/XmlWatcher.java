package pl.edashi.converter.service;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.converter.model.ConversionResult;

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
            List<String> documents = splitter.splitFileIntoDocuments(file);
            for (String docXml : documents) {
                ConversionResult result =
                        converterService.processSingleDocument(docXml, file.getFileName().toString());

                System.out.println("Processed doc " + result.getMetadata().getGenDocId()
                        + " status=" + result.getStatus()
                        + " msg=" + result.getMessage());

                // tu możesz:
                // - zapisać convertedXml do katalogu output/
                // - jeśli CONFLICT → skopiować oryginalny dokument do buffer/
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}

