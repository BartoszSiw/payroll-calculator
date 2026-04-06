package pl.edashi.converter.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import pl.edashi.common.logging.AppLogger;
import pl.edashi.converter.service.AppStartupListener.AppConfig;

public class DailyXmlWatcher {
	private final AppLogger log = new AppLogger("DailyXmlWatcher");
    private final Supplier<Set<String>> filtersSupplier;
    private final Supplier<String> oddzialSupplier;
    private final Path watchDir;
    private final Path outputDir;
    private final ConverterService converterService;
    private final boolean allowUpdate;
    private final ScheduledExecutorService scheduler;
    //private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService workerPool;// = Executors.newFixedThreadPool(4);
    private final AtomicReference<Boolean> temporaryAllowUpdate = new AtomicReference<>(null);
    //private ScheduledFuture<?> scheduledTask;
    //private volatile boolean running = false;
    private final ReentrantLock processingLock = new ReentrantLock();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final List<String> GROUP1 = List.of("SL");
    private static final List<String> GROUP2 = List.of("DS", "DZ");
    private static final List<String> GROUP3 = List.of("KO", "KZ", "DK", "RO", "RZ", "RD");
    private final AtomicBoolean pauseRequested = new AtomicBoolean(false);
    private final AtomicReference<Boolean> pendingOverride = new AtomicReference<>(null);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile ScheduledFuture<?> scheduledTask;
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    // stability and readiness parameters (tweakable)
    private final long stabilityMillis = 1_000L; // file must be unchanged for 1s
    private final String xmlGlob = "*.xml";
    Path toggleFile = AppConfig.ALLOW_UPDATE_FILE;
    public DailyXmlWatcher(String watchDir, String outputDir, ConverterService converterService, boolean allowUpdate, Supplier<Set<String>> filtersSupplier, Supplier<String> oddzialSupplier, int workerThreads) {
        if (watchDir == null || watchDir.isBlank()) {
            throw new IllegalArgumentException("watchDir must be provided");
        }
        if (outputDir == null || outputDir.isBlank()) {
            throw new IllegalArgumentException("outputDir must be provided");
        }
        this.watchDir = Paths.get(watchDir);
        this.outputDir = Paths.get(outputDir);
        this.converterService = Objects.requireNonNull(converterService, "converterService");
        this.allowUpdate = allowUpdate;
        this.filtersSupplier = Objects.requireNonNull(filtersSupplier, "filtersSupplier");
        this.oddzialSupplier = Objects.requireNonNull(oddzialSupplier, "oddzialSupplier");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DailyXmlWatcher");
            t.setDaemon(true);
            return t;
        });
        
        this.workerPool = Executors.newFixedThreadPool(Math.max(1, workerThreads), r -> {
            Thread t = new Thread(r, "DailyXmlWatcher-worker");
            t.setDaemon(true);
            return t;
        });
    }
    private void runScheduledPass() {
        // jeśli ktoś poprosił o pauzę, pomijamy zaplanowany run
        if (pauseRequested.get()) {
            log.info("runScheduledPass: paused, skipping scheduled pass");
            return;
        }
        if (!processingLock.tryLock()) {
            log.info("runScheduledPass: another run in progress, skipping");
            return;
        }
        try {
            boolean allowUpdate = resolveAllowUpdate();
            log.info(String.format("runScheduledPass: starting processing allowUpdate={}", allowUpdate));
            doProcessing(allowUpdate);
        } catch (Throwable t) {
            log.error("runScheduledPass: unexpected error", t);
        } finally {
            processingLock.unlock();
        }
    }
    /** Schedule the daily run at given hour/minute local server time. */
    public void startAtDailyTime(int hour, int minute) {
        if (!running.compareAndSet(false, true)) {
            log.info("Watcher already running");
            return;
        }
        // oblicz delay do pierwszego uruchomienia
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        long initialDelay = Duration.between(now, next).toMillis();
        if (initialDelay < 0) initialDelay = 0L; // dodatkowe zabezpieczenie
        long period = TimeUnit.DAYS.toMillis(1);

        try {
            scheduledTask = scheduler.scheduleAtFixedRate(() -> {
                try {
                    runScheduledPass();
                } catch (Throwable t) {
                    log.error("Scheduled pass failed", t);
                }
            }, initialDelay, period, TimeUnit.MILLISECONDS);

            scheduled.set(isScheduled());
        } catch (RejectedExecutionException rex) {
            log.error("Failed to schedule DailyXmlWatcher", rex);
            scheduled.set(false);
        }

        log.info(String.format("DailyXmlWatcher scheduled at=%02d:%02d (initialDelayMs=%d, periodMs=%d) scheduled=%s",
                hour, minute, initialDelay, period, scheduled.get()));
    }

    /** Convenience for testing: run immediately once (does not schedule repeating). */
    public void startNowOnce() {
        // atomowo ustawiamy running na true tylko jeśli było false
        if (!running.compareAndSet(false, true)) return;
        // uruchamiamy runOnceSafely z null (brak override) w wątku scheduler
        scheduler.execute(() -> runOnceSafely(null));
    }
    public void stop() {
        running.set(false);
        try { scheduler.shutdownNow(); } catch (Exception ignored) {}
        try { workerPool.shutdownNow(); } catch (Exception ignored) {}
        log.info("DailyXmlWatcher stopped");
    }

    private long computeInitialDelaySeconds(int hour, int minute) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1);
        return Duration.between(now, nextRun).getSeconds();
    }
    private long computeInitialDelayMillis(int hour, int minute) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return Duration.between(now, next).toMillis();
    }
    private void runOnceSafely(Boolean overrideAllow) {
        try {
            runOnce(overrideAllow);
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            // zwalniamy flagę running
            running.set(false);
        }
    }



    private void runOnce(Boolean overrideAllow) throws IOException {
    	log.info(String.format("runOnce invoked at=%s ",LocalDateTime.now()));
        if (!Files.exists(watchDir) || !Files.isDirectory(watchDir)) return;
        Files.createDirectories(outputDir.resolve("archive"));
        Files.createDirectories(outputDir.resolve("error"));
        log.info("runOnce invoked override=" + overrideAllow + " at=" + java.time.LocalDateTime.now());

        boolean allow;
        if (overrideAllow != null) {
            allow = overrideAllow;
            log.info("runOnce: using overrideAllow=" + allow);
        } else {
            // dotychczasowa logika: czytać z ParserRegistry / pliku toggle
            allow = ParserRegistry.getInstance().isAllowUpdate();
            if (!allow) {
                allow = readAllowUpdateFromFile(); // jeśli masz taką metodę
                ParserRegistry.getInstance().setAllowUpdate(allow);
            }
            log.info("runOnce: resolved allowUpdate=" + allow);
        }

        if (!allow) {
            log.info("runOnce: allow=false -> skipping processing");
            return;
        }
        // collect xml files
        List<Path> files;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(watchDir, xmlGlob)) {
            files = new ArrayList<>();
            for (Path p : ds) if (Files.isRegularFile(p)) files.add(p);
        } catch (IOException e) {
            log.error(String.format("DailyXmlWatcher: error reading watchDir %s: %s", watchDir, e.getMessage()), e);
            return;
        }

        if (files.isEmpty()) return;

        // sort by prefix priority (SL first, then DS/DZ, then group3, then others)
        List<Path> ordered = files.stream()
                .sorted(Comparator.comparingInt(this::prefixPriority).thenComparing(Path::getFileName))
                .collect(Collectors.toList());

        List<String> outSL = new ArrayList<>();
        List<String> outDSDZ = new ArrayList<>();
        List<String> outOther = new ArrayList<>();
        log.info(String.format("DailyXmlWatcher: found %d xml files in %s", files.size(), watchDir));
        for (Path file : ordered) {
            try {
                if (!isFromToday(file)) {
                    // skip files older than today
                    continue;
                }
                if (!isFileReady(file)) {
                    // skip unstable or empty files
                    continue;
                }
             // get current filters just before processing
                Set<String> watcherFiltrRejestry = ParserRegistry.getInstance().getFilters();
                String watcherFiltrOddzial = ParserRegistry.getInstance().getOddzial();
                // call converterService; adapt signature if your service differs
                ProcessingResult result = converterService.processFile(file.toFile(), allow, watcherFiltrRejestry, watcherFiltrOddzial);

                if (result.getStatus() == ProcessingResult.Status.ERROR) {
                    // move to error and continue
                    Path target = outputDir.resolve("error").resolve(file.getFileName());
                    Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
                    continue;
                }

                if (result.getStatus() == ProcessingResult.Status.SKIPPED) {
                    // optional: log skip reason if available
                    result.getParsedObject().ifPresent(obj -> {
                        if (obj instanceof SkippedDocument sd) {
                            System.out.println("Skipped " + file.getFileName() + " reason=" + sd.getType());
                        }
                    });
                    // move to archive or keep as policy dictates
                    Path target = outputDir.resolve("archive").resolve(file.getFileName());
                    Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
                    continue;
                }

                // OK: get optional fragment and group it
                Optional<String> fragment = result.getXmlFragment();
                if (fragment.isPresent() && !fragment.get().isBlank()) {
                    int pr = prefixPriority(file);
                    if (pr == 1) outSL.add(fragment.get());
                    else if (pr == 2) outDSDZ.add(fragment.get());
                    else outOther.add(fragment.get());
                }
                // move to archive on success
                Path target = outputDir.resolve("archive").resolve(file.getFileName());
                Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                e.printStackTrace();
                // move to error folder for later inspection
                try {
                    Path target = outputDir.resolve("error").resolve(file.getFileName());
                    Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            log.info(String.format("DailyXmlWatcher: using filters=%s oddzial=%s",
            	    ParserRegistry.getInstance().getFilters(),
            	    ParserRegistry.getInstance().getOddzial()));
        }

        // write grouped outputs (only non-empty groups)
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String ts = LocalDateTime.now().format(fmt);
        log.info("About to write outputs: outSL=" + outSL.size() + " outDSDZ=" + outDSDZ.size() + " outOther=" + outOther.size());
        if (!outSL.isEmpty()) writeOutputFile("SL", outSL, ts);
        if (!outDSDZ.isEmpty()) writeOutputFile("DS_DZ", outDSDZ, ts);
        if (!outOther.isEmpty()) writeOutputFile("OTHER", outOther, ts);
    }

    private int prefixPriority(Path p) {
        String name = p.getFileName().toString();
        if (name.length() >= 2) {
            String two = name.substring(0, 2).toUpperCase(Locale.ROOT);
            if (GROUP1.contains(two)) return 1;
            if (GROUP2.contains(two)) return 2;
            if (GROUP3.contains(two)) return 3;
        }
        return 10;
    }

    private boolean isFromToday(Path file) {
        try {
            long lastModified = Files.getLastModifiedTime(file).toMillis();
            LocalDate fileDate = Instant.ofEpochMilli(lastModified).atZone(ZoneId.systemDefault()).toLocalDate();
            return LocalDate.now().equals(fileDate);
        } catch (IOException e) {
            return false;
        }
    }
    private boolean readAllowUpdateFromFile() {
        Path toggleFile = AppConfig.ALLOW_UPDATE_FILE;
        try {
            if (!Files.exists(toggleFile)) {
                log.info(String.format("readAllowUpdateFromFile: toggle file %s not found -> default false", toggleFile));
                return false;
            }
            String s = Files.readString(toggleFile).trim();
            boolean v = Boolean.parseBoolean(s);
            log.info(String.format("readAllowUpdateFromFile: read %s from %s", v, toggleFile));
            return v;
        } catch (IOException e) {
            log.warn(String.format("readAllowUpdateFromFile: cannot read %s: %s", AppConfig.ALLOW_UPDATE_FILE, e.getMessage()));
            return false;
        }
    }
    private boolean isFileReady(Path file) {
        try {
            long size = Files.size(file);
            if (size == 0) return false;
            long lastModified = Files.getLastModifiedTime(file).toMillis();
            return System.currentTimeMillis() - lastModified > stabilityMillis;
        } catch (IOException e) {
            return false;
        }
    }

    private void writeOutputFile(String groupName, List<String> fragments, String ts) {
        try {
            Files.createDirectories(outputDir);
            Path out = outputDir.resolve(String.format("output_%s_%s.xml", groupName, ts));
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<documents group=\"").append(groupName).append("\" generatedAt=\"").append(ts).append("\">\n");
            for (String frag : fragments) sb.append(frag).append("\n");
            sb.append("</documents>\n");
            Files.writeString(out, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("Wrote output file: " + out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /// Helpers
    public boolean isScheduled() {
        return scheduledTask != null && !scheduledTask.isCancelled() && !scheduledTask.isDone();
    }
    // public API wywoływane przez servlet
 // public API: akceptuje Boolean (może być null)
 // pole klasy (jeśli jeszcze nie ma)
       // metoda
    public boolean triggerTemporaryRun(boolean overrideAllow, long waitMillis) {
        log.info(String.format("triggerTemporaryRun called overrideAllow=%s waitMillis=%s", overrideAllow, waitMillis));
        long effectiveWait = Math.max(0L, waitMillis);

        // zaplanuj lub wykonaj natychmiast zadanie, ale nie blokuj wątku servletu
        Runnable schedule = () -> {
            // próbujemy wykonać run: jeśli lock zajęty, ustawiamy pendingOverride
            if (!processingLock.tryLock()) {
                pendingOverride.set(overrideAllow);
                log.info("Watcher busy -> pendingOverride set to " + overrideAllow);
                return;
            }
            try {
                log.info("Watcher: lock acquired -> performing run with overrideAllow=" + overrideAllow);
                runOnce(Boolean.valueOf(overrideAllow)); // runOnce obsługuje logikę allow
            } catch (Throwable t) {
                log.error("Watcher temporary run failed", t);
            } finally {
                processingLock.unlock();
                log.info("Watcher: run finished overrideAllow=" + overrideAllow);
                // po zakończeniu sprawdź pendingOverride i wykonaj jeden dodatkowy run jeśli ustawione
                Boolean next = pendingOverride.getAndSet(null);
                if (next != null) {
                    log.info("Watcher: executing pending override=" + next);
                    // uruchom bez opóźnienia
                    triggerTemporaryRun(next, 0L);
                }
            }
        };

        if (effectiveWait > 0) {
            scheduler.schedule(() -> executor.submit(schedule), effectiveWait, TimeUnit.MILLISECONDS);
        } else {
            executor.submit(schedule);
        }

        // zwracamy true = żądanie przyjęte do wykonania (asynchronicznie)
        return true;
    }

 // delegacja do wspólnej logiki (wykonywana w wątku executor lub scheduler)
    private void doTriggerImmediate(boolean overrideAllow) {
        log.info("Watcher: doTriggerImmediate requested overrideAllow=" + overrideAllow);
        if (!processingLock.tryLock()) {
            // watcher pracuje -> zapisz ostatni override i zwróć
            pendingOverride.set(overrideAllow);
            log.info("Watcher busy -> pendingOverride set to " + overrideAllow);
            return;
        }
        try {
            log.info("Watcher: lock acquired -> performing run with overrideAllow=" + overrideAllow);
            runOnce(Boolean.valueOf(overrideAllow));
        } catch (Throwable t) {
            log.error("Watcher temporary run failed", t);
        } finally {
            processingLock.unlock();
            log.info("Watcher: run finished overrideAllow=" + overrideAllow);
            // po zakończeniu sprawdź pendingOverride i wykonaj jeden dodatkowy run jeśli ustawione
            Boolean next = pendingOverride.getAndSet(null);
            if (next != null) {
                log.info("Watcher: executing pending override=" + next);
                // uruchom bez opóźnienia
                triggerTemporaryRun(next, 0L);
            }
        }
    }
    // zachowaj wygodną wersję bez parametru
    //public void triggerTemporaryRun() { triggerTemporaryRun((Boolean) null); }
    private boolean resolveAllowUpdate() {
        Boolean tmp = temporaryAllowUpdate.get();
        if (tmp != null) return tmp;
        // tu możesz odczytać globalny supplier / plik / servletContext
        // przykładowo: return Boolean.TRUE.equals(globalAllowSupplier.get());
        return false;
    }

    private void doProcessing(boolean allowUpdate) {
        // przykładowy przebieg: listowanie plików i delegacja do workerPool
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(watchDir, "*.xml")) {
            for (Path p : ds) {
                // atomic move / lock per-file można dodać tutaj
                workerPool.submit(() -> {
                    try {
                        String xml = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                        // filtrRejestry i filtrOddzial dostosuj do kontekstu
                        Object result = converterService.processSingleDocument(xml, p.toString(),allowUpdate, Collections.emptySet(), null);
                        log.info(String.format("Processed=%s ->=%s", p.getFileName(), result == null ? "null" : result.getClass().getSimpleName()));
                        // po sukcesie przenieś do archive
                    } catch (Exception e) {
                        log.error("Error processing file " + p, e);
                        // retry / move to error dir
                    }
                });
            }
        } catch (IOException ioe) {
            log.error("doProcessing: IO error listing directory", ioe);
        }
    }
}


