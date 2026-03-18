package pl.edashi.dms.model;

public enum MappingTarget {
    SALES("S"),
    PURCHASES("Z");

    private final String code;

    MappingTarget(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static MappingTarget fromCode(String code) {
        if (code == null) return SALES;
        code = code.trim().toUpperCase();
        return "Z".equals(code) ? PURCHASES : SALES;
    }
}

