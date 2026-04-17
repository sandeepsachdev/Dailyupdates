package com.example.sydneyinfo.model;

public class MetroAlert {
    private String header;
    private String description;
    private String effect;
    private String cause;

    public String getSeverityClass() {
        if (effect == null) return "warning";
        String e = effect.toUpperCase();
        if (e.contains("NO_SERVICE") || e.contains("STOP_MOVED")) return "danger";
        if (e.contains("REDUCED") || e.contains("SIGNIFICANT")) return "warning";
        return "info";
    }

    public String getHeader() { return header; }
    public void setHeader(String header) { this.header = header; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEffect() { return effect; }
    public void setEffect(String effect) { this.effect = effect; }
    public String getCause() { return cause; }
    public void setCause(String cause) { this.cause = cause; }
}
