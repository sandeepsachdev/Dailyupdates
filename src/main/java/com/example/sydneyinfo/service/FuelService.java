package com.example.sydneyinfo.service;

import com.example.sydneyinfo.model.FuelInfo;
import com.example.sydneyinfo.model.FuelPrice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class FuelService {

    private static final String TOKEN_URL =
        "https://api.onegov.nsw.gov.au/oauth/client_credential/accesstoken?grant_type=client_credentials";
    private static final String PRICES_URL =
        "https://api.onegov.nsw.gov.au/FuelCheckApp/v1/fuel/prices/current";

    // Sydney metro postcodes range roughly 2000–2249 and some outer suburbs
    private static final Set<String> SYDNEY_FUEL_TYPES = Set.of("U91", "E10", "DL");
    private static final Map<String, String> FUEL_LABELS = Map.of(
        "U91", "Unleaded 91",
        "E10", "E10 Ethanol",
        "DL",  "Diesel",
        "U95", "Premium 95",
        "U98", "Premium 98"
    );

    @Value("${app.fuelcheck.client-id:}")
    private String clientId;

    @Value("${app.fuelcheck.client-secret:}")
    private String clientSecret;

    @Value("${app.fuelcheck.api-key:}")
    private String fuelApiKey;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @Cacheable("fuel")
    public FuelInfo getFuelPrices() {
        if (clientId == null || clientId.isBlank() ||
            clientSecret == null || clientSecret.isBlank() ||
            fuelApiKey == null || fuelApiKey.isBlank()) {
            return FuelInfo.error(
                "Set FUELCHECK_CLIENT_ID, FUELCHECK_CLIENT_SECRET and FUELCHECK_API_KEY " +
                "environment variables to view petrol prices.");
        }

        try {
            String accessToken = getAccessToken();
            return fetchPrices(accessToken);
        } catch (Exception e) {
            return FuelInfo.error("Could not fetch fuel prices: " + e.getMessage());
        }
    }

    @Cacheable("fuelToken")
    public String getAccessToken() {
        String credentials = Base64.getEncoder().encodeToString(
            (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + credentials);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        ResponseEntity<String> response = restTemplate.exchange(
            TOKEN_URL, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        try {
            JsonNode root = mapper.readTree(response.getBody());
            return root.get("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse access token: " + e.getMessage());
        }
    }

    private FuelInfo fetchPrices(String accessToken) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("apikey", fuelApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<String> response = restTemplate.exchange(
            PRICES_URL, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        return parsePricesResponse(response.getBody());
    }

    private FuelInfo parsePricesResponse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode stations = root.has("stations") ? root.get("stations") : root;

        // Aggregate prices per fuel type across Sydney-area stations
        Map<String, List<Double>> pricesByType = new LinkedHashMap<>();
        for (String type : List.of("U91", "E10", "DL", "U95", "U98")) {
            pricesByType.put(type, new ArrayList<>());
        }

        for (JsonNode station : stations) {
            String postcode = station.has("postcode") ? station.get("postcode").asText() : "";
            // Filter to Greater Sydney (postcodes 2000–2999)
            if (!postcode.isEmpty() && (postcode.compareTo("2000") < 0 || postcode.compareTo("2999") > 0)) {
                continue;
            }

            JsonNode prices = station.get("prices");
            if (prices == null) continue;

            for (JsonNode priceNode : prices) {
                String type = priceNode.has("fueltype") ? priceNode.get("fueltype").asText() : "";
                double price = priceNode.has("price") ? priceNode.get("price").asDouble() : 0;
                if (pricesByType.containsKey(type) && price > 0) {
                    pricesByType.get(type).add(price);
                }
            }
        }

        List<FuelPrice> fuelPrices = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : pricesByType.entrySet()) {
            List<Double> prices = entry.getValue();
            if (prices.isEmpty()) continue;

            FuelPrice fp = new FuelPrice();
            fp.setFuelType(entry.getKey());
            fp.setFuelTypeLabel(FUEL_LABELS.getOrDefault(entry.getKey(), entry.getKey()));
            fp.setAveragePrice(prices.stream().mapToDouble(d -> d).average().orElse(0));
            fp.setMinPrice(prices.stream().mapToDouble(d -> d).min().orElse(0));
            fp.setMaxPrice(prices.stream().mapToDouble(d -> d).max().orElse(0));
            fp.setStationCount(prices.size());
            fuelPrices.add(fp);
        }

        FuelInfo info = new FuelInfo();
        info.setDataAvailable(true);
        info.setPrices(fuelPrices);
        info.setRegion("Sydney Metro");
        return info;
    }
}
