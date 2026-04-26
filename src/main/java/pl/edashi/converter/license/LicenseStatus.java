package pl.edashi.converter.license;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

public final class LicenseStatus {
    public final boolean valid;
    public final String message;
    public final String customer;
    public final String hostname;
    public final LocalDate validUntil;
    public final Map<String, Boolean> modules;

    private LicenseStatus(boolean valid, String message, String customer, String hostname, LocalDate validUntil, Map<String, Boolean> modules) {
        this.valid = valid;
        this.message = message == null ? "" : message;
        this.customer = customer == null ? "" : customer;
        this.hostname = hostname == null ? "" : hostname;
        this.validUntil = validUntil;
        this.modules = modules == null ? Collections.emptyMap() : Collections.unmodifiableMap(modules);
    }

    public static LicenseStatus ok(String customer, String hostname, LocalDate validUntil, Map<String, Boolean> modules) {
        return new LicenseStatus(true, "OK", customer, hostname, validUntil, modules);
    }

    public static LicenseStatus invalid(String message) {
        return new LicenseStatus(false, message, "", "", null, Collections.emptyMap());
    }

    public boolean moduleEnabled(String name, boolean defaultValue) {
        if (name == null || name.isBlank()) return defaultValue;
        Boolean v = modules.get(name.trim().toLowerCase());
        return v != null ? v.booleanValue() : defaultValue;
    }
}

