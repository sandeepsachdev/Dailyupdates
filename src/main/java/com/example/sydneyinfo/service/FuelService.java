package com.example.sydneyinfo.service;

import com.example.sydneyinfo.model.FuelInfo;
import com.example.sydneyinfo.model.FuelPrice;
import com.example.sydneyinfo.model.LocalFuelPrice;
import com.example.sydneyinfo.model.LocalFuelStation;
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
import java.util.Collections;

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

    // Cherrybrook ~2 km bounding box (centre: -33.733, 151.050)
    private static final double CHERRY_LAT_MIN = -33.753, CHERRY_LAT_MAX = -33.713;
    private static final double CHERRY_LON_MIN = 151.025, CHERRY_LON_MAX = 151.075;

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

    private HttpEntity<Void> buildRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("requesttimestamp", LocalDateTime.now().format(TIMESTAMP_FMT));
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(headers);
    }

    @Cacheable("fuel")
    public FuelInfo getFuelPrices() {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                PRICES_URL, HttpMethod.GET, buildRequest(), String.class);
            return parseResponse(response.getBody());
        } catch (Exception e) {
            return FuelInfo.error("Could not fetch fuel prices: " + e.getMessage());
        }
    }

    /** Returns first 200 station entries as JSON for debugging field names / lat-lon. */
    public String getRawStations() {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                PRICES_URL, HttpMethod.GET, buildRequest(), String.class);
            JsonNode root = mapper.readTree(response.getBody());
            JsonNode stations = root.get("stations");
            if (stations == null) return "{\"error\":\"no stations node\"}";
            // Return first 200 entries to keep the response manageable
            List<JsonNode> sample = new ArrayList<>();
            int i = 0;
            for (JsonNode s : stations) {
                if (i++ >= 200) break;
                sample.add(s);
            }
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sample);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private FuelInfo parseResponse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);

        Set<String> sydneyCodes = new HashSet<>();
        Set<String> cherryCodes = new HashSet<>();

        // station metadata keyed by code
        Map<String, JsonNode> stationMeta = new HashMap<>();

        JsonNode stations = root.get("stations");
        if (stations != null) {
            for (JsonNode s : stations) {
                String code = s.has("code") ? s.get("code").asText() : "";
                if (code.isEmpty()) continue;
                stationMeta.put(code, s);

                JsonNode loc = s.get("location");
                double lat = (loc != null && loc.has("latitude"))  ? loc.get("latitude").asDouble()  : 0;
                double lon = (loc != null && loc.has("longitude")) ? loc.get("longitude").asDouble() : 0;

                if (lat >= LAT_MIN && lat <= LAT_MAX && lon >= LON_MIN && lon <= LON_MAX) {
                    sydneyCodes.add(code);
                }
                if (lat >= CHERRY_LAT_MIN && lat <= CHERRY_LAT_MAX
                        && lon >= CHERRY_LON_MIN && lon <= CHERRY_LON_MAX) {
                    cherryCodes.add(code);
                }
            }
        }

        // Aggregate prices across Sydney stations; also collect per-station prices for Cherrybrook
        Map<String, List<Double>> byType = new LinkedHashMap<>();
        for (String t : List.of("U91", "E10", "DL", "U95", "U98")) byType.put(t, new ArrayList<>());

        // stationCode → (fuelType → price)
        Map<String, Map<String, Double>> cherryPrices = new LinkedHashMap<>();

        JsonNode prices = root.get("prices");
        if (prices != null) {
            for (JsonNode p : prices) {
                String code  = p.has("stationcode") ? p.get("stationcode").asText() : "";
                String type  = p.has("fueltype")    ? p.get("fueltype").asText()    : "";
                double price = p.has("price")        ? p.get("price").asDouble()    : 0;
                if (price <= 0) continue;

                boolean inSydney = sydneyCodes.isEmpty() || sydneyCodes.contains(code);
                if (inSydney && byType.containsKey(type)) {
                    byType.get(type).add(price);
                }
                if (cherryCodes.contains(code) && byType.containsKey(type)) {
                    cherryPrices.computeIfAbsent(code, k -> new LinkedHashMap<>()).put(type, price);
                }
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

        // Build local station list sorted by code for stability
        List<LocalFuelStation> localStations = new ArrayList<>();
        List<String> sortedCherryCodes = new ArrayList<>(cherryPrices.keySet());
        Collections.sort(sortedCherryCodes);
        for (String code : sortedCherryCodes) {
            JsonNode meta = stationMeta.get(code);
            LocalFuelStation ls = new LocalFuelStation();
            ls.setStationCode(code);
            ls.setName(meta != null && meta.has("name")    ? meta.get("name").asText()    : code);
            ls.setBrand(meta != null && meta.has("brand")  ? meta.get("brand").asText()   : "");
            ls.setAddress(meta != null && meta.has("address") ? meta.get("address").asText() : "");

            Map<String, Double> stPrices = cherryPrices.get(code);
            List<LocalFuelPrice> lfp = new ArrayList<>();
            for (String t : List.of("U91", "E10", "DL", "U95", "U98")) {
                if (!stPrices.containsKey(t)) continue;
                LocalFuelPrice lp = new LocalFuelPrice();
                lp.setFuelType(t);
                lp.setFuelTypeLabel(FUEL_LABELS.getOrDefault(t, t));
                lp.setPrice(stPrices.get(t));
                lfp.add(lp);
            }
            ls.setPrices(lfp);
            String nameUpper = ls.getName().toUpperCase();
            if (nameUpper.contains("CHERRYBROOK")) {
                localStations.add(ls);
            }
        }

        FuelInfo info = new FuelInfo();
        info.setDataAvailable(true);
        info.setPrices(fuelPrices);
        info.setLocalStations(localStations);
        info.setRegion("Sydney Metro");
        return info;
    }
}
