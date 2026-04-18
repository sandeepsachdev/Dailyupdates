package com.example.sydneyinfo.model;

import java.util.List;

public class EventInfo {
    private List<SydneyEvent> events;
    private boolean dataAvailable;
    private String errorMessage;

    public static EventInfo error(String message) {
        EventInfo e = new EventInfo();
        e.dataAvailable = false;
        e.errorMessage = message;
        return e;
    }

    public List<SydneyEvent> getEvents() { return events; }
    public void setEvents(List<SydneyEvent> events) { this.events = events; }
    public boolean isDataAvailable() { return dataAvailable; }
    public void setDataAvailable(boolean dataAvailable) { this.dataAvailable = dataAvailable; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
