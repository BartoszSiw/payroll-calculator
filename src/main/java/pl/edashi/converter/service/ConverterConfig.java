package pl.edashi.converter.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConverterConfig {

	    private static final Logger log = LoggerFactory.getLogger(ConverterConfig.class);

	    /** Defaults when {@code rejestr.jdbc.*} keys are absent (override in {@code %USER_HOME%/converter.properties}). */
	    private static final String DEFAULT_REJESTR_JDBC_URL =
	            "jdbc:sqlserver://:;databaseName=CONVERTER;encrypt=false;trustServerCertificate=true";
	    private static final String DEFAULT_REJESTR_JDBC_USER = "as";
	    private static final String DEFAULT_REJESTR_JDBC_PASSWORD = "ssssss";

	    private final Properties props = new Properties();
	    private final Path configPath;
	    private final boolean loadedFromFile;

	    public ConverterConfig() {
	        this((String) null);
	    }

	    /** @param rawConfigPath override path (e.g. from web.xml context-param) */
	    public ConverterConfig(String rawConfigPath) {
	        boolean ok = false;
	        this.configPath = resolveConfigPath(rawConfigPath);
	        if (this.configPath != null) {
	            try (InputStream in = Files.newInputStream(configPath)) {
	                props.load(in);
	                ok = true;
	            } catch (Exception e) {
	                // fallback defaults — file may be missing or inaccessible for service user
	                try {
	                    log.warn("ConverterConfig: failed to load properties from path=%s exists=%s readable=%s err=%s",
	                            configPath.toAbsolutePath(),
	                            Files.exists(configPath),
	                            Files.isReadable(configPath),
	                            String.valueOf(e.getMessage()));
	                } catch (Throwable ignored) {
	                    // avoid failing startup on logging
	                }
	            }
	        }
	        this.loadedFromFile = ok;
	        props.putIfAbsent("watcher.day", "1");
	        props.putIfAbsent("watcher.hour", "09");
	        props.putIfAbsent("watcher.minute", "00");
	        // Minimalny wiek pliku KO/KZ/DK/RO/RZ/RD (dni wg mtime) przed archive. 0 = od razu jak pozostałe typy.
	        props.putIfAbsent("watcher.cashCardArchiveMinAgeDays", "3");
	    }

	    private static Path resolveConfigPath(String rawConfigPath) {
	        // Priority:
	        // 1) explicit ctor arg (typically ServletContext init-param from web.xml)
	        // 2) -DconfigPath=...
	        // 3) CONFIG_PATH env var
	        // 4) %USER_HOME%/converter.properties (often LocalService profile when running Tomcat as a service)
	        String raw = rawConfigPath;
	        if (raw == null || raw.isBlank()) {
	            raw = System.getProperty("configPath");
	        }
	        if (raw == null || raw.isBlank()) {
	            raw = System.getenv("CONFIG_PATH");
	        }
	        if (raw != null && !raw.isBlank()) {
	            try {
	                return Path.of(raw.trim());
	            } catch (Exception ignored) {
	                // fall through to default
	            }
	        }
	        // Default: %USER_HOME%/converter.properties (may be service profile if running as service)
	        String home = System.getProperty("user.home");
	        if (home == null || home.isBlank()) {
	            // last-resort: current directory
	            return Path.of("converter.properties");
	        }
	        return Path.of(home.trim(), "converter.properties");
	    }

	    /**
	     * JDBC URL for {@link pl.edashi.common.dao.RejestrDaoImpl} (SQL Server), oddział {@code 01}.
	     * Property: {@code rejestr.jdbc.url}
	     */
	    public String getRejestrJdbcUrl() {
	        return getRejestrJdbcUrl("01");
	    }

	    /**
	     * JDBC URL wg oddziału: {@code 01} → {@code rejestr.jdbc.url}, {@code 02} → {@code rejestr.jdbc.url.02}
	     * (jeśli brak .02, ten sam co dla 01).
	     */
	    public String getRejestrJdbcUrl(String oddzial) {
	        String o = oddzial == null ? "" : oddzial.trim();
	        if ("02".equals(o)) {
	            String v2 = props.getProperty("rejestr.jdbc.url.02");
	            if (v2 != null && !v2.isBlank()) {
	                return v2.trim();
	            }
	        }
	        String v = props.getProperty("rejestr.jdbc.url", DEFAULT_REJESTR_JDBC_URL);
	        return v == null ? DEFAULT_REJESTR_JDBC_URL : v.trim();
	    }

	    /** Property: {@code rejestr.jdbc.user} */
	    public String getRejestrJdbcUser() {
	        String v = props.getProperty("rejestr.jdbc.user", DEFAULT_REJESTR_JDBC_USER);
	        return v == null ? DEFAULT_REJESTR_JDBC_USER : v.trim();
	    }

	    /** Property: {@code rejestr.jdbc.password} */
	    public String getRejestrJdbcPassword() {
	        String v = props.getProperty("rejestr.jdbc.password", DEFAULT_REJESTR_JDBC_PASSWORD);
	        return v == null ? DEFAULT_REJESTR_JDBC_PASSWORD : v;
	    }

	    /** Property: {@code auth.username} (default: "admin") */
	    public String getAuthUsername() {
	        String v = props.getProperty("auth.username", "admin");
	        return v == null ? "admin" : v.trim();
	    }

	    /** Property: {@code auth.password} (default: "admin") */
	    public String getAuthPassword() {
	        String v = props.getProperty("auth.password", "admin");
	        return v == null ? "admin" : v;
	    }

	    /** Where this app looks for settings (not src/main/resources). */
	    public Path getConfigPath() {
	        return configPath;
	    }

	    public boolean isLoadedFromFile() {
	        return loadedFromFile;
	    }
	    public int getWatcherDay() {
	        try {
	            return Integer.parseInt(props.getProperty("watcher.day"));
	        } catch (Exception e) {
	            return 1;
	        }
	    }
	    public int getWatcherHour() {
	        try {
	            return Integer.parseInt(props.getProperty("watcher.hour"));
	        } catch (Exception e) {
	            return 10;
	        }
	    }

	    public int getWatcherMinute() {
	        try {
	            return Integer.parseInt(props.getProperty("watcher.minute"));
	        } catch (Exception e) {
	            return 36;
	        }
	    }

	    /**
	     * Z {@code watcher.cashCardArchiveMinAgeDays} w {@code converter.properties} (katalog {@code user.home}).
	     * Liczba pełnych dni od {@code lastModified} pliku, po której można przenieść XML raportu kasowego/kartowego do archive.
	     */
	    public int getCashCardArchiveMinAgeDays() {
	        try {
	            int v = Integer.parseInt(props.getProperty("watcher.cashCardArchiveMinAgeDays", "5").trim());
	            if (v < 0) return 0;
	            if (v > 366) return 366;
	            return v;
	        } catch (Exception e) {
	            return 3;
	        }
	    }
	}
