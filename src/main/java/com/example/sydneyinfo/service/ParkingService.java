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

@Service
public class ParkingService {

    private static final String CARPARK_URL =
        "https://api.transport.nsw.gov.au/v1/carpark";

    @Value("${app.tfnsw.api-key:}")
    private String apiKey;

    @Value("${app.tfnsw.carpark.facility-id:MACs100034}")
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
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            String url = CARPARK_URL + "?facility=" + facilityId;
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            return parseParkingResponse(response.getBody());

        } catch (Exception e) {
            return ParkingInfo.error("Could not fetch parking data: " + e.getMessage());
        }
    }

    private ParkingInfo parseParkingResponse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);

        ParkingInfo info = new ParkingInfo();
        info.setDataAvailable(true);

        // Handle both possible API response structures
        if (root.has("spots_total")) {
            info.setTotalSpots(root.get("spots_total").asInt());
            info.setAvailableSpots(root.get("spots_available").asInt());
        } else if (root.has("occupancy")) {
            JsonNode occ = root.get("occupancy");
            info.setTotalSpots(occ.has("total") ? occ.get("total").asInt() : 0);
            info.setAvailableSpots(occ.has("available") ? occ.get("available").asInt() : 0);
        }

        if (root.has("carpark_name")) {
            info.setFacilityName(root.get("carpark_name").asText());
        } else if (root.has("facility") && root.get("facility").has("name")) {
            info.setFacilityName(root.get("facility").get("name").asText());
        } else {
            info.setFacilityName("Cherrybrook Station");
        }

        if (root.has("last_updated")) {
            info.setLastUpdated(root.get("last_updated").asText());
        } else if (root.has("time")) {
            info.setLastUpdated(root.get("time").asText());
        }

        return info;
    }
}
