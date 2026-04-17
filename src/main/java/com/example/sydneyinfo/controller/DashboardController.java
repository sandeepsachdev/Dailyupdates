package com.example.sydneyinfo.controller;

import com.example.sydneyinfo.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class DashboardController {

    @Autowired private ParkingService parkingService;
    @Autowired private WeatherService weatherService;
    @Autowired private MetroService metroService;
    @Autowired private FuelService fuelService;

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a");

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("parking", parkingService.getParking());
        model.addAttribute("cherrybrookWeather", weatherService.getCherrybrookWeather());
        model.addAttribute("sydneyWeather", weatherService.getSydneyWeather());
        model.addAttribute("metroAlerts", metroService.getAlerts());
        model.addAttribute("fuelInfo", fuelService.getFuelPrices());
        model.addAttribute("lastUpdated",
            ZonedDateTime.now(ZoneId.of("Australia/Sydney")).format(FORMATTER));
        return "dashboard";
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
}
