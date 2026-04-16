package pl.edashi.converter.servlet;

import java.util.Set;

public final class DocTypeConstants {
    private DocTypeConstants() {}

    public static final Set<String> DS_TYPES = Set.of("DS", "FV", "PR", "FZL", "FVK", "RWS", "PRK", "FZLK", "FVU", "FVM", "FVG", "FH", "FHK");
    public static final Set<String> DZ_TYPES = Set.of("DZ","FVZ","FVZK","FZK","FS","FK","UMUZ");
    public static final Set<String> DK_TYPES = Set.of("KO","KZ","DK","RO","RZ","RD");
    public static final Set<String> DD_TYPES = Set.of("DM","PO");
    //public static final Set<String> SL_TYPES = Set.of("SL");

    /** KO/KZ/DK — zapis do rejestru dopiero po złożeniu raportu kasowego (jest KZ). */
    public static final Set<String> CASH_REPORT_DB_DEFERRED_TYPES = Set.of("KO", "KZ", "DK");
    /** RO/RZ/RD — analogicznie dla raportu kartowego (jest RZ). */
    public static final Set<String> CARD_REPORT_DB_DEFERRED_TYPES = Set.of("RO", "RZ", "RD");
}

