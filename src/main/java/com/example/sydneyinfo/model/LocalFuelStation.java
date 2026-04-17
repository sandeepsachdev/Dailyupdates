package com.example.sydneyinfo.model;

import java.util.List;

public class LocalFuelStation {
    private String stationCode;
    private String name;
    private String brand;
    private String address;
    private List<LocalFuelPrice> prices;

    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public List<LocalFuelPrice> getPrices() { return prices; }
    public void setPrices(List<LocalFuelPrice> prices) { this.prices = prices; }
}
