package com.example.sydneyinfo.service;

import com.example.sydneyinfo.model.DayForecast;
import com.example.sydneyinfo.model.LocationWeather;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
public class WeatherService {

    private static final String BASE_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String CURRENT_VARS = "temperature_2m,weathercode";
    private static final String[] DAILY_VARS =
        "temperature_2m_max,temperature_2m_min,weathercode,precipitation_probability_max".split(",");

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @Cacheable("weather")
    public LocationWeather getCherrybrookWeather() {
        return fetchWeather("Cherrybrook", -33.733, 151.050);
    }

    @Cacheable(value = "weather", key = "'sydney'")
    public LocationWeather getSydneyWeather() {
        return fetchWeather("Sydney CBD", -33.8688, 151.2093);
    }

    private LocationWeather fetchWeather(String locationName, double lat, double lon) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .queryParam("latitude", lat)
                .queryParam("longitude", lon)
                .queryParam("current", CURRENT_VARS)
                .queryParam("daily", String.join(",", DAILY_VARS))
                .queryParam("timezone", "Australia/Sydney")
                .queryParam("forecast_days", 2)
                .toUriString();

            String json = restTemplate.getForObject(url, String.class);
            JsonNode root = mapper.readTree(json);
            JsonNode current = root.get("current");
            JsonNode daily = root.get("daily");

            JsonNode times = daily.get("time");
            JsonNode maxTemps = daily.get("temperature_2m_max");
            JsonNode minTemps = daily.get("temperature_2m_min");
            JsonNode codes = daily.get("weathercode");
            JsonNode precip = daily.get("precipitation_probability_max");

            String[] labels = {"Today", "Tomorrow"};
            List<DayForecast> forecasts = new ArrayList<>();

            for (int i = 0; i < 2 && i < times.size(); i++) {
                DayForecast day = new DayForecast();
                day.setDate(times.get(i).asText());
                day.setLabel(labels[i]);
                day.setMaxTemp(Math.round(maxTemps.get(i).asDouble() * 10.0) / 10.0);
                day.setMinTemp(Math.round(minTemps.get(i).asDouble() * 10.0) / 10.0);
                day.setWeatherCode(codes.get(i).asInt());
                day.setPrecipitationProbability(precip != null && !precip.get(i).isNull()
                    ? precip.get(i).asInt() : 0);
                forecasts.add(day);
            }

            LocationWeather weather = new LocationWeather();
            weather.setLocationName(locationName);
            weather.setForecasts(forecasts);
            weather.setDataAvailable(true);
            if (current != null) {
                weather.setCurrentTemp(Math.round(current.get("temperature_2m").asDouble() * 10.0) / 10.0);
                weather.setCurrentWeatherCode(current.get("weathercode").asInt());
            }
            return weather;

        } catch (Exception e) {
            return LocationWeather.error(locationName, "Weather data unavailable: " + e.getMessage());
        }
    }
}
