package com.example.sydneyinfo.service;

import com.example.sydneyinfo.model.FuelInfo;
import com.example.sydneyinfo.model.FuelPrice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * NSW FuelCheck API — no OAuth or API key required.
 * Authentication is via a requesttimestamp header (DD/MM/YYYY HH:mm:ss).
 * Prices endpoint returns all NSW stations; we filter to Sydney metro by lat/lon.
 */
@Service
public class FuelService {

    private static final String PRICES_URL =
        "https://api.onegov.nsw.gov.au/FuelCheckApp/v1/fuel/prices";

    private static final DateTimeFormatter TIMESTAMP_FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    // Bounding box for Greater Sydney metro
    private static final double LAT_MIN = -34.3, LAT_MAX = -33.2;
    private static final double LON_MIN = 150.3, LON_MAX = 151.8;

    private static final Map<String, String> FUEL_LABELS = Map.of(
        "U91", "Unleaded 91",
        "E10", "E10 Ethanol",
        "DL",  "Diesel",
        "U95", "Premium 95",
        "U98", "Premium 98"
    );

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @Cacheable("fuel")
    public FuelInfo getFuelPrices() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("requesttimestamp", LocalDateTime.now().format(TIMESTAMP_FMT));
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplate.exchange(
                PRICES_URL, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            return parseResponse(response.getBody());

        } catch (Exception e) {
            return FuelInfo.error("Could not fetch fuel prices: " + e.getMessage());
        }
    }

    private FuelInfo parseResponse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);

        // Build a set of station codes that are within Greater Sydney
        Set<String> sydneyCodes = new HashSet<>();
        JsonNode stations = root.get("stations");
        if (stations != null) {
            for (JsonNode s : stations) {
                double lat = s.has("latitude")  ? s.get("latitude").asDouble()  : 0;
                double lon = s.has("longitude") ? s.get("longitude").asDouble() : 0;
                if (lat >= LAT_MIN && lat <= LAT_MAX && lon >= LON_MIN && lon <= LON_MAX) {
                    // code field identifies the station in the prices list
                    if (s.has("code")) sydneyCodes.add(s.get("code").asText());
                }
            }
        }

        // Aggregate prices across Sydney stations
        Map<String, List<Double>> byType = new LinkedHashMap<>();
        for (String t : List.of("U91", "E10", "DL", "U95", "U98")) byType.put(t, new ArrayList<>());

        JsonNode prices = root.get("prices");
        if (prices != null) {
            for (JsonNode p : prices) {
                String code = p.has("stationcode") ? p.get("stationcode").asText() : "";
                if (!sydneyCodes.isEmpty() && !sydneyCodes.contains(code)) continue;

                String type  = p.has("fueltype") ? p.get("fueltype").asText() : "";
                double price = p.has("price")    ? p.get("price").asDouble()  : 0;
                if (byType.containsKey(type) && price > 0) byType.get(type).add(price);
            }
        }

        List<FuelPrice> fuelPrices = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : byType.entrySet()) {
            List<Double> list = entry.getValue();
            if (list.isEmpty()) continue;

            FuelPrice fp = new FuelPrice();
            fp.setFuelType(entry.getKey());
            fp.setFuelTypeLabel(FUEL_LABELS.getOrDefault(entry.getKey(), entry.getKey()));
            fp.setAveragePrice(list.stream().mapToDouble(d -> d).average().orElse(0));
            fp.setMinPrice(list.stream().mapToDouble(d -> d).min().orElse(0));
            fp.setMaxPrice(list.stream().mapToDouble(d -> d).max().orElse(0));
            fp.setStationCount(list.size());
            fuelPrices.add(fp);
        }

        FuelInfo info = new FuelInfo();
        info.setDataAvailable(true);
        info.setPrices(fuelPrices);
        info.setRegion("Sydney Metro");
        return info;
    }
}
