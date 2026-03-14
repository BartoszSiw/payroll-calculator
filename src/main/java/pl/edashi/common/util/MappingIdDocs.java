package pl.edashi.common.util;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
//import java.math.BigInteger;
	public final class MappingIdDocs {

	    private MappingIdDocs() { /* nieinstancjonowalna klasa util */ }

	    public static String sanitize(String s) {
	        if (s == null) return "";
	        String t = s.trim().toUpperCase().replaceAll("[^A-Z0-9]", "_");
	        return t.replaceAll("_+", "_");
	    }

	    public static String shortHashBase36(String input, int length) {
	        CRC32 crc = new CRC32();
	        crc.update(input.getBytes(StandardCharsets.UTF_8));
	        long val = crc.getValue();
	        String base36 = Long.toString(val, 36).toUpperCase();
	        if (base36.length() >= length) return base36.substring(0, length);
	        return String.format("%1$" + length + "s", base36).replace(' ', '0');
	    }

	    public static String generateCandidate(String podmiot, String numer, int maxLen) {
	        String a = sanitize(podmiot);
	        String b = sanitize(numer);
	        int hashLen = 6;
	        int sep = 2; // dwa '_' między częściami
	        int remaining = Math.max(0, maxLen - hashLen - sep);
	        int prefLen = Math.min(12, Math.max(3, remaining * 55 / 100));
	        int numLen = Math.max(1, remaining - prefLen);
	        String partA = a.length() <= prefLen ? a : a.substring(0, prefLen);
	        String partB = b.length() <= numLen ? b : b.substring(Math.max(0, b.length() - numLen));
	        String hash = shortHashBase36(a + "|" + b, hashLen);
	        return String.join("_", partA, partB, hash);
	    }

	    public static String extendCandidate(String baseCandidate, int attempt) {
	        String suffix = "_" + attempt;
	        int max = 36;
	        String base = baseCandidate;
	        if (baseCandidate.length() + suffix.length() > max) {
	            base = baseCandidate.substring(0, max - suffix.length());
	        }
	        return base + suffix;
	    }
	}

