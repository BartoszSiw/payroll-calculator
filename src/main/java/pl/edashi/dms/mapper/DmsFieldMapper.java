package pl.edashi.dms.mapper;

import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.model.DmsPosition;

import java.util.HashMap;
import java.util.Map;

public class DmsFieldMapper {

    // ============================
    // MAPOWANIA — łatwe do rozbudowy
    // ============================

    private static final Map<String, String> WYR_MAP = new HashMap<>();
    static {
        WYR_MAP.put("CP", "Z1");
        WYR_MAP.put("CD", "Z1");
        WYR_MAP.put("CR", "Z1");
        WYR_MAP.put("EX", "Z2");
        WYR_MAP.put("PA", "Z3");
        WYR_MAP.put("EX", "Z4");
        WYR_MAP.put("EX", "ZK");
        WYR_MAP.put("EX", "ZR");
        // W przyszłości:
        // WYR_MAP.put("ABC", "COŚ_INNEGO");
        // WYR_MAP.put("XYZ", "USŁUGI");
    }

    // Możesz dodać kolejne mapy:
    // private static final Map<String, String> KATEGORIA_MAP = ...
    // private static final Map<String, String> RODZAJ_KOSZTY_MAP = ...
    // private static final Map<String, String> SPRZEDAZ_MAP = ...

    // ============================
    // GŁÓWNA METODA
    // ============================

    public static void normalize(DmsParsedDocument doc) {
        if (doc == null || doc.getPositions() == null) return;

        for (DmsPosition p : doc.getPositions()) {
            normalizeWyr(p);
            // normalizeKategorie(p);
            // normalizeRodzajKoszty(p);
            // normalizeSprzedaz(p);
            // normalizeOpis(p);
        }
    }

    // ============================
    // KONKRETNE MAPOWANIA
    // ============================

    private static void normalizeWyr(DmsPosition p) {
        if (p == null ) return;
        p.typDZ = WYR_MAP.getOrDefault(p.typDZ, p.typDZ);
    }

    // Przykład kolejnych metod:
    /*
    private static void normalizeKategorie(DmsPosition p) {
        if (p == null || p.kategoria == null) return;
        p.kategoria = KATEGORIA_MAP.getOrDefault(p.kategoria, p.kategoria);
    }

    private static void normalizeRodzajKoszty(DmsPosition p) {
        if (p == null || p.rodzajKoszty == null) return;
        p.rodzajKoszty = RODZAJ_KOSZTY_MAP.getOrDefault(p.rodzajKoszty, p.rodzajKoszty);
    }
    */
}

