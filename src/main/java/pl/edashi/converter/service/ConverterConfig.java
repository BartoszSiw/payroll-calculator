package pl.edashi.converter.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ConverterConfig {

	    /** Defaults when {@code rejestr.jdbc.*} keys are absent (override in {@code %USER_HOME%/converter.properties}). */
	    private static final String DEFAULT_REJESTR_JDBC_URL =
	            "jdbc:sqlserver://:;databaseName=CONVERTER;encrypt=false;trustServerCertificate=true";
	    private static final String DEFAULT_REJESTR_JDBC_USER = "as";
	    private static final String DEFAULT_REJESTR_JDBC_PASSWORD = "ssssss";

	    private final Properties props = new Properties();
	    private final Path configPath = Path.of(System.getProperty("user.home"), "converter.properties");
	    private final boolean loadedFromFile;

	    public ConverterConfig() {
	        boolean ok = false;
	        try (InputStream in = Files.newInputStream(configPath)) {
	            props.load(in);
	            ok = true;
	        } catch (Exception e) {
	            // fallback defaults — file often missing until user creates %USER_HOME%/converter.properties
	        }
	        this.loadedFromFile = ok;
	        props.putIfAbsent("watcher.day", "1");
	        props.putIfAbsent("watcher.hour", "10");
	        props.putIfAbsent("watcher.minute", "36");
	        // Minimalny wiek pliku KO/KZ/DK/RO/RZ/RD (dni wg mtime) przed archive. 0 = od razu jak pozostałe typy.
	        props.putIfAbsent("watcher.cashCardArchiveMinAgeDays", "3");
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
	            int v = Integer.parseInt(props.getProperty("watcher.cashCardArchiveMinAgeDays", "3").trim());
	            if (v < 0) return 0;
	            if (v > 366) return 366;
	            return v;
	        } catch (Exception e) {
	            return 3;
	        }
	    }
	}
