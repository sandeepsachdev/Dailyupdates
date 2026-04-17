package com.example.sydneyinfo.model;

public class LocalFuelPrice {
    private String fuelType;
    private String fuelTypeLabel;
    private double price;

    public String getFormatted() {
        return String.format("%.1f", price);
    }

    public String getFuelType() { return fuelType; }
    public void setFuelType(String fuelType) { this.fuelType = fuelType; }
    public String getFuelTypeLabel() { return fuelTypeLabel; }
    public void setFuelTypeLabel(String fuelTypeLabel) { this.fuelTypeLabel = fuelTypeLabel; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}
