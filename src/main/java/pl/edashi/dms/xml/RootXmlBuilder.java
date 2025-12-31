package pl.edashi.dms.xml;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class RootXmlBuilder {

    private final List<XmlSectionBuilder> sections = new ArrayList<>();

    public void addSection(XmlSectionBuilder section) {
        sections.add(section);
    }

    public Document build() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element root = doc.createElement("ROOT");
        doc.appendChild(root);

        for (XmlSectionBuilder section : sections) {
            section.build(doc, root);
        }

        return doc;
    }
}
