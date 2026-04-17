package com.example.sydneyinfo.model;

public class ParkingInfo {
    private String facilityName;
    private int totalSpots;
    private int availableSpots;
    private String lastUpdated;
    private boolean dataAvailable;
    private String errorMessage;

    public static ParkingInfo error(String message) {
        ParkingInfo p = new ParkingInfo();
        p.dataAvailable = false;
        p.errorMessage = message;
        return p;
    }

    public double getAvailabilityPercent() {
        if (totalSpots == 0) return 0;
        return 100.0 * availableSpots / totalSpots;
    }

    public String getStatusClass() {
        double avail = getAvailabilityPercent();
        if (avail > 50) return "success";
        if (avail > 20) return "warning";
        return "danger";
    }

    public String getStatusText() {
        double avail = getAvailabilityPercent();
        if (avail > 50) return "Available";
        if (avail > 20) return "Limited";
        if (availableSpots == 0) return "Full";
        return "Almost Full";
    }

    public String getFacilityName() { return facilityName; }
    public void setFacilityName(String facilityName) { this.facilityName = facilityName; }
    public int getTotalSpots() { return totalSpots; }
    public void setTotalSpots(int totalSpots) { this.totalSpots = totalSpots; }
    public int getAvailableSpots() { return availableSpots; }
    public void setAvailableSpots(int availableSpots) { this.availableSpots = availableSpots; }
    public String getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(String lastUpdated) { this.lastUpdated = lastUpdated; }
    public boolean isDataAvailable() { return dataAvailable; }
    public void setDataAvailable(boolean dataAvailable) { this.dataAvailable = dataAvailable; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
