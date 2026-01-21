package pl.edashi.common.logging;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import pl.edashi.dms.parser.util.DocumentNumberExtractor;

public class AppLogger {
    private final Logger logger;
    private final String module;
    //private final org.slf4j.Logger logger;

    public AppLogger(String module) {
        this.module = module != null ? module : "default";
        this.logger = LoggerFactory.getLogger("pl.edashi." + this.module);
    }
 // Ustawia MDC tylko na czas pojedynczego wywołania logu
    private void withModule(Runnable r) {
        MDC.put("module", module);
        try { r.run(); } finally { MDC.remove("module"); }
    }

    public void info(String msg) { withModule(() -> logger.info(msg)); }
    public void warn(String msg) { withModule(() -> logger.warn(msg)); }
    public void error(String msg) { withModule(() -> logger.error(msg)); }
    public void error(String msg, Throwable t) { withModule(() -> logger.error(msg, t)); }
    public void debug(String msg) { withModule(() -> logger.debug(msg)); }
    public final class LogUtils {
        private LogUtils() {}

        // Bezpieczne formatowanie: zamienia null na "<null>" i używa Locale.US dla liczb
        public static String safeFormat(String pattern, Object... args) {
            if (pattern == null) return "<null pattern>";
            Object[] safeArgs = new Object[args == null ? 0 : args.length];
            for (int i = 0; i < safeArgs.length; i++) {
                safeArgs[i] = Objects.toString(args[i], "<null>");
            }
            try {
                return String.format(Locale.US, pattern, safeArgs);
            } catch (Exception ex) {
                // jeśli formatowanie zawiedzie, zwróć prostą konkatenację jako fallback
                StringBuilder sb = new StringBuilder(pattern);
                for (Object a : safeArgs) { sb.append(" ").append(a); }
                return sb.toString();
            }
        }
    }

        // ... inne metody ...

        // DODAJ:
        public String getName() {
            return logger.getName();
        }

        // opcjonalnie: zwróć poziom efektywny (Logback)
        public String getEffectiveLevel() {
            if (logger instanceof ch.qos.logback.classic.Logger lb) {
                return lb.getLevel() == null ? "NULL" : lb.getLevel().toString();
            }
            return "UNKNOWN";
        }


}


