package pl.edashi.converter.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import pl.edashi.common.dao.RejestrDao;
import pl.edashi.common.dao.RejestrDaoImpl;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.converter.repository.DocumentRepository;

@WebListener
public class AppStartupListener implements ServletContextListener {
    private DailyXmlWatcher watcher;
    private final AppLogger log = new AppLogger("AppStartupListener");
    Path toggleFile = AppConfig.ALLOW_UPDATE_FILE;
    ConverterConfig config = new ConverterConfig();
    int hour = config.getWatcherHour();
    int minute = config.getWatcherMinute();
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();
        //ParserRegistry.getInstance().initAllowUpdateFromFile(toggleFile, log);
        String watchDir = ctx.getInitParameter("watchDir");
        String outputDir = ctx.getInitParameter("outputDir");
        boolean allowUpdate = Boolean.parseBoolean(ctx.getInitParameter("allowUpdate"));
        //ctx.log("AppStartupListener: starting. watchDir=" + watchDir + " outputDir=" + outputDir + " allowUpdate=" + allowUpdate);
        log.info(String.format("AppStartupListener: starting. watchDir=%s outputDir=%s allowUpdate=%s", watchDir, outputDir, allowUpdate));
        // read workerThreads from context-param, default = 1
        int workerThreads = 1;
        String workerThreadsParam = ctx.getInitParameter("watcherWorkerThreads");
        if (workerThreadsParam != null && !workerThreadsParam.isBlank()) {
            try {
                workerThreads = Integer.parseInt(workerThreadsParam);
                if (workerThreads < 1) workerThreads = 1;
            } catch (NumberFormatException e) {
                log.warn("Invalid watcherWorkerThreads, using default 1");
                workerThreads = 1;
            }
        }
        // optional: max wait millis config
        long maxWaitMillis = 600_000L; // default 10 min
        String maxWaitParam = ctx.getInitParameter("watcherMaxWaitMillis");
        if (maxWaitParam != null && !maxWaitParam.isBlank()) {
            try {
                maxWaitMillis = Long.parseLong(maxWaitParam);
                if (maxWaitMillis < 0) maxWaitMillis = 600_000L;
            } catch (NumberFormatException e) {
                log.warn("Invalid watcherMaxWaitMillis, using default 600000");
            }
        }
        // walidacja parametrów konfiguracyjnych
        if (watchDir == null || watchDir.isBlank()) {
            ctx.log("DailyXmlWatcher not started: missing context-param 'watchDir'");
            return; // nie uruchamiamy watchera, ale aplikacja startuje dalej
        }
        if (outputDir == null || outputDir.isBlank()) {
            ctx.log("DailyXmlWatcher not started: missing context-param 'outputDir'");
            return;
        }
        RejestrDao rejestrDao = new RejestrDaoImpl(); // ensure it uses a connection pool
        ConverterService converterService = new ConverterService(new DocumentRepository(), rejestrDao);
        ctx.setAttribute("converterService", converterService);
        DocumentOutGenerator generator = new DocumentOutGenerator(converterService);
        ctx.setAttribute("documentOutGenerator", generator);
        log.info(String.format("AppStartupListener: stored shared DocumentOutGenerator in ServletContext identity=%s", System.identityHashCode(generator)));
        AtomicReference<Set<String>> filtersRef = new AtomicReference<>(Collections.emptySet());
        AtomicReference<String> oddzialRef = new AtomicReference<>("01");
        // store service for servlets to reuse

     // store in context so servlet can update them
        ctx.setAttribute("watcherFiltersRef", filtersRef);
        ctx.setAttribute("watcherOddzialRef", oddzialRef);
        DailyXmlWatcher watcher = new DailyXmlWatcher(watchDir, outputDir, converterService, allowUpdate,filtersRef::get, oddzialRef::get, workerThreads,generator);
        //watcher.startNowOnce();
        //watcher.startAtDailyTime(21,36); // schedule daily at 09:30
        watcher.startAtDailyTime(hour, minute);
        ctx.setAttribute("dailyXmlWatcher", watcher);
        log.info(String.format("DailyXmlWatcher started for directory=%s", watchDir));
        log.info(String.format("DailyXmlWatcher scheduled? %s", watcher.isScheduled()));
        log.info(String.format("JVM zone: %s", java.time.ZoneId.systemDefault()));
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (watcher != null) watcher.stop();
        // optionally close converterService resources if needed
    }
    // Config.java (or in AppStartupListener)
    public final class AppConfig {
        public static final Path OUTPUT_DIR = Paths.get("C:/XML/Output");
        public static final Path ALLOW_UPDATE_FILE = OUTPUT_DIR.resolve("allowUpdate.flag");
    }
}


