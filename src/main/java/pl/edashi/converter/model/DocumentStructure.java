package pl.edashi.converter.model;

import java.util.Set;

public class DocumentStructure {
    private final Set<String> paths; // np. DMS/document/dane/wartosci

    public DocumentStructure(Set<String> paths) {
        this.paths = paths;
    }

    public Set<String> getPaths() {
        return paths;
    }
}
