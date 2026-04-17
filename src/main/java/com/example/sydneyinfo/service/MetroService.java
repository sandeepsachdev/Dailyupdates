package com.example.sydneyinfo.service;

import com.example.sydneyinfo.model.MetroAlert;
import com.google.transit.realtime.GtfsRealtime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class MetroService {

    private static final String ALERTS_URL =
        "https://api.transport.nsw.gov.au/v2/gtfs/alerts/sydneymetro";

    @Value("${app.tfnsw.api-key:}")
    private String apiKey;

    @Autowired
    private RestTemplate restTemplate;

    @Cacheable("metro")
    public List<MetroAlert> getAlerts() {
        if (apiKey == null || apiKey.isBlank()) {
            MetroAlert placeholder = new MetroAlert();
            placeholder.setHeader("API key required");
            placeholder.setDescription("Set the TFNSW_API_KEY environment variable to view Sydney Metro disruptions.");
            placeholder.setEffect("UNKNOWN_EFFECT");
            return List.of(placeholder);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "apikey " + apiKey);
            headers.setAccept(List.of(MediaType.APPLICATION_OCTET_STREAM));

            ResponseEntity<byte[]> response = restTemplate.exchange(
                ALERTS_URL, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                return List.of();
            }

            GtfsRealtime.FeedMessage feed = GtfsRealtime.FeedMessage.parseFrom(body);
            return parseAlerts(feed);

        } catch (Exception e) {
            MetroAlert errorAlert = new MetroAlert();
            errorAlert.setHeader("Could not fetch metro alerts");
            errorAlert.setDescription(e.getMessage());
            errorAlert.setEffect("UNKNOWN_EFFECT");
            return List.of(errorAlert);
        }
    }

    private List<MetroAlert> parseAlerts(GtfsRealtime.FeedMessage feed) {
        List<MetroAlert> alerts = new ArrayList<>();

        for (GtfsRealtime.FeedEntity entity : feed.getEntityList()) {
            if (!entity.hasAlert()) continue;

            GtfsRealtime.Alert alert = entity.getAlert();
            MetroAlert metroAlert = new MetroAlert();

            if (alert.hasHeaderText()) {
                metroAlert.setHeader(getEnglishText(alert.getHeaderText()));
            }
            if (alert.hasDescriptionText()) {
                metroAlert.setDescription(getEnglishText(alert.getDescriptionText()));
            }
            metroAlert.setEffect(alert.getEffect().name());
            metroAlert.setCause(alert.getCause().name());

            if (metroAlert.getHeader() != null && !metroAlert.getHeader().isBlank()) {
                alerts.add(metroAlert);
            }
        }

        return alerts;
    }

    private String getEnglishText(GtfsRealtime.TranslatedString translatedString) {
        for (GtfsRealtime.TranslatedString.Translation t : translatedString.getTranslationList()) {
            if ("en".equals(t.getLanguage()) || t.getLanguage().isEmpty()) {
                return t.getText();
            }
        }
        if (!translatedString.getTranslationList().isEmpty()) {
            return translatedString.getTranslation(0).getText();
        }
        return "";
    }
}
