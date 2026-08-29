package com.example.sydneyinfo;

import com.example.sydneyinfo.model.FuelInfo;
import com.example.sydneyinfo.model.FuelPrice;
import com.example.sydneyinfo.model.PricePoint;
import com.example.sydneyinfo.model.PriceSnapshot;
import com.example.sydneyinfo.model.PriceSnapshotItem;
import com.example.sydneyinfo.repository.PriceSnapshotRepository;
import com.example.sydneyinfo.service.PriceRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real persistence path (PriceRecorder -> repository -> cascade to
 * items) against a live PostgreSQL database.
 *
 * <p>Only runs when a database is configured via the {@code DATABASE_URL}
 * environment variable (as on Railway); otherwise persistence auto-disables, the
 * PriceRecorder bean is absent, and this class is skipped so it never breaks a
 * database-less CI build.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = ".+",
    disabledReason = "No DATABASE_URL — persistence disabled, integration test skipped")
class PriceRecorderIntegrationTest {

    private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");

    @Autowired private PriceRecorder priceRecorder;
    @Autowired private PriceSnapshotRepository repository;

    @Test
    @Transactional  // keep the persistence context open for lazy item access, and roll back after
    void recordsAtMostOneSnapshotPerDay() {
        long before = repository.count();

        FuelInfo info = fuelInfo();
        priceRecorder.record(info);
        priceRecorder.record(info); // same day -> must be a no-op

        assertEquals(before + 1, repository.count(), "at most one snapshot per day");

        PriceSnapshot latest = repository.findAll().stream()
            .max((a, b) -> a.getId().compareTo(b.getId()))
            .orElseThrow();
        assertNotNull(latest.getRecordedAt(), "recorded date must be set");
        assertEquals(LocalDate.now(SYDNEY), latest.getSnapshotDate());
        assertEquals("Sydney Metro", latest.getRegion());
        assertEquals(2, latest.getItems().size(), "one item per fuel type");
        assertTrue(latest.getItems().stream().anyMatch(i -> "U91".equals(i.getFuelType())));
    }

    @Test
    @Transactional
    void recentHistoryReturnsE10SeriesOldestFirst() {
        LocalDate today = LocalDate.now(SYDNEY);
        OffsetDateTime now = ZonedDateTime.now(SYDNEY).toOffsetDateTime();

        // Three days of history: day-2 = 180.0, day-1 = 181.0, today = 182.0
        for (int d = 2; d >= 0; d--) {
            PriceSnapshot s = new PriceSnapshot(now.minusDays(d), today.minusDays(d), "Sydney Metro");
            s.addItem(new PriceSnapshotItem("E10", "E10 Ethanol", 182.0 - d, 175.0, 190.0, 40));
            repository.save(s);
        }

        List<PricePoint> series = priceRecorder.recentHistory("E10", 5);

        assertEquals(3, series.size(), "one point per recorded day");
        // Oldest first, so the chart reads left-to-right in time.
        assertEquals(today.minusDays(2), series.get(0).getDate());
        assertEquals(today, series.get(2).getDate());
        assertEquals(180.0, series.get(0).getPrice(), 0.001);
        assertEquals(182.0, series.get(2).getPrice(), 0.001);
    }

    private static FuelInfo fuelInfo() {
        FuelInfo info = new FuelInfo();
        info.setDataAvailable(true);
        info.setRegion("Sydney Metro");
        info.setPrices(List.of(
            price("U91", "Unleaded 91", 189.9, 179.9, 199.9, 42),
            price("E10", "E10 Ethanol", 182.0, 175.0, 190.0, 40)));
        return info;
    }

    private static FuelPrice price(String type, String label, double avg,
                                   double min, double max, int count) {
        FuelPrice p = new FuelPrice();
        p.setFuelType(type);
        p.setFuelTypeLabel(label);
        p.setAveragePrice(avg);
        p.setMinPrice(min);
        p.setMaxPrice(max);
        p.setStationCount(count);
        return p;
    }
}
