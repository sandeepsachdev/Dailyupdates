package com.example.sydneyinfo.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * One day of the E10 price-trend chart: the Sydney metro {@code average} and the
 * Ampol Foodary Cherrybrook price ({@code local}, which may be {@code null} on days
 * the local station price wasn't recorded).
 */
public class E10TrendPoint {

    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("d MMM");

    private final LocalDate date;
    private final double average;
    private final Double local;

    public E10TrendPoint(LocalDate date, double average, Double local) {
        this.date = date;
        this.average = average;
        this.local = local;
    }

    public LocalDate getDate() { return date; }

    /** Short x-axis label, e.g. "14 Jun". */
    public String getLabel() { return date.format(LABEL_FMT); }

    /** Sydney metro average E10 price. */
    public double getAverage() { return average; }

    /** Ampol Foodary Cherrybrook E10 price, or {@code null} if not recorded that day. */
    public Double getLocal() { return local; }
}
