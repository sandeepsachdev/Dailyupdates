package com.example.sydneyinfo;

import com.example.sydneyinfo.model.FuelInfo;
import com.example.sydneyinfo.model.FuelPrice;
import com.example.sydneyinfo.model.PriceSnapshot;
import com.example.sydneyinfo.repository.PriceSnapshotRepository;
import com.example.sydneyinfo.service.PriceRecorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired private PriceRecorder priceRecorder;
    @Autowired private PriceSnapshotRepository repository;

    @Test
    @Transactional  // keep the persistence context open for lazy item access, and roll back after
    void recordsDateAndPricesWhenDataAvailable() {
        long before = repository.count();

        FuelInfo info = new FuelInfo();
        info.setDataAvailable(true);
        info.setRegion("Sydney Metro");
        info.setPrices(List.of(
            price("U91", "Unleaded 91", 189.9, 179.9, 199.9, 42),
            price("DL",  "Diesel",      201.3, 195.0, 210.0, 37)));

        priceRecorder.record(info);

        assertEquals(before + 1, repository.count(), "one snapshot per record() call");

        PriceSnapshot latest = repository.findAll().stream()
            .max((a, b) -> a.getId().compareTo(b.getId()))
            .orElseThrow();
        assertNotNull(latest.getRecordedAt(), "recorded date must be set");
        assertEquals("Sydney Metro", latest.getRegion());
        assertEquals(2, latest.getItems().size(), "one item per fuel type");
        assertTrue(latest.getItems().stream().anyMatch(i -> "U91".equals(i.getFuelType())));
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
