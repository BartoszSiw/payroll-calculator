package pl.edashi.converter.service;

import org.w3c.dom.*;

import pl.edashi.converter.model.DocumentStructure;

import java.util.HashSet;
import java.util.Set;

public class XmlStructureComparator {

    public DocumentStructure buildStructure(Document doc) {
        Set<String> paths = new HashSet<>();
        buildPaths(doc.getDocumentElement(), "", paths);
        return new DocumentStructure(paths);
    }

    private void buildPaths(Node node, String currentPath, Set<String> paths) {
        if (node.getNodeType() != Node.ELEMENT_NODE) return;

        String newPath = currentPath.isEmpty()
                ? node.getNodeName()
                : currentPath + "/" + node.getNodeName();

        paths.add(newPath);

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            buildPaths(children.item(i), newPath, paths);
        }
    }

    public boolean areStructuresEqual(DocumentStructure a, DocumentStructure b) {
        return a.getPaths().equals(b.getPaths());
    }
}

