package com.example.sydneyinfo.model;

public class DayForecast {
    private String date;
    private String label; // "Today" or "Tomorrow"
    private double maxTemp;
    private double minTemp;
    private int weatherCode;
    private int precipitationProbability;

    public String getWeatherDescription() {
        if (weatherCode == 0) return "Clear sky";
        if (weatherCode <= 2) return "Mainly clear";
        if (weatherCode == 3) return "Overcast";
        if (weatherCode <= 48) return "Foggy";
        if (weatherCode <= 57) return "Drizzle";
        if (weatherCode <= 67) return "Rain";
        if (weatherCode <= 77) return "Snow";
        if (weatherCode <= 82) return "Rain showers";
        if (weatherCode <= 86) return "Snow showers";
        if (weatherCode <= 99) return "Thunderstorm";
        return "Unknown";
    }

    public String getWeatherIcon() {
        if (weatherCode == 0) return "☀️";
        if (weatherCode <= 2) return "🌤️";
        if (weatherCode == 3) return "☁️";
        if (weatherCode <= 48) return "🌫️";
        if (weatherCode <= 67) return "🌧️";
        if (weatherCode <= 77) return "❄️";
        if (weatherCode <= 82) return "🌦️";
        if (weatherCode <= 86) return "🌨️";
        return "⛈️";
    }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public double getMaxTemp() { return maxTemp; }
    public void setMaxTemp(double maxTemp) { this.maxTemp = maxTemp; }
    public double getMinTemp() { return minTemp; }
    public void setMinTemp(double minTemp) { this.minTemp = minTemp; }
    public int getWeatherCode() { return weatherCode; }
    public void setWeatherCode(int weatherCode) { this.weatherCode = weatherCode; }
    public int getPrecipitationProbability() { return precipitationProbability; }
    public void setPrecipitationProbability(int precipitationProbability) { this.precipitationProbability = precipitationProbability; }
}
