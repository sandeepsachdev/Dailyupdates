package com.example.sydneyinfo.service;

import com.example.sydneyinfo.model.ParkingInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class ParkingService {

    private static final Logger log = LoggerFactory.getLogger(ParkingService.class);
    private static final String CARPARK_URL =
        "https://api.transport.nsw.gov.au/v1/carpark";

    @Value("${app.tfnsw.api-key:}")
    private String apiKey;

    // Optional override — leave blank to auto-discover Cherrybrook from the full list
    @Value("${app.tfnsw.carpark.facility-id:}")
    private String facilityId;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    // Holds the last raw JSON for the /debug/parking endpoint
    private volatile String lastRawResponse = "No response yet";

    @Cacheable("parking")
    public ParkingInfo getParking() {
        if (apiKey == null || apiKey.isBlank()) {
            return ParkingInfo.error("Set the TFNSW_API_KEY environment variable to view parking data.");
        }

        try {
            String json = fetchRaw();
            lastRawResponse = json;
            log.info("TfNSW carpark raw response: {}", json);
            return parseParkingResponse(json);

        } catch (Exception e) {
            lastRawResponse = "Error: " + e.getMessage();
            return ParkingInfo.error("Could not fetch parking data: " + e.getMessage());
        }
    }

    public String getRawResponse() {
        if (apiKey == null || apiKey.isBlank()) return "TFNSW_API_KEY not set";
        try {
            // Always fetch fresh for debug — bypasses cache
            return fetchRaw();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String fetchRaw() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "apikey " + apiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String url = (facilityId != null && !facilityId.isBlank())
            ? CARPARK_URL + "?facility=" + facilityId
            : CARPARK_URL;

        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class)
            .getBody();
    }

    private ParkingInfo parseParkingResponse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);

        // All-facilities response is an array — search for Cherrybrook
        if (root.isArray()) {
            for (JsonNode node : root) {
                String name = extractName(node);
                if (name != null && name.toLowerCase().contains("cherrybrook")) {
                    return buildInfo(node, name);
                }
            }
            return ParkingInfo.error("Cherrybrook not found in TfNSW car park list. "
                + "Set CARPARK_FACILITY_ID to override.");
        }

        // Single-facility response
        return buildInfo(root, extractName(root));
    }

    private ParkingInfo buildInfo(JsonNode node, String name) {
        ParkingInfo info = new ParkingInfo();
        info.setDataAvailable(true);
        info.setFacilityName(name != null ? name : "Cherrybrook Station");

        // TfNSW API: "spots" = total capacity, "occupancy.total" = occupied count
        if (node.has("spots") && node.has("occupancy")) {
            int total = node.get("spots").asInt();
            int occupied = node.get("occupancy").get("total").asInt();
            info.setTotalSpots(total);
            info.setAvailableSpots(Math.max(0, total - occupied));
        } else if (node.has("spots_total")) {
            info.setTotalSpots(node.get("spots_total").asInt());
            info.setAvailableSpots(node.get("spots_available").asInt());
        } else if (node.has("total") && node.has("available")) {
            info.setTotalSpots(node.get("total").asInt());
            info.setAvailableSpots(node.get("available").asInt());
        }

        if (node.has("MessageDate")) {
            info.setLastUpdated(node.get("MessageDate").asText());
        } else if (node.has("last_updated")) {
            info.setLastUpdated(node.get("last_updated").asText());
        } else if (node.has("time")) {
            info.setLastUpdated(node.get("time").asText());
        }

        return info;
    }

    private String extractName(JsonNode node) {
        if (node.has("carpark_name"))  return node.get("carpark_name").asText();
        if (node.has("facility_name")) return node.get("facility_name").asText();
        if (node.has("name"))          return node.get("name").asText();
        if (node.has("facility") && node.get("facility").has("name"))
            return node.get("facility").get("name").asText();
        return null;
    }
}
