package pl.edashi.converter.service;

import java.time.LocalDate;

public class DateFilterRegistry {

    private static final DateFilterRegistry INSTANCE = new DateFilterRegistry();

    private LocalDate fromDate;
    private LocalDate toDate;

    private DateFilterRegistry() {}

    public static DateFilterRegistry getInstance() {
        return INSTANCE;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public boolean hasFilter() {
        return fromDate != null || toDate != null;
    }
}

