package com.example.sydneyinfo.model;

import java.util.List;

public class LocationWeather {
    private String locationName;
    private List<DayForecast> forecasts;
    private double currentTemp;
    private int currentWeatherCode;
    private boolean dataAvailable;
    private String errorMessage;

    public static LocationWeather error(String locationName, String message) {
        LocationWeather w = new LocationWeather();
        w.locationName = locationName;
        w.dataAvailable = false;
        w.errorMessage = message;
        return w;
    }

    public String getCurrentTempFormatted() {
        return String.format("%.1f", currentTemp);
    }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public List<DayForecast> getForecasts() { return forecasts; }
    public void setForecasts(List<DayForecast> forecasts) { this.forecasts = forecasts; }
    public double getCurrentTemp() { return currentTemp; }
    public void setCurrentTemp(double currentTemp) { this.currentTemp = currentTemp; }
    public int getCurrentWeatherCode() { return currentWeatherCode; }
    public void setCurrentWeatherCode(int currentWeatherCode) { this.currentWeatherCode = currentWeatherCode; }
    public boolean isDataAvailable() { return dataAvailable; }
    public void setDataAvailable(boolean dataAvailable) { this.dataAvailable = dataAvailable; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
