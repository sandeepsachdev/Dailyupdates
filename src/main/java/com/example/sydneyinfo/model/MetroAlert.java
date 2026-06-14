package com.example.sydneyinfo.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class MetroAlert {
    private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");
    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("EEE d MMM").withZone(SYDNEY);

    private String header;
    private String description;
    private String effect;
    private String cause;
    private Long activePeriodStart;   // epoch seconds, may be null
    private Long activePeriodEnd;     // epoch seconds, may be null

    public String getSeverityClass() {
        if (effect == null) return "warning";
        String e = effect.toUpperCase();
        if (e.contains("NO_SERVICE") || e.contains("STOP_MOVED")) return "danger";
        if (e.contains("REDUCED") || e.contains("SIGNIFICANT")) return "warning";
        return "info";
    }

    /**
     * A human-readable date range for the alert's active period, or null when the
     * feed provides no timing information. Examples:
     *   "From Mon 18 May"            (start only)
     *   "Until Wed 20 May"           (end only)
     *   "Mon 18 May – Wed 20 May"    (both)
     */
    public String getDateRange() {
        boolean hasStart = activePeriodStart != null && activePeriodStart > 0;
        boolean hasEnd = activePeriodEnd != null && activePeriodEnd > 0;
        if (!hasStart && !hasEnd) return null;

        String start = hasStart ? DATE_FMT.format(Instant.ofEpochSecond(activePeriodStart)) : null;
        String end = hasEnd ? DATE_FMT.format(Instant.ofEpochSecond(activePeriodEnd)) : null;

        if (hasStart && hasEnd) {
            return start.equals(end) ? start : start + " – " + end;
        }
        return hasStart ? "From " + start : "Until " + end;
    }

    public String getHeader() { return header; }
    public void setHeader(String header) { this.header = header; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEffect() { return effect; }
    public void setEffect(String effect) { this.effect = effect; }
    public String getCause() { return cause; }
    public void setCause(String cause) { this.cause = cause; }
    public Long getActivePeriodStart() { return activePeriodStart; }
    public void setActivePeriodStart(Long activePeriodStart) { this.activePeriodStart = activePeriodStart; }
    public Long getActivePeriodEnd() { return activePeriodEnd; }
    public void setActivePeriodEnd(Long activePeriodEnd) { this.activePeriodEnd = activePeriodEnd; }
}
