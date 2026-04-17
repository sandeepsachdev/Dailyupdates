package com.example.sydneyinfo.model;

import java.util.List;

public class FuelInfo {
    private List<FuelPrice> prices;
    private List<LocalFuelStation> localStations;
    private boolean dataAvailable;
    private String errorMessage;
    private String region;

    public static FuelInfo error(String message) {
        FuelInfo f = new FuelInfo();
        f.dataAvailable = false;
        f.errorMessage = message;
        return f;
    }

    public List<FuelPrice> getPrices() { return prices; }
    public void setPrices(List<FuelPrice> prices) { this.prices = prices; }
    public List<LocalFuelStation> getLocalStations() { return localStations; }
    public void setLocalStations(List<LocalFuelStation> localStations) { this.localStations = localStations; }
    public boolean isDataAvailable() { return dataAvailable; }
    public void setDataAvailable(boolean dataAvailable) { this.dataAvailable = dataAvailable; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
}
