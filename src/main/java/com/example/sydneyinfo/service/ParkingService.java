package com.example.sydneyinfo.service;

import com.example.sydneyinfo.model.ParkingInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class ParkingService {

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

    @Cacheable("parking")
    public ParkingInfo getParking() {
        if (apiKey == null || apiKey.isBlank()) {
            return ParkingInfo.error("Set the TFNSW_API_KEY environment variable to view parking data.");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "apikey " + apiKey);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            // Use specific facility ID if provided, otherwise fetch all and search
            String url = (facilityId != null && !facilityId.isBlank())
                ? CARPARK_URL + "?facility=" + facilityId
                : CARPARK_URL;

            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            return parseParkingResponse(response.getBody());

        } catch (Exception e) {
            return ParkingInfo.error("Could not fetch parking data: " + e.getMessage());
        }
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

        if (node.has("spots_total")) {
            info.setTotalSpots(node.get("spots_total").asInt());
            info.setAvailableSpots(node.get("spots_available").asInt());
        } else if (node.has("occupancy")) {
            JsonNode occ = node.get("occupancy");
            info.setTotalSpots(occ.has("total") ? occ.get("total").asInt() : 0);
            info.setAvailableSpots(occ.has("available") ? occ.get("available").asInt() : 0);
        } else if (node.has("total") && node.has("available")) {
            info.setTotalSpots(node.get("total").asInt());
            info.setAvailableSpots(node.get("available").asInt());
        }

        if (node.has("last_updated")) {
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
