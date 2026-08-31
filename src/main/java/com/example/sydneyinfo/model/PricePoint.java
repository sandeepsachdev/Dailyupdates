package com.example.sydneyinfo.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * A single point in a fuel-price history series: the date of a snapshot and the
 * average price recorded that day. Used to build the E10 price-trend chart.
 */
public class PricePoint {

    private static final DateTimeFormatter LABEL_FMT = DateTimeFormatter.ofPattern("d MMM");

    private final LocalDate date;
    private final double price;

    public PricePoint(LocalDate date, double price) {
        this.date = date;
        this.price = price;
    }

    public LocalDate getDate() { return date; }

    /** Short x-axis label, e.g. "14 Jun". */
    public String getLabel() { return date.format(LABEL_FMT); }

    public double getPrice() { return price; }
}
