package pl.edashi.converter.service;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.output.XmlStreamWriter;
import org.apache.commons.io.output.XmlStreamWriter.Builder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import pl.edashi.common.logging.AppLogger;
import pl.edashi.converter.service.AppStartupListener.AppConfig;
import pl.edashi.converter.service.DocumentOutGenerator.DocOutGenerationResult;
import pl.edashi.converter.servlet.DocTypeConstants;
import pl.edashi.dms.model.DmsDocumentOut;
import pl.edashi.dms.model.DmsParsedContractorList;
import pl.edashi.dms.model.DmsParsedDocument;
import pl.edashi.dms.xml.CashReportXmlBuilder;
import pl.edashi.dms.xml.DmsOfflinePurchaseBuilder;
import pl.edashi.dms.xml.DmsOfflineXmlBuilder;
import pl.edashi.optima.builder.ContractorsXmlBuilder;
import pl.edashi.optima.mapper.ContractorMapper;
import pl.edashi.optima.model.OfflineContractor;
//import pl.edashi.dms.xml.XmlStreamWriter; // dopasuj do rzeczywistego pakietu interfejsu Builder
import pl.edashi.dms.xml.RozliczeniaXmlBuilder;
import pl.edashi.dms.xml.XmlSectionBuilder;
public class DailyXmlWatcher {
	private final AppLogger log = new AppLogger("DailyXmlWatcher");
	ConverterConfig config = new ConverterConfig();
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
    private final DocumentOutGenerator generator;
    private volatile ScheduledFuture<?> scheduledTask;
    private final AtomicBoolean scheduled = new AtomicBoolean(false);
    // stability and readiness parameters (tweakable)
    private final long stabilityMillis = 1_000L; // file must be unchanged for 1s
    int day = config.getWatcherDay();
    private final String xmlGlob = "*.xml";
    Path toggleFile = AppConfig.ALLOW_UPDATE_FILE;
    public DailyXmlWatcher(String watchDir, String outputDir, ConverterService converterService, boolean allowUpdate, Supplier<Set<String>> filtrRejestry,Supplier<String> filtrOddzial, int workerThreads,DocumentOutGenerator generator) {
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
        this.filtersSupplier = Objects.requireNonNull(filtrRejestry, "filtrRejestry");
        this.oddzialSupplier = Objects.requireNonNull(filtrOddzial, "filtrOddzial");
        log.info(String.format("1 Daily filtrRejestry=%s filtrOddzial=%s",filtrRejestry,filtrOddzial));
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
        this.generator = generator != null ? generator : new DocumentOutGenerator(converterService);
    }
    private void runScheduledPass() {
        // jeśli ktoś poprosił o pauzę, pomijamy zaplanowany run
        if (pauseRequested.get()) {
            log.info("2 runScheduledPass: paused, skipping scheduled pass");
            return;
        }
        if (!processingLock.tryLock()) {
            log.info("2 runScheduledPass: another run in progress, skipping");
            return;
        }
        try {
            boolean allowUpdate = resolveAllowUpdate();
            log.info(String.format("3 runScheduledPass: starting processing allowUpdate=%s", allowUpdate));
            doProcessing(allowUpdate);
        } catch (Throwable t) {
            log.error("4 runScheduledPass: unexpected error", t);
        } finally {
            processingLock.unlock();
        }
    }
    /** Schedule the daily run at given hour/minute local server time. */
    public void startAtDailyTime(int hour, int minute) {
        if (!running.compareAndSet(false, true)) {
            log.info("5 Watcher already running");
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
                    log.error("5 Scheduled pass failed", t);
                }
            }, initialDelay, period, TimeUnit.MILLISECONDS);

            scheduled.set(isScheduled());
        } catch (RejectedExecutionException rex) {
            log.error("5 Failed to schedule DailyXmlWatcher", rex);
            scheduled.set(false);
        }

        log.info(String.format("6 DailyXmlWatcher scheduled at=%02d:%02d (initialDelayMs=%d, periodMs=%d) scheduled=%s",
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
        log.info("7 DailyXmlWatcher stopped");
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
        log.info(String.format("8 runOnce invoked at=%s", LocalDateTime.now()));

        if (!ensureWatchAndOutputDirs()) return;

        boolean allow = resolveAllow(overrideAllow);
        if (!allow) {
            log.info("9 runOnce: allow=false -> skipping processing");
            return;
        }

        List<Path> files = collectXmlFiles();
        if (files.isEmpty()) {
            log.info(String.format("10 DailyXmlWatcher: no files to process in %s", watchDir));
            return;
        }

        List<Path> ordered = files.stream()
                .sorted(Comparator.comparingInt(this::prefixPriority).thenComparing(Path::getFileName))
                .collect(Collectors.toList());

        // etykiety
        List<String> outSL = new ArrayList<>();
        List<String> outDSDZ = new ArrayList<>();
        List<String> outCashCard = new ArrayList<>();
        List<String> outOther = new ArrayList<>();

        // kolekcje builderów i komunikatów (thread-safe)
        Queue<ContractorsXmlBuilder> collectedContractorSections = new ConcurrentLinkedQueue<>();
        Queue<XmlSectionBuilder> collectedSections = new ConcurrentLinkedQueue<>(); // trzymamy buildery implementujące XmlSectionBuilder
        Queue<Object> allParsedDocs = new ConcurrentLinkedQueue<>();
        Queue<String> collectedMessages = new ConcurrentLinkedQueue<>();

        // użyj pola this.generator jeśli masz; w przeciwnym razie utwórz lokalny generator
        DocumentOutGenerator generator = this.generator != null ? this.generator : new DocumentOutGenerator(converterService);

        for (Path file : ordered) {
            processSingleFile(file, allow, generator, outSL, outDSDZ, outCashCard, outOther,
                    collectedContractorSections, collectedSections, allParsedDocs, collectedMessages);
        }
        log.info(String.format("11 runOnce: finished processing files. collectedContractorSections.size=%s collectedSections.size=%s allParsedDocs.size=%s collectedMessages.size=%s",
                collectedContractorSections.size(), collectedSections.size(), allParsedDocs.size(), collectedMessages.size()));
        log.info(String.format("12 runOnce: outSL.size=%s outDSDZ.size=%s outCashCard.size=%s outOther.size=%s",
                outSL.size(), outDSDZ.size(), outCashCard.size(), outOther.size()));

        // publikacja finalDoc z builderów zebranych przez watcher
        persistPublishedFinalDocs(collectedContractorSections, collectedSections, outSL, outDSDZ, outCashCard, outOther,
                collectedMessages, allParsedDocs, allow);

        // zapis etykiet / raportów
        writeGroupedOutputs(outSL, outDSDZ, outCashCard, outOther);
        archiveStaleCashCardFromWatch();
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
            LocalDate today = LocalDate.now();
            return !fileDate.isBefore(today.minusDays(day)); // czyli: >= today-2
        } catch (IOException e) {
            return false;
        }
    }
    private boolean readAllowUpdateFromFile() {
        Path toggleFile = AppConfig.ALLOW_UPDATE_FILE;
        try {
            if (!Files.exists(toggleFile)) {
                log.info(String.format("13 readAllowUpdateFromFile: toggle file %s not found -> default false", toggleFile));
                return false;
            }
            String s = Files.readString(toggleFile).trim();
            boolean v = Boolean.parseBoolean(s);
            log.info(String.format("14 readAllowUpdateFromFile: read %s from %s", v, toggleFile));
            return v;
        } catch (IOException e) {
            log.warn(String.format("15 readAllowUpdateFromFile: cannot read %s: %s", AppConfig.ALLOW_UPDATE_FILE, e.getMessage()));
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
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n");
            sb.append("<ROOT>\n");
            for (String frag : fragments) sb.append(frag);
            sb.append("</ROOT>\n");
            Files.writeString(out, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("Wrote output file: " + out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private String buildFragmentFromDocOut(DmsDocumentOut docOut) throws Exception {
        if (docOut == null) return null;
        String docType = Optional.ofNullable(docOut.getDocumentType()).orElse("").trim().toUpperCase();
        log.info(String.format("16 buildFragmentFromDocOut: invoice=%s docType=%s", docOut.getInvoiceShortNumber(), docType));

        // wybierz odpowiedni builder na podstawie typu dokumentu
        Object builder = null;
        if (Set.of("DZ","FVZ","FVZK","FZK","FS","FK","UMUZ").contains(docType)) {
            builder = new DmsOfflinePurchaseBuilder(docOut);
        } else if (Set.of("DS","FV","PR","FZL","FVK","RWS","PRK","FZLK","FVU","FVM","FVG","FH","FHK").contains(docType)) {
            builder = new DmsOfflineXmlBuilder(docOut);
        } else {
            log.info(String.format("17 buildFragmentFromDocOut: no builder for docType=%s, using fallback minimal fragment", docType));
            // fallback: prosty fragment (możesz usunąć jeśli nie chcesz fallbacku)
            return String.format("<document type=\"%s\" invoice=\"%s\"/>",
                    escapeXml(docType), escapeXml(Optional.ofNullable(docOut.getInvoiceShortNumber()).orElse(docOut.getInvoiceNumber())));
        }

        // utwórz tymczasowy Document i kontenerowy element root
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document tmpDoc = db.newDocument();
        Element tmpRoot = tmpDoc.createElement("fragmentContainer");
        tmpDoc.appendChild(tmpRoot);

        // wywołaj istniejącą metodę build(Document, Element) na builderze
        try {
            // zakładamy, że builder ma metodę public void build(Document, Element)
            Method m = builder.getClass().getMethod("build", Document.class, Element.class);
            m.invoke(builder, tmpDoc, tmpRoot);
        } catch (NoSuchMethodException nsme) {
            // jeśli builder nie ma build(Document, Element) — log i fallback
            log.info(String.format("18 buildFragmentFromDocOut: builder %s has no build(Document,Element) method", builder.getClass().getSimpleName()));
            return null;
        } catch (InvocationTargetException ite) {
            throw new Exception("18 Builder invocation failed: " + ite.getTargetException().getMessage(), ite.getTargetException());
        }

        // serializuj zawartość tmpRoot (bez deklaracji XML)
        String fragment = serializeChildrenWithoutXmlDecl(tmpRoot);
        log.info(String.format("19 buildFragmentFromDocOut: generated fragment length=%d for invoice=%s", fragment == null ? 0 : fragment.length(), docOut.getInvoiceShortNumber()));
        return fragment;
    }


    /// Helpers
    public boolean isScheduled() {
        return scheduledTask != null && !scheduledTask.isCancelled() && !scheduledTask.isDone();
    }
    // public API wywoływane przez servlet
 // public API: akceptuje Boolean (może być null)
 // pole klasy (jeśli jeszcze nie ma)
    public boolean triggerTemporaryRun(boolean overrideAllow, long waitMillis) {
        log.info(String.format("20 triggerTemporaryRun called overrideAllow=%s waitMillis=%s", overrideAllow, waitMillis));
        long effectiveWait = Math.max(0L, waitMillis);

        // zaplanuj lub wykonaj natychmiast zadanie, ale nie blokuj wątku servletu
        Runnable schedule = () -> {
            // próbujemy wykonać run: jeśli lock zajęty, ustawiamy pendingOverride
            if (!processingLock.tryLock()) {
                pendingOverride.set(overrideAllow);
                log.info("21 Watcher busy -> pendingOverride set to " + overrideAllow);
                return;
            }
            try {
                log.info("22 Watcher: lock acquired -> performing run with overrideAllow=" + overrideAllow);
                runOnce(Boolean.valueOf(overrideAllow)); // runOnce obsługuje logikę allow
            } catch (Throwable t) {
                log.error("22 Watcher temporary run failed", t);
            } finally {
                processingLock.unlock();
                log.info("23 Watcher: run finished overrideAllow=" + overrideAllow);
                // po zakończeniu sprawdź pendingOverride i wykonaj jeden dodatkowy run jeśli ustawione
                Boolean next = pendingOverride.getAndSet(null);
                if (next != null) {
                    log.info("23 Watcher: executing pending override=" + next);
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

    // serializuje tylko dzieci danego elementu (bez <?xml?>), zwraca String
    private String serializeChildrenWithoutXmlDecl(Node parent) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter sw = new StringWriter();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            t.transform(new DOMSource(child), new StreamResult(sw));
        }
        return sw.toString();
    }

    // prosty escaper do atrybutów
    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
 // delegacja do wspólnej logiki (wykonywana w wątku executor lub scheduler)
    private void doTriggerImmediate(boolean overrideAllow) {
        log.info("24 Watcher: doTriggerImmediate requested overrideAllow=" + overrideAllow);
        if (!processingLock.tryLock()) {
            // watcher pracuje -> zapisz ostatni override i zwróć
            pendingOverride.set(overrideAllow);
            log.info("24 Watcher busy -> pendingOverride set to " + overrideAllow);
            return;
        }
        try {
            log.info("25 Watcher: lock acquired -> performing run with overrideAllow=" + overrideAllow);
            runOnce(Boolean.valueOf(overrideAllow));
        } catch (Throwable t) {
            log.error("25 Watcher temporary run failed", t);
        } finally {
            processingLock.unlock();
            log.info("26 Watcher: run finished overrideAllow=" + overrideAllow);
            // po zakończeniu sprawdź pendingOverride i wykonaj jeden dodatkowy run jeśli ustawione
            Boolean next = pendingOverride.getAndSet(null);
            if (next != null) {
                log.info("26 Watcher: executing pending override=" + next);
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
        // zbierz pliki najpierw (deterministycznie)
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(watchDir, "*.xml")) {
            for (Path p : ds) {
                if (Files.isRegularFile(p)) files.add(p);
            }
        } catch (IOException ioe) {
            log.error(String.format("27 doProcessing: IO error listing directory %s: %s", watchDir, ioe.getMessage()), ioe);
            return;
        }

        if (files.isEmpty()) {
            log.info(String.format("27 doProcessing: no files to process in %s", watchDir));
            return;
        }

        log.info(String.format("28 doProcessing: found %s files, submitting to workerPool", files.size()));

        // współdzielone kolekcje (takie jak w runOnce)
        Queue<ContractorsXmlBuilder> collectedContractorSections = new ConcurrentLinkedQueue<>();
        Queue<XmlSectionBuilder> collectedSections = new ConcurrentLinkedQueue<>();
        Queue<Object> allParsedDocs = new ConcurrentLinkedQueue<>();
        Queue<String> collectedMessages = new ConcurrentLinkedQueue<>();
        List<String> outSL = Collections.synchronizedList(new ArrayList<>());
        List<String> outDSDZ = Collections.synchronizedList(new ArrayList<>());
        List<String> outCashCard = Collections.synchronizedList(new ArrayList<>());
        List<String> outOther = Collections.synchronizedList(new ArrayList<>());

        // lista future do czekania
        List<Future<?>> futures = new ArrayList<>();

        for (Path p : files) {
            // submitujemy zadanie, które wykona dokładnie to, co robi processSingleFile
            futures.add(workerPool.submit(() -> {
                try {
                    // processSingleFile zajmuje się: filtrami, builderami, archiwizacją pliku
                    processSingleFile(p, allowUpdate, this.generator, outSL, outDSDZ, outCashCard, outOther,
                                      collectedContractorSections, collectedSections, allParsedDocs, collectedMessages);
                } catch (Throwable t) {
                    log.error(String.format("29 doProcessing: worker failed for file=%s err=%s", p.getFileName(), t.getMessage()), t);
                    try {
                        moveTo(p, outputDir.resolve("error"));
                    } catch (IOException ioe) {
                        log.error(String.format("29 doProcessing: failed to move file=%s to error: %s", p.getFileName(), ioe.getMessage()), ioe);
                    }
                }
            }));
        }

        // czekamy na zakończenie wszystkich zadań (bez nieskończonego blokowania)
        for (Future<?> f : futures) {
            try {
                f.get(); // blokuje do zakończenia zadania
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn(String.format("30 doProcessing: interrupted while waiting for workers"));
            } catch (ExecutionException ee) {
                log.error(String.format("30 doProcessing: worker execution exception: %s", ee.getMessage()), ee);
            }
        }

        // snapshot i log
        log.info(String.format("31 doProcessing: workers finished. collectedContractorSections.size=%s collectedSections.size=%s allParsedDocs.size=%s collectedMessages.size=%s",
                collectedContractorSections.size(), collectedSections.size(), allParsedDocs.size(), collectedMessages.size()));
        log.info(String.format("31 doProcessing: outSL.size=%s outDSDZ.size=%s outCashCard.size=%s outOther.size=%s",
                outSL.size(), outDSDZ.size(), outCashCard.size(), outOther.size()));

        // publikacja finalDoc z builderów zebranych przez watcher
        try {
            persistPublishedFinalDocs(collectedContractorSections, collectedSections, outSL, outDSDZ, outCashCard, outOther,
                    collectedMessages, allParsedDocs, allowUpdate);
        } catch (Exception e) {
            log.error(String.format("32 doProcessing: persistPublishedFinalDocs failed: %s", e.getMessage()), e);
        }

        // zapis etykiet / raportów
        try {
            writeGroupedOutputs(outSL, outDSDZ, outCashCard, outOther);
        } catch (Exception e) {
            log.error(String.format("33 doProcessing: writeGroupedOutputs failed: %s", e.getMessage()), e);
        }
        try {
            archiveStaleCashCardFromWatch();
        } catch (Exception e) {
            log.error(String.format("33b doProcessing: archiveStaleCashCardFromWatch failed: %s", e.getMessage()), e);
        }
    }

    private boolean ensureWatchAndOutputDirs() {
        try {
            if (!Files.exists(watchDir) || !Files.isDirectory(watchDir)) {
                log.info(String.format("34 runOnce: watchDir missing or not a directory: %s", watchDir));
                return false;
            }
            Files.createDirectories(outputDir.resolve("archive"));
            Files.createDirectories(outputDir.resolve("error"));
            return true;
        } catch (IOException e) {
            log.error(String.format("34 DailyXmlWatcher: failed to create output dirs %s: %s", outputDir, e.getMessage()), e);
            return false;
        }
    }
    private boolean resolveAllow(Boolean overrideAllow) {
        if (overrideAllow != null) {
            log.info(String.format("35 runOnce: using overrideAllow=%s", overrideAllow));
            return overrideAllow;
        }
        boolean allow = ParserRegistry.getInstance().isAllowUpdate();
        if (!allow) {
            allow = readAllowUpdateFromFile(); // jeśli istnieje
            ParserRegistry.getInstance().setAllowUpdate(allow);
        }
        log.info(String.format("35 runOnce: resolved allowUpdate=%s", allow));
        return allow;
    }
    private List<Path> collectXmlFiles() {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(watchDir, xmlGlob)) {
            for (Path p : ds) if (Files.isRegularFile(p)) files.add(p);
        } catch (IOException e) {
            log.error(String.format("36 DailyXmlWatcher: error reading watchDir %s: %s", watchDir, e.getMessage()), e);
        }
        return files;
    }
    private void processSingleFile(Path file,
            boolean allow,
            DocumentOutGenerator generator,
            List<String> outSL,
            List<String> outDSDZ,
            List<String> outCashCard,
            List<String> outOther,
            Queue<ContractorsXmlBuilder> collectedContractorSections,
            Queue<XmlSectionBuilder> collectedSections,
            Queue<Object> allParsedDocs,
            Queue<String> collectedMessages) {

    	String fileName = file.getFileName().toString();
    	try {
    		if (!isFromToday(file)) {
    			log.info(String.format("37 DailyXmlWatcher: skipping not-from-today file=%s", fileName));
    			return;
    		}
    		if (!isFileReady(file)) {
    			log.info(String.format("37 DailyXmlWatcher: skipping not-ready file=%s", fileName));
    			return;
    		}

    		Set<String> watcherFiltrRejestry = ParserRegistry.getInstance().getFilters();
    		String watcherFiltrOddzial = ParserRegistry.getInstance().getOddzial();
    		log.info(String.format("38 DailyXmlWatcher: processing file=%s with filters=%s oddzial=%s", fileName, watcherFiltrRejestry, watcherFiltrOddzial));

    		DocumentOutGenerator.DocOutGenerationResult genRes =
    				generator.generateAndRecord(file.toFile(), allow, watcherFiltrRejestry, watcherFiltrOddzial);

    		// log messages produced by generator
    		List<String> genMessages = generator.drainResults();
    		for (String m : genMessages) log.info(String.format("39 DailyXmlWatcher: generator msg=%s watcherFiltrRejestry=%s watcherFiltrOddzial=%s", m,watcherFiltrRejestry, watcherFiltrOddzial));

    		if (genRes == null) {
    			moveTo(file, outputDir.resolve("error"));
    			return;
    		}
    		if (genRes.status == DocumentOutGenerator.Status.ERROR) {
    			moveTo(file, outputDir.resolve("error"));
    			return;
    		}
    		if (genRes.status == DocumentOutGenerator.Status.SKIPPED) {
    			// sprawdź czy to OUT_ONLY
    		    Object parsed = genRes.parsedDocument;
    		    boolean isOutOnly = false;
    		    if (parsed instanceof SkippedDocument sd) {
    		        String reason = sd.getReason() == null ? "" : sd.getReason();
    		        String type = sd.getType() == null ? "" : sd.getType();
    		        if ("OUT_ONLY".equalsIgnoreCase(reason) || ParserRegistry.getInstance().isOutOnly(type)) {
    		            isOutOnly = true;
    		        }
    		    }
    		    if (isOutOnly) {
    		        Path outArchiveOut = outputDir.resolve("archive").resolve("out");
    		        Files.createDirectories(outArchiveOut);
    		        Path target = outArchiveOut.resolve(file.getFileName());
    		        Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
    		        log.info(String.format("40 DailyXmlWatcher: moved OUT_ONLY file=%s to %s", file.getFileName(), outArchiveOut.toAbsolutePath()));
    		        collectedMessages.add(String.format("Przeniesiono OUT_ONLY: %s", file.getFileName()));
    		        return;
    		    } else {
    		        // dotychczasowa logika: archive lub inne
    			moveToArchiveRespectingCashCardRetention(file);
    			return;
    		}
    		}
    		log.info("41 processSingleFile: OK genRes parsed=" + (genRes.parsedDocument == null ? "null" : genRes.parsedDocument.getClass().getSimpleName())
    		         + " docOut=" + (genRes.docOut == null ? "null" : genRes.docOut.getDocumentType()));
    		// TRACE: genRes context
    		log.info("41 TRACE-1 genRes context: file=" + file.getFileName()
    		    + " parsedClass=" + (genRes.parsedDocument == null ? "null" : genRes.parsedDocument.getClass().getSimpleName())
    		    + " docOutType=" + (genRes.docOut == null ? "null" : genRes.docOut.getDocumentType())
    		    + " parserRegistryOddzial=" + ParserRegistry.getInstance().getOddzial());

    		/// for SL
            // SL jako parsedDocument
    		// TRACE: before SL handling
    		log.info("41 TRACE-2 before SL handling: file=" + file.getFileName()
    		    + " parserOddzial=" + ParserRegistry.getInstance().getOddzial()
    		    + " genRes_parsed_notnull=" + (genRes.parsedDocument != null));

            if (genRes.parsedDocument instanceof DmsParsedContractorList sl) {
                ContractorsXmlBuilder cb = new ContractorsXmlBuilder(new ContractorMapper().map(sl.contractors));
             // ustaw id księgowe natychmiast (tak jak dla DSDZ)
                String watcherOddzial = ParserRegistry.getInstance().getOddzial();
                String idKsieg = "DMS_" + ("02".equals(watcherOddzial) ? "2" : "1");
                try {
                    // jeśli ContractorsXmlBuilder implementuje XmlSectionBuilder lub ma setter
                    if (cb instanceof XmlSectionBuilder) {
                        ((XmlSectionBuilder) cb).setIdKsiegOddzial(idKsieg);
                    } else {
                        // jeśli nie, wywołaj metodę setIdKsiegOddzial bez rzutowania (dodaj ją do klasy)
                        cb.setIdKsiegOddzial(idKsieg);
                    }
                    log.info("TRACE-SL builder created and id set: file=" + fileName + " builderClass=" + cb.getClass().getSimpleName() + " idKsieg=" + idKsieg);
                } catch (Throwable t) {
                    log.warn(String.format("Failed to set idKsiegOddzial on SL builder for file=%s catch (Throwable t=%s)",fileName, t));
                }
                collectedContractorSections.add(cb);
                outSL.add(sl.fileName.replaceAll("[\\\\/:*?\"<>|]", "_"));
                allParsedDocs.add(sl);
                collectedMessages.add("Dodano SL: " + sl.fileName);
                moveTo(file, outputDir.resolve("archive"));
                return;
            }
    		/// 
    		
    		DmsDocumentOut docOut = genRes.docOut;
    		if (docOut != null) {
                String docType = Optional.ofNullable(docOut.getDocumentType()).orElse("").trim().toUpperCase();
                String watcherOddzial = ParserRegistry.getInstance().getOddzial(); // powinno zwracać "01" lub "02"
                String idKsieg = "DMS_" + ( "02".equals(watcherOddzial) ? "2" : "1" );
                if (DocTypeConstants.DZ_TYPES.contains(docType)) {
                    XmlSectionBuilder builder = new DmsOfflinePurchaseBuilder(docOut);
                    try {
                        if (builder instanceof XmlSectionBuilder) {
                            ((XmlSectionBuilder) builder).setIdKsiegOddzial(idKsieg);
                        }
                    } catch (Throwable t) {
                        log.warn(String.format("Failed to set idKsiegOddzial on builder for file=%s catch Throwable t=%s", file.getFileName(), t));
                    }
                    boolean ok = collectedSections.offer(builder);
                    log.info("41 TRACE-4 queued attempt: file=" + file.getFileName()
                        + " builderClass=" + (builder == null ? "null" : builder.getClass().getSimpleName())
                        + " offerResult=" + ok
                        + " collectedSections.size=" + collectedSections.size()
                        + " parserOddzial=" + ParserRegistry.getInstance().getOddzial());
                } else if (DocTypeConstants.DK_TYPES.contains(docType)) {
                    // KO/KZ/DK/RO/RZ/RD: jak w ConverterServlet — pojedynczy docOut nie jest pełnym raportem;
                    // CashReportXmlBuilder/RozliczeniaXmlBuilder po składzie CashReportAssembler/CardReportAssembler w persistPublishedFinalDocs.
                    log.info("41 TRACE-4 DK registered for batch assembly (no per-file cash XML): file=" + file.getFileName()
                        + " docType=" + docType
                        + " parserOddzial=" + ParserRegistry.getInstance().getOddzial());
                } else {
                    // DS i inne -> DmsOfflineXmlBuilder
                    XmlSectionBuilder builder = new DmsOfflineXmlBuilder(docOut);
                    try {
                        if (builder instanceof XmlSectionBuilder) {
                            ((XmlSectionBuilder) builder).setIdKsiegOddzial(idKsieg);
                        }
                    } catch (Throwable t) {
                        log.warn(String.format("Failed to set idKsiegOddzial on builder for file=%s catch Throwable t=%s", file.getFileName(), t));
                    }
                    boolean ok = collectedSections.offer(builder);
                    log.info("41 TRACE-4 queued attempt: file=" + file.getFileName()
                        + " builderClass=" + (builder == null ? "null" : builder.getClass().getSimpleName())
                        + " offerResult=" + ok
                        + " collectedSections.size=" + collectedSections.size()
                        + " parserOddzial=" + ParserRegistry.getInstance().getOddzial());
                }

                // label
                String label = Optional.ofNullable(docOut.getInvoiceShortNumber()).filter(s -> !s.isBlank())
                                       .orElse(Optional.ofNullable(docOut.getInvoiceNumber()).orElse(fileName));
                label = label.replaceAll("[\\\\/:*?\"<>|]", "_");

                // DK, DZ, DS -> outDSDZ
                if (DocTypeConstants.DZ_TYPES.contains(docType) || DocTypeConstants.DS_TYPES.contains(docType)) {
                    outDSDZ.add(label);
                } else if (DocTypeConstants.DK_TYPES.contains(docType)) {
                    if (outCashCard != null) outCashCard.add(label);
                } else {
                    outOther.add(label);
                }
                log.info("42 TRACE-5 label added: file=" + file.getFileName()
                    + " label=" + label
                    + " outDSDZ.size=" + outDSDZ.size()
                    + " outOther.size=" + outOther.size()
                    + " parserOddzial=" + ParserRegistry.getInstance().getOddzial());

                if (genRes.parsedDocument != null) allParsedDocs.add(genRes.parsedDocument);
                collectedMessages.add(String.format("Przetworzono: %s typ=%s invoice=%s", fileName, docType, label));
            } else {
                log.info(String.format("43 DailyXmlWatcher: genRes.docOut is null for file=%s -> archiving", fileName));
            }
    		// TRACE: before archive move
    		log.info("43 TRACE-6 before archive: file=" + file.getFileName()
    		    + " collectedSections.size=" + collectedSections.size()
    		    + " collectedContractorSections.size=" + collectedContractorSections.size()
    		    + " allParsedDocs.size=" + allParsedDocs.size()
    		    + " collectedMessages.size=" + collectedMessages.size()
    		    + " parserOddzial=" + ParserRegistry.getInstance().getOddzial());

    		moveToArchiveRespectingCashCardRetention(file);
    		} catch (Exception e) {
    			log.error(String.format("43 DailyXmlWatcher: exception processing file=%s err=%s", file.getFileName(), e.getMessage()), e);
    			try {	
    				moveTo(file, outputDir.resolve("error"));
    			} catch (IOException ex) {
    				log.error(String.format("44 DailyXmlWatcher: failed to move file=%s to error: %s", file.getFileName(), ex.getMessage()), ex);
    			}
    		} finally {
    			log.info(String.format("44 TRACE-7 finally: filters=%s oddzial=%s thread=%s",
    			        ParserRegistry.getInstance().getFilters(),
    			        ParserRegistry.getInstance().getOddzial(),
    			        Thread.currentThread().getName()));

    		}
    	}
    	private void moveTo(Path file, Path targetDir) throws IOException {
	        Files.createDirectories(targetDir);
	        Path target = targetDir.resolve(file.getFileName());
	        Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
	        log.info(String.format("45 DailyXmlWatcher: moved file=%s to %s", file.getFileName(), targetDir));
	    }

    /** Prefiks nazwy jak w GROUP3 (KO/KZ/DK/RO/RZ/RD). */
    private static boolean isCashOrCardWatchFilename(Path file) {
        String name = file.getFileName().toString();
        if (name.length() < 2) return false;
        String two = name.substring(0, 2).toUpperCase(Locale.ROOT);
        return GROUP3.contains(two);
    }

    /** true gdy od mtime minęło ≥ minDays pełnych dni (UTC/porównanie Duration). */
    private static boolean isFileAtLeastDaysOld(Path file, int minDays) {
        try {
            long ageDays = Duration.between(Files.getLastModifiedTime(file).toInstant(), Instant.now()).toDays();
            return ageDays >= minDays;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Inne typy → od razu do archive. KO/KZ/DK/RO/RZ/RD → dopiero gdy plik ma ≥ {@code watcher.cashCardArchiveMinAgeDays} dni (mtime),
     * inaczej zostaje w watchDir (składanie raportów z wielu dni).
     */
    private void moveToArchiveRespectingCashCardRetention(Path file) throws IOException {
        int minDays = config.getCashCardArchiveMinAgeDays();
        if (minDays > 0 && isCashOrCardWatchFilename(file) && !isFileAtLeastDaysOld(file, minDays)) {
            log.info(String.format("45a DailyXmlWatcher: cash/card file left in watch (archive after %d days mtime, converter.properties): %s",
                    minDays, file.getFileName()));
            return;
        }
        moveTo(file, outputDir.resolve("archive"));
    }

    /**
     * Na końcu przebiegu: przenosi do archive pliki KO/KZ/DK/RO/RZ/RD starsze niż próg dni, które zostały wcześniej w watchDir.
     */
    private void archiveStaleCashCardFromWatch() {
        int minDays = config.getCashCardArchiveMinAgeDays();
        if (minDays <= 0) {
            return;
        }
        List<Path> files;
        try {
            files = collectXmlFiles();
        } catch (Throwable t) {
            log.error(String.format("archiveStaleCashCardFromWatch: collectXmlFiles failed: %s", t.getMessage()), t);
            return;
        }
        for (Path file : files) {
            if (!Files.isRegularFile(file)) continue;
            if (!isCashOrCardWatchFilename(file)) continue;
            if (!isFileAtLeastDaysOld(file, minDays)) continue;
            try {
                moveTo(file, outputDir.resolve("archive"));
                log.info(String.format("45b DailyXmlWatcher: archived stale cash/card (≥%d dni, converter.properties): %s",
                        minDays, file.getFileName()));
            } catch (IOException e) {
                log.warn(String.format("45b DailyXmlWatcher: could not archive %s: %s", file.getFileName(), e.getMessage()));
            }
        }
    }
   	private void persistPublishedFinalDocs(Queue<ContractorsXmlBuilder> contractorSections,
    	        Queue<XmlSectionBuilder> collectedSections,
    	        List<String> outSL,
    	        List<String> outDSDZ,
    	        List<String> outCashCard,
    	        List<String> outOther,
    	        Queue<String> collectedMessages,
    	        Queue<Object> allParsedDocs,
    	        boolean allowUpdate) {

    	    // 1) opcjonalnie: zapisz to, co generator mógł już opublikować
    	    if (this.generator != null) {
    	        log.info(String.format("46 DailyXmlWatcher: generator identity=%s", System.identityHashCode(this.generator)));
    	        List<DocumentOutGenerator.SerializedDoc> finalDocs = this.generator.drainFinalDocs();
    	        log.info(String.format("47 DailyXmlWatcher: polled %d finalDocs from generator", finalDocs.size()));
    	        for (DocumentOutGenerator.SerializedDoc sd : finalDocs) {
    	            try {
    	                Path out = outputDir.resolve(String.format("out_%s_%s.xml", sd.outputGroup, sd.timestamp));
    	                Files.writeString(out, sd.xml, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    	                log.info(String.format("48 DailyXmlWatcher: wrote finalDoc file %s", out.toString()));
    	            } catch (IOException e) {
    	                log.error(String.format("49 DailyXmlWatcher: failed to write finalDoc for group=%s ts=%s err=%s", sd.outputGroup, sd.timestamp, e.getMessage()), e);
    	            }
    	        }
    	    }

    	    // 2) snapshot kolekcji
    	    List<ContractorsXmlBuilder> slList = new ArrayList<>(contractorSections);
    	    List<XmlSectionBuilder> sectionsList = new ArrayList<>(collectedSections);
    	    List<String> messages = new ArrayList<>(collectedMessages == null ? List.of() : collectedMessages);

    	    List<DmsParsedDocument> parsedDocsForReports = new ArrayList<>();
    	    if (allParsedDocs != null) {
    	        for (Object o : allParsedDocs) {
    	            if (o instanceof DmsParsedDocument dp) parsedDocsForReports.add(dp);
    	        }
    	    }
    	    Set<String> reportNumbers = collectReportNumbersForAssembly(parsedDocsForReports);

    	    if (slList.isEmpty() && sectionsList.isEmpty() && reportNumbers.isEmpty()) {
    	        log.info("50 persistPublishedFinalDocs: nothing to publish (root empty)");
    	        messages.add("Brak sekcji do publikacji");
    	        messages.forEach(m -> log.info(String.format("persistPublishedFinalDocs msg=%s", m)));
    	        return;
    	    }

    	    try {
    	        TransformerFactory tf = TransformerFactory.newInstance();
    	        Transformer transformer = tf.newTransformer();

    	        // przygotuj id księgowe z registry (jednolity sposób)
    	        String watcherOddzial = ParserRegistry.getInstance().getOddzial();
    	        String idKsieg = "DMS_" + ("02".equals(watcherOddzial) ? "2" : "1");

    	        CashReportAssembler assemblerCash = new CashReportAssembler(parsedDocsForReports);
    	        CardReportAssembler assemblerCard = new CardReportAssembler(parsedDocsForReports);

    	        // Jeśli mamy tylko SL albo tylko sections, zachowaj dotychczasowe zachowanie (jeden dokument)
    	        if (!slList.isEmpty() && sectionsList.isEmpty()) {
    	            // tylko SL -> zbuduj jeden dokument (jak wcześniej)
    	            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    	            DocumentBuilder db = dbf.newDocumentBuilder();
    	            Document doc = db.newDocument();
    	            Element root = doc.createElement("ROOT");
    	            doc.appendChild(root);

    	            for (ContractorsXmlBuilder cb : slList) {
    	                try {
    	                    try {
    	                        if (cb instanceof XmlSectionBuilder) {
    	                            ((XmlSectionBuilder) cb).setIdKsiegOddzial(idKsieg);
    	                        } else {
    	                            try {
    	                                cb.getClass().getMethod("setIdKsiegOddzial", String.class).invoke(cb, idKsieg);
    	                            } catch (NoSuchMethodException ignore) { }
    	                        }
    	                    } catch (Throwable t) {
    	                        log.warn(String.format("persistPublishedFinalDocs: failed to set idKsiegOddzial on SL builder", t));
    	                    }
    	                    cb.build(doc, root);
    	                } catch (Throwable t) {
    	                    log.error("persistPublishedFinalDocs: error building SL section", t);
    	                }
    	            }

    	            // serializacja i zapis (jeden plik)
    	            StringWriter writer = new StringWriter();
    	            transformer.transform(new DOMSource(doc), new StreamResult(writer));
    	            String xmlString = writer.toString();

    	            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    	            String fileNameOut = "SL_" + ts + ".xml";
    	            Path outPath = outputDir.resolve(fileNameOut);
    	            Files.writeString(outPath, xmlString, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    	            log.info("51 persistPublishedFinalDocs: published finalDoc " + outPath);
    	            if (outSL != null) {
    	                messages.addAll(outSL.stream().map(s -> "Dodano SL: " + s).collect(Collectors.toList()));
    	            }
    	            messages.add("Opublikowano finalDoc: " + fileNameOut);
    	            // koniec przypadku tylko SL
    	        } else if (slList.isEmpty() && !sectionsList.isEmpty()) {
    	            // tylko DS/DZ/Other -> zbuduj jeden dokument (jak wcześniej)
    	            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    	            DocumentBuilder db = dbf.newDocumentBuilder();
    	            Document doc = db.newDocument();
    	            Element root = doc.createElement("ROOT");
    	            doc.appendChild(root);

    	            for (XmlSectionBuilder b : sectionsList) {
    	                try {
    	                    try {
    	                        if (b instanceof XmlSectionBuilder) {
    	                            ((XmlSectionBuilder) b).setIdKsiegOddzial(idKsieg);
    	                        } else {
    	                            try {
    	                                b.getClass().getMethod("setIdKsiegOddzial", String.class).invoke(b, idKsieg);
    	                            } catch (NoSuchMethodException ignore) { }
    	                        }
    	                    } catch (Throwable t) {
    	                        log.warn(String.format("persistPublishedFinalDocs: failed to set idKsiegOddzial on DS/DZ builder", t));
    	                    }
    	                    b.build(doc, root);
    	                } catch (Throwable t) {
    	                    log.error("persistPublishedFinalDocs: error building DS/DZ section", t);
    	                }
    	            }

    	            StringWriter writer = new StringWriter();
    	            transformer.transform(new DOMSource(doc), new StreamResult(writer));
    	            String xmlString = writer.toString();

    	            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    	            String fileNameOut = "DZ_DS_" + ts + ".xml";
    	            Path outPath = outputDir.resolve(fileNameOut);
    	            Files.writeString(outPath, xmlString, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    	            log.info("51 persistPublishedFinalDocs: published finalDoc " + outPath);
    	            if (outDSDZ != null) {
    	                messages.addAll(outDSDZ.stream().map(s -> "Dodano DS/DZ: " + s).collect(Collectors.toList()));
    	            }
    	            if (outCashCard != null) {
    	                messages.addAll(outCashCard.stream().map(s -> "Zarejestrowano KO/KZ/DK/RO/RZ/RD: " + s).collect(Collectors.toList()));
    	            }
    	            messages.add("Opublikowano finalDoc: " + fileNameOut);
    	            // koniec przypadku tylko DS/DZ
    	        } else {
    	            // mamy jednocześnie SL i sections -> zbuduj i zapisz DWA osobne dokumenty (minimalna zmiana)
    	            // 3A) SL
    	            DocumentBuilderFactory dbfSL = DocumentBuilderFactory.newInstance();
    	            DocumentBuilder dbSL = dbfSL.newDocumentBuilder();
    	            Document docSL = dbSL.newDocument();
    	            Element rootSL = docSL.createElement("ROOT");
    	            docSL.appendChild(rootSL);

    	            for (ContractorsXmlBuilder cb : slList) {
    	                try {
    	                    try {
    	                        if (cb instanceof XmlSectionBuilder) {
    	                            ((XmlSectionBuilder) cb).setIdKsiegOddzial(idKsieg);
    	                        } else {
    	                            try {
    	                                cb.getClass().getMethod("setIdKsiegOddzial", String.class).invoke(cb, idKsieg);
    	                            } catch (NoSuchMethodException ignore) { }
    	                        }
    	                    } catch (Throwable t) {
    	                        log.warn(String.format("persistPublishedFinalDocs: failed to set idKsiegOddzial on SL builder", t));
    	                    }
    	                    cb.build(docSL, rootSL);
    	                } catch (Throwable t) {
    	                    log.error("persistPublishedFinalDocs: error building SL section", t);
    	                }
    	            }

    	            StringWriter writerSL = new StringWriter();
    	            transformer.transform(new DOMSource(docSL), new StreamResult(writerSL));
    	            String xmlSL = writerSL.toString();

    	            String tsSL = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    	            String fileNameOutSL = "SL_" + tsSL + ".xml";
    	            Path outPathSL = outputDir.resolve(fileNameOutSL);
    	            Files.writeString(outPathSL, xmlSL, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    	            log.info("51 persistPublishedFinalDocs: published finalDoc " + outPathSL);
    	            if (outSL != null) {
    	                messages.addAll(outSL.stream().map(s -> "Dodano SL: " + s).collect(Collectors.toList()));
    	            }
    	            messages.add("Opublikowano finalDoc: " + fileNameOutSL);

    	            // 3B) DS/DZ
    	            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    	            DocumentBuilder db = dbf.newDocumentBuilder();
    	            Document doc = db.newDocument();
    	            Element root = doc.createElement("ROOT");
    	            doc.appendChild(root);

    	            for (XmlSectionBuilder b : sectionsList) {
    	                try {
    	                    try {
    	                        if (b instanceof XmlSectionBuilder) {
    	                            ((XmlSectionBuilder) b).setIdKsiegOddzial(idKsieg);
    	                        } else {
    	                            try {
    	                                b.getClass().getMethod("setIdKsiegOddzial", String.class).invoke(b, idKsieg);
    	                            } catch (NoSuchMethodException ignore) { }
    	                        }
    	                    } catch (Throwable t) {
    	                        log.warn(String.format("persistPublishedFinalDocs: failed to set idKsiegOddzial on DS/DZ builder", t));
    	                    }
    	                    b.build(doc, root);
    	                } catch (Throwable t) {
    	                    log.error("persistPublishedFinalDocs: error building DS/DZ section", t);
    	                }
    	            }

    	            StringWriter writer = new StringWriter();
    	            transformer.transform(new DOMSource(doc), new StreamResult(writer));
    	            String xmlString = writer.toString();

    	            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    	            String fileNameOut = "DZ_DS_" + ts + ".xml";
    	            Path outPath = outputDir.resolve(fileNameOut);
    	            Files.writeString(outPath, xmlString, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    	            log.info("51 persistPublishedFinalDocs: published finalDoc " + outPath);
    	            if (outDSDZ != null) {
    	                messages.addAll(outDSDZ.stream().map(s -> "Dodano DS/DZ: " + s).collect(Collectors.toList()));
    	            }
    	            messages.add("Opublikowano finalDoc: " + fileNameOut);

    	        }

    	        if (!reportNumbers.isEmpty()) {
    	            log.info(String.format("persistPublishedFinalDocs: assembling cash/card reports reportNumbers=%s", reportNumbers));
    	            buildAndWriteReports(reportNumbers, assemblerCash, assemblerCard, idKsieg, transformer, messages);
    	        }

    	        try {
    	            converterService.registerClosedCashAndCardReports(parsedDocsForReports, allowUpdate);
    	        } catch (Exception ex) {
    	            log.error(String.format("persistPublishedFinalDocs: registerClosedCashAndCardReports failed: %s", ex.getMessage()), ex);
    	            messages.add("Błąd zapisu rejestru raportów (KO/KZ/DK, RO/RZ/RD): " + ex.getMessage());
    	        }

    	    } catch (Exception e) {
    	        log.error("52 persistPublishedFinalDocs: unexpected error while building finalDoc", e);
    	        messages.add("Błąd składania finalDoc: " + e.getMessage());
    	    }

    	    // 6) wypisz komunikaty
    	    messages.forEach(m -> log.info(String.format("53 persistPublishedFinalDocs msg=%s", m)));
    	}

    /** Numery raportów (KO/RO/RD) do składania raportów — jak w ConverterServlet. */
    private static Set<String> collectReportNumbersForAssembly(List<DmsParsedDocument> parsedDocs) {
        Set<String> reportNumbers = new HashSet<>();
        if (parsedDocs == null) return reportNumbers;
        for (DmsParsedDocument d : parsedDocs) {
            String dt = d.getDocumentType();
            if (dt == null) continue;
            if ("KO".equalsIgnoreCase(dt) || "RO".equalsIgnoreCase(dt) || "RD".equalsIgnoreCase(dt)) {
                String rn = d.getReportNumber();
                if (rn != null && !rn.isBlank()) reportNumbers.add(rn);
            }
        }
        return reportNumbers;
    }

    private void writeGroupedOutputs(List<String> outSL, List<String> outDSDZ, List<String> outCashCard, List<String> outOther) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String ts = LocalDateTime.now().format(fmt);
        log.info(String.format("54 About to write outputs: outSL=%d outDSDZ=%d outCashCard=%d outOther=%d",
                outSL.size(), outDSDZ.size(), outCashCard == null ? 0 : outCashCard.size(), outOther.size()));
        if (!outSL.isEmpty()) writeOutputFile("SL", outSL, ts);
        if (!outDSDZ.isEmpty()) writeOutputFile("DS_DZ", outDSDZ, ts);
        if (outCashCard != null && !outCashCard.isEmpty()) writeOutputFile("CASH_CARD", outCashCard, ts);
        if (!outOther.isEmpty()) writeOutputFile("OTHER", outOther, ts);
    }
    public void shutdown() {
        scheduler.shutdownNow();
        workerPool.shutdownNow();
        log.info("DailyXmlWatcher shutdown requested");
    }
    /**
     * Pomocnicza metoda: buduje raporty (KO/RO i kartowe) i zapisuje je jako KO_RO_yyyyMMdd_HHmmss.xml
     * - reportNumbers: zebrane numery raportów (KO/RO)
     * - assemblerCash / assemblerCard: używane do budowy DmsDocumentOut raportów
     * - idKsieg: ustawiane na root builderach
     * - transformer: używany do serializacji
     * - messages: lista komunikatów do dopisania
     */
    private void buildAndWriteReports(Set<String> reportNumbers,
                                      CashReportAssembler assemblerCash,
                                      CardReportAssembler assemblerCard,
                                      String idKsieg,
                                      Transformer transformer,
                                      List<String> messages) {
        if (reportNumbers == null || reportNumbers.isEmpty()) return;
        try {
            DocumentBuilderFactory dbfR = DocumentBuilderFactory.newInstance();
            DocumentBuilder dbR = dbfR.newDocumentBuilder();
            Document docR = dbR.newDocument();
            Element rootR = docR.createElement("ROOT");
            docR.appendChild(rootR);

            boolean anyReportAdded = false;
            for (String nr : reportNumbers) {
                // cash report
                DmsDocumentOut rapCash = assemblerCash.buildSingleReport(nr);
                if (rapCash != null) {
                    try {
                        CashReportXmlBuilder crb = new CashReportXmlBuilder(rapCash);
                        try {
                            if (crb instanceof XmlSectionBuilder) {
                                ((XmlSectionBuilder) crb).setIdKsiegOddzial(idKsieg);
                            }
                        } catch (Throwable t) {
                            log.warn(String.format("buildAndWriteReports: failed to set idKsiegOddzial on CashReportXmlBuilder", t));
                        }
                        crb.build(docR, rootR);
                        // rozliczenia
                        RozliczeniaXmlBuilder roz = new RozliczeniaXmlBuilder(rapCash);
                        try {
                            if (roz instanceof XmlSectionBuilder) {
                                ((XmlSectionBuilder) roz).setIdKsiegOddzial(idKsieg);
                            }
                        } catch (Throwable t) {
                            log.warn(String.format("buildAndWriteReports: failed to set idKsiegOddzial on RozliczeniaXmlBuilder", t));
                        }
                        roz.build(docR, rootR);

                        messages.add("Dodano RAPORT_KB: " + nr);
                        log.info(String.format("persistPublishedFinalDocs: RAPORT_KB added nr=%s", nr));
                        anyReportAdded = true;
                    } catch (Throwable t) {
                        log.error("persistPublishedFinalDocs: error building RAPORT_KB for " + nr, t);
                        messages.add("Błąd RAPORT_KB: " + nr + " -> " + t.getMessage());
                    }
                } else {
                    log.info("persistPublishedFinalDocs: Raport kasowy " + nr + " pominięty - brak KO lub KZ lub raport niekompletny");
                    messages.add("Pominięto raport: " + nr + " (niekompletny)");
                }

                // card report
                DmsDocumentOut rapCard = assemblerCard.buildSingleReport(nr);
                if (rapCard != null) {
                    try {
                        CashReportXmlBuilder crb2 = new CashReportXmlBuilder(rapCard);
                        try {
                            if (crb2 instanceof XmlSectionBuilder) {
                                ((XmlSectionBuilder) crb2).setIdKsiegOddzial(idKsieg);
                            }
                        } catch (Throwable t) {
                            log.warn(String.format("buildAndWriteReports: failed to set idKsiegOddzial on CashReportXmlBuilder (card)", t));
                        }
                        crb2.build(docR, rootR);
                        RozliczeniaXmlBuilder roz2 = new RozliczeniaXmlBuilder(rapCard);
                        try {
                            if (roz2 instanceof XmlSectionBuilder) {
                                ((XmlSectionBuilder) roz2).setIdKsiegOddzial(idKsieg);
                            }
                        } catch (Throwable t) {
                            log.warn(String.format("buildAndWriteReports: failed to set idKsiegOddzial on RozliczeniaXmlBuilder (card)", t));
                        }
                        roz2.build(docR, rootR);

                        messages.add("Dodano RAPORT_KARTOWY: " + nr);
                        log.info(String.format("persistPublishedFinalDocs: RAPORT_KARTOWY added nr=%s", nr));
                        anyReportAdded = true;
                    } catch (Throwable t) {
                        log.error("persistPublishedFinalDocs: error building RAPORT_KARTOWY for " + nr, t);
                        messages.add("Błąd RAPORT_KARTOWY: " + nr + " -> " + t.getMessage());
                    }
                } else {
                    log.debug("persistPublishedFinalDocs: Raport kartowy " + nr + " pominięty - brak RO/RZ lub niekompletny");
                }
            }

            if (anyReportAdded) {
                StringWriter writerR = new StringWriter();
                transformer.transform(new DOMSource(docR), new StreamResult(writerR));
                String xmlR = writerR.toString();

                String tsR = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String fileNameOutR = "KO_RO_" + tsR + ".xml";
                Path outPathR = outputDir.resolve(fileNameOutR);
                Files.writeString(outPathR, xmlR, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                log.info("persistPublishedFinalDocs: published reports finalDoc " + outPathR);
                messages.add("Opublikowano finalDoc (raporty): " + fileNameOutR);
                if (this.generator != null) {
                    try {
                        this.generator.publishFinalDoc("KO_RO", xmlR, tsR);
                        log.info(String.format("buildAndWriteReports: published to generator outputGroup=KO_RO ts=%s", tsR));
                    } catch (Exception ex) {
                        log.error("buildAndWriteReports: failed to publish KO_RO to generator", ex);
                    }
                }
            }

        } catch (Exception e) {
            log.error("persistPublishedFinalDocs: error while building/writing reports", e);
            messages.add("Błąd budowy raportów: " + e.getMessage());
        }
    }
}


