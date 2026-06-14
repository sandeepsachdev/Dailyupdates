package com.example.sydneyinfo.service;

import com.example.sydneyinfo.model.MetroAlert;
import com.google.protobuf.CodedInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches Sydney Metro service alerts from the TfNSW GTFS-RT feed and parses
 * the protobuf binary manually using CodedInputStream, avoiding the need for
 * pre-compiled gtfs-realtime-bindings which are not on Maven Central.
 *
 * GTFS-RT field numbers used:
 *   FeedMessage:  2=entity
 *   FeedEntity:   5=alert
 *   Alert:        1=active_period, 6=cause, 7=effect, 10=header_text, 11=description_text
 *   TimeRange:    1=start, 2=end (both uint64 epoch seconds)
 *   TranslatedString: 1=translation
 *   Translation:  1=text, 2=language
 */
@Service
public class MetroService {

    private static final String ALERTS_URL =
        "https://api.transport.nsw.gov.au/v2/gtfs/alerts/metro";

    private static final Map<Integer, String> EFFECTS = new HashMap<>();
    private static final Map<Integer, String> CAUSES = new HashMap<>();

    static {
        EFFECTS.put(1, "NO_SERVICE");
        EFFECTS.put(2, "REDUCED_SERVICE");
        EFFECTS.put(3, "SIGNIFICANT_DELAYS");
        EFFECTS.put(4, "DETOUR");
        EFFECTS.put(5, "ADDITIONAL_SERVICE");
        EFFECTS.put(6, "MODIFIED_SERVICE");
        EFFECTS.put(7, "OTHER_EFFECT");
        EFFECTS.put(8, "UNKNOWN_EFFECT");
        EFFECTS.put(9, "STOP_MOVED");
        EFFECTS.put(10, "NO_EFFECT");
        EFFECTS.put(11, "ACCESSIBILITY_ISSUE");

        CAUSES.put(1, "UNKNOWN_CAUSE");
        CAUSES.put(2, "OTHER_CAUSE");
        CAUSES.put(3, "TECHNICAL_PROBLEM");
        CAUSES.put(4, "STRIKE");
        CAUSES.put(5, "DEMONSTRATION");
        CAUSES.put(6, "ACCIDENT");
        CAUSES.put(7, "HOLIDAY");
        CAUSES.put(8, "WEATHER");
        CAUSES.put(9, "MAINTENANCE");
        CAUSES.put(10, "CONSTRUCTION");
        CAUSES.put(11, "POLICE_ACTIVITY");
        CAUSES.put(12, "MEDICAL_EMERGENCY");
    }

    @Value("${app.tfnsw.api-key:}")
    private String apiKey;

    @Autowired
    private RestTemplate restTemplate;

    @Cacheable("metro")
    public List<MetroAlert> getAlerts() {
        if (apiKey == null || apiKey.isBlank()) {
            MetroAlert placeholder = new MetroAlert();
            placeholder.setHeader("API key required");
            placeholder.setDescription(
                "Set the TFNSW_API_KEY environment variable to view Sydney Metro disruptions.");
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
            if (body == null || body.length == 0) return List.of();

            return parseFeedMessage(body);

        } catch (Exception e) {
            MetroAlert err = new MetroAlert();
            err.setHeader("Could not fetch metro alerts");
            err.setDescription(e.getMessage());
            err.setEffect("UNKNOWN_EFFECT");
            return List.of(err);
        }
    }

    // ── GTFS-RT protobuf parsing ──────────────────────────────────────────────

    private List<MetroAlert> parseFeedMessage(byte[] data) throws IOException {
        List<MetroAlert> alerts = new ArrayList<>();
        CodedInputStream in = CodedInputStream.newInstance(data);
        while (!in.isAtEnd()) {
            int tag = in.readTag();
            if (tag == 0) break;
            if ((tag >>> 3) == 2) {             // FeedMessage.entity (field 2)
                MetroAlert a = parseEntity(in.readBytes().toByteArray());
                if (a != null && a.getHeader() != null && !a.getHeader().isBlank()) {
                    alerts.add(a);
                }
            } else {
                in.skipField(tag);
            }
        }
        return alerts;
    }

    private MetroAlert parseEntity(byte[] data) throws IOException {
        CodedInputStream in = CodedInputStream.newInstance(data);
        while (!in.isAtEnd()) {
            int tag = in.readTag();
            if (tag == 0) break;
            if ((tag >>> 3) == 5) {             // FeedEntity.alert (field 5)
                return parseAlert(in.readBytes().toByteArray());
            } else {
                in.skipField(tag);
            }
        }
        return null;
    }

    private MetroAlert parseAlert(byte[] data) throws IOException {
        CodedInputStream in = CodedInputStream.newInstance(data);
        MetroAlert alert = new MetroAlert();
        while (!in.isAtEnd()) {
            int tag = in.readTag();
            if (tag == 0) break;
            switch (tag >>> 3) {
                case 1  -> parseActivePeriod(in.readBytes().toByteArray(), alert);
                case 6  -> alert.setCause(CAUSES.getOrDefault(in.readInt32(), "UNKNOWN_CAUSE"));
                case 7  -> alert.setEffect(EFFECTS.getOrDefault(in.readInt32(), "UNKNOWN_EFFECT"));
                case 10 -> alert.setHeader(parseTranslatedString(in.readBytes().toByteArray()));
                case 11 -> alert.setDescription(parseTranslatedString(in.readBytes().toByteArray()));
                default -> in.skipField(tag);
            }
        }
        return alert;
    }

    /**
     * Parses a TimeRange (Alert.active_period). An alert may carry several active
     * periods; we keep the earliest start and the latest end so the displayed
     * range spans the whole disruption.
     */
    private void parseActivePeriod(byte[] data, MetroAlert alert) throws IOException {
        CodedInputStream in = CodedInputStream.newInstance(data);
        long start = 0, end = 0;
        while (!in.isAtEnd()) {
            int tag = in.readTag();
            if (tag == 0) break;
            switch (tag >>> 3) {
                case 1 -> start = in.readUInt64();
                case 2 -> end = in.readUInt64();
                default -> in.skipField(tag);
            }
        }
        if (start > 0 && (alert.getActivePeriodStart() == null || start < alert.getActivePeriodStart())) {
            alert.setActivePeriodStart(start);
        }
        if (end > 0 && (alert.getActivePeriodEnd() == null || end > alert.getActivePeriodEnd())) {
            alert.setActivePeriodEnd(end);
        }
    }

    private String parseTranslatedString(byte[] data) throws IOException {
        CodedInputStream in = CodedInputStream.newInstance(data);
        String first = null, english = null;
        while (!in.isAtEnd()) {
            int tag = in.readTag();
            if (tag == 0) break;
            if ((tag >>> 3) == 1) {             // TranslatedString.translation (field 1)
                String[] tl = parseTranslation(in.readBytes().toByteArray());
                if (first == null) first = tl[0];
                if (english == null && ("en".equals(tl[1]) || tl[1].isEmpty())) english = tl[0];
            } else {
                in.skipField(tag);
            }
        }
        return english != null ? english : first;
    }

    private String[] parseTranslation(byte[] data) throws IOException {
        CodedInputStream in = CodedInputStream.newInstance(data);
        String text = "", lang = "";
        while (!in.isAtEnd()) {
            int tag = in.readTag();
            if (tag == 0) break;
            switch (tag >>> 3) {
                case 1 -> text = in.readString();
                case 2 -> lang = in.readString();
                default -> in.skipField(tag);
            }
        }
        return new String[]{text, lang};
    }
}
