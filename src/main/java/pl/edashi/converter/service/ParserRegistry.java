package pl.edashi.converter.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ParserRegistry {
    private static final ParserRegistry INSTANCE = new ParserRegistry();

    private final Map<String, Boolean> ready = new HashMap<>();

    private ParserRegistry() {
        // Domyślnie włączone tylko stabilne typy
        ready.put("DS", true);
        ready.put("DZ", true);
        ready.put("SL", true);

        // Parsery w trakcie pracy / wyłączone
        ready.put("DK", false);
        ready.put("DM", false);
        ready.put("PO", false);
        ready.put("PZ", false);
        ready.put("RD", false);
        ready.put("RO", false);
        ready.put("ZC", false);
        ready.put("WZ", false);
        ready.put("KO", false);
        ready.put("KZ", false);
    }

    public static ParserRegistry getInstance() {
        return INSTANCE;
    }

    public boolean isEnabled(String docType) {
        if (docType == null) return false;
        return ready.getOrDefault(docType.trim().toUpperCase(), false);
    }

    public void setEnabled(String docType, boolean enabled) {
        if (docType == null) return;
        ready.put(docType.trim().toUpperCase(), enabled);
    }

    public Set<String> enabledTypes() {
        return ready.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}

