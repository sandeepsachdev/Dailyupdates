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

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Two-step lookup:
 *  1. GET /v1/carpark           → {"33":"Park&Ride - Cherrybrook", ...}  find Cherrybrook's ID
 *  2. GET /v1/carpark?facility=33 → {spots, occupancy.total, ...}         get live counts
 */
@Service
public class ParkingService {

    private static final Logger log = LoggerFactory.getLogger(ParkingService.class);
    private static final String CARPARK_URL =
        "https://api.transport.nsw.gov.au/v1/carpark";

    @Value("${app.tfnsw.api-key:}")
    private String apiKey;

    // Optional hard-coded override (e.g. "33"). Leave blank to auto-discover.
    @Value("${app.tfnsw.carpark.facility-id:}")
    private String facilityIdOverride;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @Cacheable("parking")
    public ParkingInfo getParking() {
        if (apiKey == null || apiKey.isBlank()) {
            return ParkingInfo.error("Set the TFNSW_API_KEY environment variable to view parking data.");
        }
        try {
            String facilityId = facilityIdOverride != null && !facilityIdOverride.isBlank()
                ? facilityIdOverride
                : resolveCherrybrookId();

            String json = fetch(CARPARK_URL + "?facility=" + facilityId);
            log.info("TfNSW carpark facility {} response: {}", facilityId, json);
            return parseOccupancy(json);

        } catch (Exception e) {
            log.error("Parking fetch failed", e);
            return ParkingInfo.error("Could not fetch parking data: " + e.getMessage());
        }
    }

    /** Calls the list endpoint and returns the ID of the active Cherrybrook facility. */
    private String resolveCherrybrookId() throws Exception {
        String json = fetch(CARPARK_URL);
        log.debug("TfNSW carpark list: {}", json);
        JsonNode root = mapper.readTree(json);

        // Prefer the non-historical Cherrybrook entry
        String fallback = null;
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String name = entry.getValue().asText();
            if (name.toLowerCase().contains("cherrybrook")) {
                if (!name.toLowerCase().contains("historical")) {
                    return entry.getKey();  // "33" → live data
                }
                fallback = entry.getKey(); // "5"  → historical, keep as fallback
            }
        }
        if (fallback != null) return fallback;
        throw new RuntimeException("Cherrybrook not found in TfNSW car park list");
    }

    /** Parses a single-facility response: {spots, occupancy:{total,...}, MessageDate, ...} */
    private ParkingInfo parseOccupancy(String json) throws Exception {
        JsonNode node = mapper.readTree(json);

        ParkingInfo info = new ParkingInfo();
        info.setDataAvailable(true);

        // Facility name
        String name = node.has("facility_name") ? node.get("facility_name").asText()
                    : node.has("carpark_name")   ? node.get("carpark_name").asText()
                    : "Cherrybrook Car Park";
        info.setFacilityName(name);

        // Spots: "spots" = total capacity, "occupancy.total" = occupied
        if (node.has("spots") && node.has("occupancy")) {
            int total    = node.get("spots").asInt();
            int occupied = node.get("occupancy").get("total").asInt();
            info.setTotalSpots(total);
            info.setAvailableSpots(Math.max(0, total - occupied));
        } else if (node.has("total") && node.has("available")) {
            info.setTotalSpots(node.get("total").asInt());
            info.setAvailableSpots(node.get("available").asInt());
        }

        // Timestamp
        if (node.has("MessageDate"))  info.setLastUpdated(node.get("MessageDate").asText());
        else if (node.has("time"))    info.setLastUpdated(node.get("time").asText());

        return info;
    }

    private String fetch(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "apikey " + apiKey);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class)
                           .getBody();
    }

    /** Raw single-facility response for /debug/parking */
    public String getRawResponse() {
        if (apiKey == null || apiKey.isBlank()) return "TFNSW_API_KEY not set";
        try {
            String id = facilityIdOverride != null && !facilityIdOverride.isBlank()
                ? facilityIdOverride : resolveCherrybrookId();
            return "facility_id=" + id + "\n\n" + fetch(CARPARK_URL + "?facility=" + id);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
