package com.example.sydneyinfo.service;

import com.example.sydneyinfo.model.EventInfo;
import com.example.sydneyinfo.model.SydneyEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class EventService {

    private static final String BASE_URL = "https://app.ticketmaster.com/discovery/v2/events.json";
    private static final DateTimeFormatter TM_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("EEE d MMM");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter LOCAL_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Value("${app.ticketmaster.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public EventService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Cacheable("events")
    public EventInfo getEvents() {
        if (!isConfigured()) {
            return EventInfo.error("Set TICKETMASTER_API_KEY to enable Sydney events.");
        }
        try {
            String start = LocalDateTime.now().format(TM_DATE);
            String end   = LocalDate.now().plusDays(7).atStartOfDay().format(TM_DATE);

            String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .queryParam("apikey", apiKey)
                .queryParam("city", "Sydney")
                .queryParam("countryCode", "AU")
                .queryParam("startDateTime", start)
                .queryParam("endDateTime", end)
                .queryParam("size", 12)
                .queryParam("sort", "date,asc")
                .toUriString();

            String json = restTemplate.getForObject(url, String.class);
            return parseResponse(json);
        } catch (Exception e) {
            return EventInfo.error("Could not fetch events: " + e.getMessage());
        }
    }

    private EventInfo parseResponse(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        JsonNode embedded = root.path("_embedded");
        JsonNode eventsNode = embedded.path("events");

        List<SydneyEvent> events = new ArrayList<>();

        if (!eventsNode.isMissingNode()) {
            for (JsonNode ev : eventsNode) {
                SydneyEvent e = new SydneyEvent();
                e.setName(ev.path("name").asText(""));
                e.setUrl(ev.path("url").asText(""));

                // Date / time
                JsonNode dates = ev.path("dates").path("start");
                String localDate = dates.path("localDate").asText("");
                String localTime = dates.path("localTime").asText("");
                if (!localDate.isEmpty()) {
                    try {
                        LocalDate ld = LocalDate.parse(localDate);
                        e.setDate(ld.format(DISPLAY_DATE));
                    } catch (Exception ignored) {
                        e.setDate(localDate);
                    }
                }
                if (!localTime.isEmpty()) {
                    try {
                        LocalDateTime ldt = LocalDateTime.parse(localDate + "T" + localTime, LOCAL_DT);
                        e.setTime(ldt.format(DISPLAY_TIME));
                    } catch (Exception ignored) {
                        e.setTime(localTime);
                    }
                }

                // Venue
                JsonNode venues = ev.path("_embedded").path("venues");
                if (venues.isArray() && venues.size() > 0) {
                    e.setVenue(venues.get(0).path("name").asText(""));
                }

                // Genre
                JsonNode classifications = ev.path("classifications");
                if (classifications.isArray() && classifications.size() > 0) {
                    String segment = classifications.get(0).path("segment").path("name").asText("");
                    String genre   = classifications.get(0).path("genre").path("name").asText("");
                    e.setGenre(!genre.isEmpty() && !genre.equals("Undefined") ? genre : segment);
                }

                // Image — prefer 16:9 ratio, width ~640
                JsonNode images = ev.path("images");
                String bestImg = "";
                int bestWidth = 0;
                if (images.isArray()) {
                    for (JsonNode img : images) {
                        String ratio = img.path("ratio").asText("");
                        int w = img.path("width").asInt(0);
                        if ("16_9".equals(ratio) && w > bestWidth && w <= 640) {
                            bestImg = img.path("url").asText("");
                            bestWidth = w;
                        }
                    }
                    if (bestImg.isEmpty() && images.size() > 0) {
                        bestImg = images.get(0).path("url").asText("");
                    }
                }
                e.setImageUrl(bestImg);

                events.add(e);
            }
        }

        EventInfo info = new EventInfo();
        info.setDataAvailable(true);
        info.setEvents(events);
        return info;
    }
}
