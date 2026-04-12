package pl.edashi.converter.service;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class ParserRegistry {
    private static final ParserRegistry INSTANCE = new ParserRegistry();

    private final Map<String, Boolean> ready = new ConcurrentHashMap<>();
    private final AtomicReference<Set<String>> filters = new AtomicReference<>(Collections.emptySet());
    private final AtomicReference<String> oddzial = new AtomicReference<>("01");
    private final AtomicBoolean allowUpdate = new AtomicBoolean(false);
    private final AtomicBoolean allowWatcher = new AtomicBoolean(false);
    private final Set<String> outOnlyTypes = ConcurrentHashMap.newKeySet();

    private ParserRegistry() {
        // Domyślnie włączone tylko stabilne typy
        ready.put("DS", true);
        ready.put("DZ", true);
        ready.put("SL", true);

        // Parsery w trakcie pracy / wyłączone
        ready.put("DK", true);
        ready.put("DM", false);
        ready.put("DW", false);
        ready.put("PO", false);
        ready.put("PZ", false);
        ready.put("RD", true);
        ready.put("RO", true);
        ready.put("RZ", true);
        ready.put("ZC", false);
        ready.put("WZ", false);
        ready.put("KO", true);
        ready.put("KZ", true);
        outOnlyTypes.add("DM");
        outOnlyTypes.add("DW");
        outOnlyTypes.add("PO");
        outOnlyTypes.add("WZ");
        outOnlyTypes.add("PZ");
        outOnlyTypes.add("ZC");
    }

    public static ParserRegistry getInstance() {
        return INSTANCE;
    }

    public boolean isEnabled(String type) {
        return Boolean.TRUE.equals(ready.get(type));
    }

    public void setEnabled(String type, boolean enabled) {
        ready.put(type, enabled);
    }

    public Map<String, Boolean> getReadyMap() {
        return Collections.unmodifiableMap(ready);
    }

    // filters (atomic replace)
    public void setFilters(Set<String> newFilters) {
        if (newFilters == null) newFilters = Collections.emptySet();
        // store an immutable copy to avoid external mutation
        filters.set(Collections.unmodifiableSet(new HashSet<>(newFilters)));
    }

    public Set<String> getFilters() {
        return filters.get();
    }

    // oddzial
    public void setOddzial(String o) {
        oddzial.set(o == null ? "01" : o);
    }

    public String getOddzial() {
        return oddzial.get();
    }
    public Set<String> enabledTypes() {
        return ready.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
    public void setAllowUpdate(boolean u) {
        allowUpdate.set(u);
    }
    public void setAllowWatcher(boolean w) {
        allowWatcher.set(w);
    }
    // szybki odczyt cache w watcherze
    public boolean isAllowUpdate() {
        return allowUpdate.get();
    }
    public boolean isAllowWatcher() {
        return allowWatcher.get();
    }

 // public API
    public boolean isOutOnly(String type) {
        if (type == null) return false;
        return outOnlyTypes.contains(type.trim().toUpperCase(Locale.ROOT));
    }
    public void setOutOnlyTypes(Set<String> types) {
        outOnlyTypes.clear();
        if (types != null) {
            for (String t : types) {
                if (t != null && !t.isBlank()) outOnlyTypes.add(t.trim().toUpperCase(Locale.ROOT));
            }
        }
    }

    public Set<String> getOutOnlyTypes() {
        return Collections.unmodifiableSet(new HashSet<>(outOnlyTypes));
    }
}

