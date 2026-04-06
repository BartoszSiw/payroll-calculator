package pl.edashi.converter.service;
import pl.edashi.common.logging.AppLogger;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmlWatcher {
    private final AppLogger log = new AppLogger("Converter-Watcher");
    private final Path watchDir;
    private final ConverterService converterService;
    private final Pattern filenamePattern; // regex for filename filter
    private final Predicate<Path> dateFilter; // custom date filter
    private final WatchService watchService;
    private final ExecutorService workerPool;
    private final ScheduledExecutorService scheduler;
    private final int maxConcurrent;
    private volatile boolean running = false;

    public XmlWatcher(Path watchDir,
                      ConverterService converterService,
                      Pattern filenamePattern,
                      Predicate<Path> dateFilter,
                      int maxConcurrent) throws IOException {
        this.watchDir = watchDir;
        this.converterService = converterService;
        this.filenamePattern = filenamePattern;
        this.dateFilter = dateFilter;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.maxConcurrent = Math.max(1, maxConcurrent);
        this.workerPool = Executors.newFixedThreadPool(this.maxConcurrent);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() throws IOException {
        if (running) return;
        running = true;
        // register directory
        watchDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY);
        // initial scan to pick up existing files
        initialScan();
        // start watcher loop in background
        Thread watcherThread = new Thread(this::watchLoop, "XmlWatcher-Thread");
        watcherThread.setDaemon(true);
        watcherThread.start();
        log.info(String.format("XmlWatcher started for dir=%s", watchDir));
    }

    public void stop() {
        running = false;
        try { watchService.close(); } catch (IOException ignored) {}
        workerPool.shutdown();
        scheduler.shutdown();
        log.info("XmlWatcher stopped");
    }

    private void initialScan() {
        try {
            Files.list(watchDir)
                 .filter(p -> p.toString().toLowerCase().endsWith(".xml"))
                 .forEach(this::scheduleIfMatches);
        } catch (IOException e) {
            log.warn(String.format("Initial scan failed", e));
        }
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException cwse) {
                break;
            }
            if (key == null) continue;
            for (WatchEvent<?> ev : key.pollEvents()) {
                WatchEvent.Kind<?> kind = ev.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) continue;
                WatchEvent<Path> evPath = (WatchEvent<Path>) ev;
                Path filename = evPath.context();
                Path full = watchDir.resolve(filename);
                // schedule processing with small delay to allow file to finish writing
                scheduler.schedule(() -> scheduleIfMatches(full), 300, TimeUnit.MILLISECONDS);
            }
            boolean valid = key.reset();
            if (!valid) break;
        }
    }

    private void scheduleIfMatches(Path file) {
        try {
            if (!Files.exists(file) || !Files.isRegularFile(file)) return;
            String name = file.getFileName().toString();
            if (!filenamePattern.matcher(name).matches()) {
                log.debug(String.format("Filename does not match pattern=%s", name));
                return;
            }
            if (!dateFilter.test(file)) {
                log.debug(String.format("File filtered by date=%s", name));
                return;
            }
            // check file stability: ensure size not changing for short period
            if (!isFileStable(file, 500, 3)) {
                log.warn(String.format("File not stable, skipping for now=%s", file));
                // optionally schedule retry
                scheduler.schedule(() -> scheduleIfMatches(file), 1, TimeUnit.SECONDS);
                return;
            }
            // submit to worker pool
            workerPool.submit(() -> processFile(file));
        } catch (Throwable t) {
            log.error("Error scheduling file " + file, t);
        }
    }

    private boolean isFileStable(Path file, long waitMillis, int attempts) {
        try {
            long previous = Files.size(file);
            for (int i = 0; i < attempts; i++) {
                Thread.sleep(waitMillis);
                long now = Files.size(file);
                if (now != previous) {
                    previous = now;
                } else {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug(String.format("Stability check failed for file=%s e=%s" + file, e));
            return false;
        }
        return false;
    }

    private void processFile(Path file) {
        String sourceFile = file.toString();
        log.info(String.format("Processing file=%s", sourceFile));
        try {
            String xml = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
            // tutaj wywołujemy istniejący serwis; dopasuj parametry do Twojego API
            // przykładowe parametry filtrów — dostosuj do potrzeb
            Set<String> filtrRejestry = java.util.Collections.emptySet();
            String filtrOddzial = null;
            boolean allowUpdate = true;
            Object result = converterService.processSingleDocument(xml, sourceFile,allowUpdate, filtrRejestry, filtrOddzial);
            log.info(String.format("Processing result for sourceFile=%s result=%s ", sourceFile, result == null ? "null" : result.getClass().getSimpleName()));
            // po sukcesie możesz przenieść plik do katalogu archive lub usunąć
            // Files.move(file, archiveDir.resolve(file.getFileName()), StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            log.error("Failed to process file " + sourceFile, e);
            // retry logic: schedule limited retries with backoff
            scheduleRetry(file, 1);
        }
    }

    private void scheduleRetry(Path file, int attempt) {
        int maxAttempts = 3;
        if (attempt > maxAttempts) {
            log.warn(String.format("Max retries reached for file=%s", file));
            return;
        }
        long delay = (long) Math.pow(2, attempt) * 1000L;
        scheduler.schedule(() -> {
            try {
                if (Files.exists(file)) {
                    processFile(file);
                }
            } catch (Throwable t) {
                log.error("Retry failed for " + file, t);
                scheduleRetry(file, attempt + 1);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    // Example helper: date filter by lastModified or by parsing date from filename
    public static Predicate<Path> lastModifiedBetween(LocalDate from, LocalDate to) {
        return path -> {
            try {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                Instant instant = attrs.lastModifiedTime().toInstant();
                LocalDate fileDate = instant.atZone(ZoneId.systemDefault()).toLocalDate();
                if (from != null && fileDate.isBefore(from)) return false;
                if (to != null && fileDate.isAfter(to)) return false;
                return true;
            } catch (IOException e) {
                return false;
            }
        };
    }

    // Example helper: parse date from filename using formatter and regex group
    public static Predicate<Path> filenameDateRange(Pattern datePattern, DateTimeFormatter fmt, LocalDate from, LocalDate to) {
        return path -> {
            String name = path.getFileName().toString();
            java.util.regex.Matcher m = datePattern.matcher(name);
            if (!m.find()) return false;
            try {
                String dateStr = m.group(1);
                LocalDate d = LocalDate.parse(dateStr, fmt);
                if (from != null && d.isBefore(from)) return false;
                if (to != null && d.isAfter(to)) return false;
                return true;
            } catch (Exception e) {
                return false;
            }
        };
    }
}


