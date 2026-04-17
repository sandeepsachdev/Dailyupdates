package com.example.sydneyinfo.model;

import java.util.List;

public class LocationWeather {
    private String locationName;
    private List<DayForecast> forecasts;
    private boolean dataAvailable;
    private String errorMessage;

    public static LocationWeather error(String locationName, String message) {
        LocationWeather w = new LocationWeather();
        w.locationName = locationName;
        w.dataAvailable = false;
        w.errorMessage = message;
        return w;
    }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public List<DayForecast> getForecasts() { return forecasts; }
    public void setForecasts(List<DayForecast> forecasts) { this.forecasts = forecasts; }
    public boolean isDataAvailable() { return dataAvailable; }
    public void setDataAvailable(boolean dataAvailable) { this.dataAvailable = dataAvailable; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
