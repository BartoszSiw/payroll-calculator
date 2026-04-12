package pl.edashi.converter.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ConverterConfig {

	    private final Properties props = new Properties();
	    private final Path configPath = Path.of(System.getProperty("user.home"), "converter.properties");
	    
	    public ConverterConfig() {
	        try (InputStream in = Files.newInputStream(configPath)) {
	            props.load(in);
	        } catch (Exception e) {
	            // fallback defaults
	        }
	        props.putIfAbsent("watcher.day", "1");
	        props.putIfAbsent("watcher.hour", "10");
	        props.putIfAbsent("watcher.minute", "36");
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
	}
