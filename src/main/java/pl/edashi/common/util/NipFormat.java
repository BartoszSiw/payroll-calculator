package pl.edashi.common.util;

/**
 * Normalizacja NIP z DMS (np. {@code 576-142-74-30}, spacje) do samej sekwencji cyfr dla porównań i eksportu Optima.
 */
public final class NipFormat {

    private NipFormat() {}

    /**
     * Usuwa wszystko poza cyframi (myślniki, spacje, prefiks kraju z liter — zostaje tylko ciąg cyfr).
     * {@code null} lub pusty wejście → {@code ""}.
     */
    public static String digitsOnly(String nip) {
        if (nip == null || nip.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(nip.length());
        for (int i = 0; i < nip.length(); i++) {
            char ch = nip.charAt(i);
            if (Character.isDigit(ch)) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
