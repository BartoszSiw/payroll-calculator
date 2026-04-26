package pl.edashi.converter.license;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;

/**
 * Offline license verification (Ed25519 signature over raw license.json bytes).
 *
 * Files:
 * - license.json: payload (must not be reformatted after signing)
 * - license.sig : Base64 signature of license.json bytes
 */
public final class LicenseVerifier {

    // Vendor public key (X.509 SubjectPublicKeyInfo, Base64). Replace with your real key.
    // You generate it with the provided LicenseTool and embed only the public key here.
    private static final String VENDOR_PUBLIC_KEY_B64 = "MCowBQYDK2VwAyEAiaPWMTt1jjn4T+T1Dh/YHOYZST2GSyFzuSgYNyRqJuM=";

    public static final String CTX_ATTR_LICENSE = "payacon.licenseStatus";

    private LicenseVerifier() {}

    public static LicenseStatus verify(Path licenseJsonPath, Path licenseSigPath, String expectedHostname) {
        try {
            if (licenseJsonPath == null) return LicenseStatus.invalid("Brak licensePath");
            if (!Files.exists(licenseJsonPath)) return LicenseStatus.invalid("Brak pliku licencji: " + licenseJsonPath);
            if (licenseSigPath == null || !Files.exists(licenseSigPath)) return LicenseStatus.invalid("Brak podpisu licencji: " + licenseSigPath);

            byte[] payloadBytes = Files.readAllBytes(licenseJsonPath);
            String sigB64 = Files.readString(licenseSigPath, StandardCharsets.UTF_8).trim();
            if (sigB64.isBlank()) return LicenseStatus.invalid("Pusty plik podpisu licencji");

            PublicKey pub = loadVendorPublicKey();
            boolean sigOk = verifyEd25519(pub, payloadBytes, Base64.getDecoder().decode(sigB64));
            if (!sigOk) return LicenseStatus.invalid("Nieprawidłowy podpis licencji");

            JSONObject json = new JSONObject(new String(payloadBytes, StandardCharsets.UTF_8));
            String customer = json.optString("customer", "");
            String hostname = json.optString("hostname", "");
            String validUntilRaw = json.optString("validUntil", "");

            LocalDate validUntil = validUntilRaw.isBlank() ? null : LocalDate.parse(validUntilRaw.trim());
            if (validUntil == null) return LicenseStatus.invalid("Brak validUntil w licencji");
            if (LocalDate.now().isAfter(validUntil)) return LicenseStatus.invalid("Licencja wygasła: " + validUntil);

            String expected = normalizeHost(expectedHostname);
            String actualLicenseHost = normalizeHost(hostname);
            String actualMachineHost = normalizeHost(currentHostname());

            if (!expected.isBlank() && !expected.equalsIgnoreCase(actualLicenseHost)) {
                return LicenseStatus.invalid("Licencja wystawiona na inny host: " + hostname + " (oczekiwano: " + expectedHostname + ")");
            }
            // Also ensure license matches current machine hostname (protects copying license to other server)
            if (!actualLicenseHost.isBlank() && !actualLicenseHost.equalsIgnoreCase(actualMachineHost)) {
                return LicenseStatus.invalid("Hostname serwera niezgodny z licencją: server=" + actualMachineHost + " license=" + actualLicenseHost);
            }

            Map<String, Boolean> modules = new HashMap<>();
            JSONObject mods = json.optJSONObject("modules");
            if (mods != null) {
                for (String k : mods.keySet()) {
                    modules.put(k.toLowerCase(), mods.optBoolean(k, false));
                }
            }

            return LicenseStatus.ok(customer, hostname, validUntil, modules);
        } catch (Exception e) {
            return LicenseStatus.invalid("Błąd weryfikacji licencji: " + e.getMessage());
        }
    }

    private static PublicKey loadVendorPublicKey() throws Exception {
        if (VENDOR_PUBLIC_KEY_B64.contains("REPLACE_ME")) {
            throw new IllegalStateException("Vendor public key not configured (VENDOR_PUBLIC_KEY_B64)");
        }
        byte[] der = Base64.getDecoder().decode(VENDOR_PUBLIC_KEY_B64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        return KeyFactory.getInstance("Ed25519").generatePublic(spec);
    }

    private static boolean verifyEd25519(PublicKey pub, byte[] payload, byte[] sig) throws Exception {
        Signature s = Signature.getInstance("Ed25519");
        s.initVerify(pub);
        s.update(payload);
        return s.verify(sig);
    }

    private static String currentHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "";
        }
    }

    private static String normalizeHost(String h) {
        if (h == null) return "";
        String x = h.trim();
        int dot = x.indexOf('.');
        if (dot > 0) x = x.substring(0, dot);
        return x.trim();
    }
}

