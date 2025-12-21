package pl.edashi.common.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AppLogger {

    private final String module;

    public AppLogger(String module) {
        this.module = module;
    }

    private void log(String level, String msg) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("[" + time + "][" + module + "][" + level + "] " + msg);
    }

    public void info(String msg) { log("INFO", msg); }
    public void warn(String msg) { log("WARN", msg); }
    public void error(String msg) { log("ERROR", msg); }
}

