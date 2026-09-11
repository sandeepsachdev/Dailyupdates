package com.example.sydneyinfo.controller;

import com.example.sydneyinfo.model.FuelInfo;
import com.example.sydneyinfo.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class DashboardController {

    /** How many weeks of price history the E10 trend chart shows. */
    private static final int TREND_WEEKS = 6;

    @Autowired private ParkingService parkingService;
    @Autowired private WeatherService weatherService;
    @Autowired private MetroService metroService;
    @Autowired private FuelService fuelService;

    /** Present only when a database is configured; null otherwise (see PriceRecorder). */
    @Autowired(required = false) private PriceRecorder priceRecorder;

    /** Spring Boot's configured mapper (has Java-8 date/time support). */
    @Autowired private ObjectMapper objectMapper;

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a");

    @GetMapping("/")
    public String dashboard(Model model) {
        FuelInfo fuelInfo = fuelService.getFuelPrices();

        // Persist at most one snapshot per day, then expose the last 6 weeks of the
        // E10 trend (Sydney average + Ampol Foodary Cherrybrook) for the chart.
        if (priceRecorder != null) {
            priceRecorder.record(fuelInfo);
            model.addAttribute("e10ChartJson", toJson(priceRecorder.recentE10Trend(TREND_WEEKS)));
        }

        model.addAttribute("parking", parkingService.getParking());
        model.addAttribute("cherrybrookWeather", weatherService.getCherrybrookWeather());
        model.addAttribute("sydneyWeather", weatherService.getSydneyWeather());
        model.addAttribute("metroAlerts", metroService.getAlerts());
        model.addAttribute("fuelInfo", fuelInfo);
        model.addAttribute("lastUpdated",
            ZonedDateTime.now(ZoneId.of("Australia/Sydney")).format(FORMATTER));
        return "dashboard";
    }

    /** Serialises the E10 history to a JSON string for the inline chart, or null on failure. */
    private String toJson(List<?> points) {
        if (points == null || points.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(points);
        } catch (Exception e) {
            return null;
        }
    }

    /** Raw TfNSW API response — visit /debug/parking to diagnose field mapping issues */
    @GetMapping("/debug/parking")
    @ResponseBody
    public String debugParking() {
        return parkingService.getRawResponse();
    }

    /** First 200 fuel stations — visit /debug/fuel to check lat/lon field names */
    @GetMapping("/debug/fuel")
    @ResponseBody
    public String debugFuel() {
        return fuelService.getRawStations();
    }

    /** Lightweight health check for Sliplane / container orchestrators */
    @GetMapping("/health")
    @ResponseBody
    public String health() {
        return "ok";
    }
}
