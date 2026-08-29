package com.example.sydneyinfo.service;

import com.example.sydneyinfo.model.FuelInfo;
import com.example.sydneyinfo.model.FuelPrice;
import com.example.sydneyinfo.model.PriceSnapshot;
import com.example.sydneyinfo.model.PriceSnapshotItem;
import com.example.sydneyinfo.repository.PriceSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Persists a snapshot of the current fuel prices on each dashboard load.
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
     * Records the current date/time and the displayed fuel prices. Never throws:
     * a persistence failure is logged and swallowed so it can't break the page.
     */
    @Transactional
    public void record(FuelInfo fuelInfo) {
        if (fuelInfo == null || !fuelInfo.isDataAvailable()
                || fuelInfo.getPrices() == null || fuelInfo.getPrices().isEmpty()) {
            return;
        }
        try {
            OffsetDateTime now = ZonedDateTime.now(SYDNEY).toOffsetDateTime();
            PriceSnapshot snapshot = new PriceSnapshot(now, fuelInfo.getRegion());
            for (FuelPrice p : fuelInfo.getPrices()) {
                snapshot.addItem(new PriceSnapshotItem(
                    p.getFuelType(), p.getFuelTypeLabel(),
                    p.getAveragePrice(), p.getMinPrice(), p.getMaxPrice(),
                    p.getStationCount()));
            }
            repository.save(snapshot);
        } catch (Exception e) {
            log.warn("Could not record price snapshot: {}", e.getMessage());
        }
    }
}
