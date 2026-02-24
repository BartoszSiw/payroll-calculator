package pl.edashi.dms.parser;

import org.w3c.dom.Document;

import pl.edashi.dms.model.DmsParsedDocument;

public interface DmsParser {
    DmsParsedDocument parse(Document doc, String sourceFile) throws Exception;
}
