package pl.edashi.converter.service;
import pl.edashi.common.logging.AppLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class XmlSplitter {
	private final AppLogger log = new AppLogger("SPLITTER");
    public List<String> splitFileIntoDocuments(Path filePath) throws IOException {
        String content = Files.readString(filePath);

        // rozdzielamy po wystąpieniu nagłówka XML
        String[] parts = content.split("<\\?xml");
        List<String> docs = new ArrayList<>();

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            // dodajemy z powrotem nagłówek
            String xml = "<?xml" + trimmed;
            docs.add(xml);
        }

        return docs;
    }
}
