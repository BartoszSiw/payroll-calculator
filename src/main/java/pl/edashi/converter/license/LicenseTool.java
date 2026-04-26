package pl.edashi.converter.license;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Vendor-side CLI tool (do NOT ship private key).
 *
 * Commands:
 * - gen-keypair
 * - sign <privateKeyB64> <licenseJsonPath> <outSigPath>
 *
 * Output keys are Base64 DER:
 * - public:  X.509 SubjectPublicKeyInfo
 * - private: PKCS#8
 */
public final class LicenseTool {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: gen-keypair | sign <privateKeyB64> <licenseJsonPath> <outSigPath>");
            System.exit(2);
        }
        switch (args[0]) {
            case "gen-keypair" -> genKeypair();
            case "sign" -> {
                if (args.length != 4) {
                    System.err.println("Usage: sign <privateKeyB64> <licenseJsonPath> <outSigPath>");
                    System.exit(2);
                }
                sign(args[1], Path.of(args[2]), Path.of(args[3]));
            }
            default -> {
                System.err.println("Unknown command: " + args[0]);
                System.exit(2);
            }
        }
    }

    private static void genKeypair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
        KeyPair kp = kpg.generateKeyPair();
        String pubB64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        String privB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        System.out.println("PUBLIC_KEY_B64=" + pubB64);
        System.out.println("PRIVATE_KEY_B64=" + privB64);
    }

    private static void sign(String privateKeyB64, Path licenseJsonPath, Path outSigPath) throws Exception {
        byte[] payload = Files.readAllBytes(licenseJsonPath);
        PrivateKey priv = loadPrivateKey(privateKeyB64);
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(priv);
        s.update(payload);
        byte[] sig = s.sign();
        String sigB64 = Base64.getEncoder().encodeToString(sig);
        Files.writeString(outSigPath, sigB64 + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println("Wrote signature: " + outSigPath.toAbsolutePath());
    }

    private static PrivateKey loadPrivateKey(String privateKeyB64) throws Exception {
        byte[] der = Base64.getDecoder().decode(privateKeyB64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        return KeyFactory.getInstance("Ed25519").generatePrivate(spec);
    }
}

