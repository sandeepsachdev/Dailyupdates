package com.example.sydneyinfo.service;

import com.example.sydneyinfo.model.E10TrendPoint;
import com.example.sydneyinfo.model.FuelInfo;
import com.example.sydneyinfo.model.FuelPrice;
import com.example.sydneyinfo.model.LocalFuelPrice;
import com.example.sydneyinfo.model.LocalFuelStation;
import com.example.sydneyinfo.model.PriceSnapshot;
import com.example.sydneyinfo.model.PriceSnapshotItem;
import com.example.sydneyinfo.repository.PriceSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Persists a daily snapshot of the current fuel prices, and reads back recent
 * history for the E10 price-trend chart.
 *
 * <p>Only instantiated when a database is configured
 * ({@code app.persistence.enabled=true}, set by
 * {@link com.example.sydneyinfo.config.DatabaseUrlEnvironmentPostProcessor}).
 * When no database is present the bean is absent and the controller skips
 * recording entirely, so the app runs fine without PostgreSQL.
 */
@Service
@ConditionalOnProperty(name = "app.persistence.enabled", havingValue = "true")
public class PriceRecorder {

    private static final Logger log = LoggerFactory.getLogger(PriceRecorder.class);
    private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");

    private final PriceSnapshotRepository repository;

    public PriceRecorder(PriceSnapshotRepository repository) {
        this.repository = repository;
    }

    /**
     * Records the current prices at most once per Sydney calendar day. If a
     * snapshot already exists for today, this is a no-op. Never throws: any
     * persistence failure is logged and swallowed so it can't break the page.
     */
    @Transactional
    public void record(FuelInfo fuelInfo) {
        if (fuelInfo == null || !fuelInfo.isDataAvailable()
                || fuelInfo.getPrices() == null || fuelInfo.getPrices().isEmpty()) {
            return;
        }

        LocalDate today = LocalDate.now(SYDNEY);
        if (repository.existsBySnapshotDate(today)) {
            return; // already recorded a snapshot for today
        }

        try {
            OffsetDateTime now = ZonedDateTime.now(SYDNEY).toOffsetDateTime();
            PriceSnapshot snapshot = new PriceSnapshot(now, today, fuelInfo.getRegion());
            for (FuelPrice p : fuelInfo.getPrices()) {
                snapshot.addItem(new PriceSnapshotItem(
                    p.getFuelType(), p.getFuelTypeLabel(),
                    p.getAveragePrice(), p.getMinPrice(), p.getMaxPrice(),
                    p.getStationCount()));
            }
            snapshot.setLocalE10Price(localE10Price(fuelInfo.getLocalStations()));
            repository.save(snapshot);
        } catch (DataIntegrityViolationException e) {
            // Another concurrent request recorded today's snapshot first
            // (unique constraint on snapshot_date) — that's fine, ignore.
        } catch (Exception e) {
            log.warn("Could not record price snapshot: {}", e.getMessage());
        }
    }

    /**
     * The E10 price at the Ampol Foodary Cherrybrook station, or {@code null} if no
     * local station / E10 price is available. Prefers the Ampol station; otherwise
     * falls back to the first local station that lists an E10 price.
     */
    private Double localE10Price(List<LocalFuelStation> stations) {
        if (stations == null || stations.isEmpty()) return null;
        LocalFuelStation preferred = null;
        for (LocalFuelStation s : stations) {
            if (s.getName() != null && s.getName().toUpperCase().contains("AMPOL")) {
                preferred = s;
                break;
            }
        }
        Double e10 = e10Of(preferred);
        if (e10 != null) return e10;
        for (LocalFuelStation s : stations) {
            e10 = e10Of(s);
            if (e10 != null) return e10;
        }
        return null;
    }

    private Double e10Of(LocalFuelStation station) {
        if (station == null || station.getPrices() == null) return null;
        for (LocalFuelPrice lp : station.getPrices()) {
            if ("E10".equals(lp.getFuelType())) return lp.getPrice();
        }
        return null;
    }

    /**
     * The E10 trend (Sydney average + Ampol Foodary local price) over the last
     * {@code weeks} weeks, oldest first (ready for a left-to-right chart). Returns
     * an empty list on any error so the page is never affected.
     */
    @Transactional(readOnly = true)
    public List<E10TrendPoint> recentE10Trend(int weeks) {
        try {
            LocalDate since = LocalDate.now(SYDNEY).minusWeeks(weeks);
            return repository.findE10TrendSince(since);
        } catch (Exception e) {
            log.warn("Could not load E10 price history: {}", e.getMessage());
            return List.of();
        }
    }
}
