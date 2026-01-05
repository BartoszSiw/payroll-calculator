package pl.edashi.common.logging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import pl.edashi.dms.parser.util.DocumentNumberExtractor;

public class AppLogger {
    private final Logger logger;
    private final String module;

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

}


