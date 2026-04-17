package com.example.sydneyinfo.model;

public class FuelPrice {
    private String fuelType;
    private String fuelTypeLabel;
    private double averagePrice;
    private double minPrice;
    private double maxPrice;
    private int stationCount;

    public String getFormattedAverage() {
        return String.format("%.1f", averagePrice);
    }

    public String getFormattedMin() {
        return String.format("%.1f", minPrice);
    }

    public String getFormattedMax() {
        return String.format("%.1f", maxPrice);
    }

    public String getFuelType() { return fuelType; }
    public void setFuelType(String fuelType) { this.fuelType = fuelType; }
    public String getFuelTypeLabel() { return fuelTypeLabel; }
    public void setFuelTypeLabel(String fuelTypeLabel) { this.fuelTypeLabel = fuelTypeLabel; }
    public double getAveragePrice() { return averagePrice; }
    public void setAveragePrice(double averagePrice) { this.averagePrice = averagePrice; }
    public double getMinPrice() { return minPrice; }
    public void setMinPrice(double minPrice) { this.minPrice = minPrice; }
    public double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(double maxPrice) { this.maxPrice = maxPrice; }
    public int getStationCount() { return stationCount; }
    public void setStationCount(int stationCount) { this.stationCount = stationCount; }
}
