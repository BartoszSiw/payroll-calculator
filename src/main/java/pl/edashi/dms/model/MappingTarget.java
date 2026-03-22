package pl.edashi.dms.model;

public enum MappingTarget {
    SALES("S"),
    PURCHASES("Z"),
    CARD("C"),
    CASH("K");

    private final String code;

    MappingTarget(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static MappingTarget fromCode(String code) {
        if (code == null) return SALES;

        String normalized = code.trim().toUpperCase();

        switch (normalized) {
            case "Z":
                return PURCHASES;
            case "C":
                return CARD;
            case "K":
                return CASH;
            case "S":
            default:
                return SALES;
        }
    }
}

