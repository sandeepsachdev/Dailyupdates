package com.example.sydneyinfo.service;

import com.example.sydneyinfo.model.FuelInfo;
import com.example.sydneyinfo.model.FuelPrice;
import com.example.sydneyinfo.model.PricePoint;
import com.example.sydneyinfo.model.PriceSnapshot;
import com.example.sydneyinfo.model.PriceSnapshotItem;
import com.example.sydneyinfo.repository.PriceSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persists a daily snapshot of the current fuel prices, and reads back recent
 * history for the price-trend chart.
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
            repository.save(snapshot);
        } catch (DataIntegrityViolationException e) {
            // Another concurrent request recorded today's snapshot first
            // (unique constraint on snapshot_date) — that's fine, ignore.
        } catch (Exception e) {
            log.warn("Could not record price snapshot: {}", e.getMessage());
        }
    }

    /**
     * The most recent price points for one fuel type, oldest first (ready for a
     * left-to-right chart). Returns an empty list on any error so the page is
     * never affected.
     */
    @Transactional(readOnly = true)
    public List<PricePoint> recentHistory(String fuelType, int limit) {
        try {
            List<PricePoint> points =
                new ArrayList<>(repository.findRecentPoints(fuelType, PageRequest.of(0, limit)));
            Collections.reverse(points); // newest-first -> oldest-first
            return points;
        } catch (Exception e) {
            log.warn("Could not load price history: {}", e.getMessage());
            return List.of();
        }
    }
}
