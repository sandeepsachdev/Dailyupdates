package com.example.sydneyinfo;

import com.example.sydneyinfo.model.E10TrendPoint;
import com.example.sydneyinfo.model.FuelInfo;
import com.example.sydneyinfo.model.FuelPrice;
import com.example.sydneyinfo.model.LocalFuelPrice;
import com.example.sydneyinfo.model.LocalFuelStation;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void recordsAtMostOneSnapshotPerDayWithLocalE10() {
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
        assertNotNull(latest.getLocalE10Price(), "local Ampol E10 price recorded");
        assertEquals(197.5, latest.getLocalE10Price(), 0.001);
    }

    @Test
    @Transactional
    void recentE10TrendReturnsLastSixWeeksOldestFirst() {
        LocalDate today = LocalDate.now(SYDNEY);
        OffsetDateTime now = ZonedDateTime.now(SYDNEY).toOffsetDateTime();

        // In range: day-2 avg 182 local 179 ; day-1 avg 181 local null ; today avg 180 local 178
        Double[] locals = { 179.0, null, 178.0 };
        for (int d = 2; d >= 0; d--) {
            PriceSnapshot s = new PriceSnapshot(now.minusDays(d), today.minusDays(d), "Sydney Metro");
            s.addItem(new PriceSnapshotItem("E10", "E10 Ethanol", 180.0 + d, 175.0, 190.0, 40));
            s.setLocalE10Price(locals[2 - d]);
            repository.save(s);
        }
        // Out of range: 7 weeks ago -> must be excluded from a 6-week window
        PriceSnapshot old = new PriceSnapshot(now.minusWeeks(7), today.minusWeeks(7), "Sydney Metro");
        old.addItem(new PriceSnapshotItem("E10", "E10 Ethanol", 100.0, 90.0, 110.0, 40));
        old.setLocalE10Price(99.0);
        repository.save(old);

        List<E10TrendPoint> series = priceRecorder.recentE10Trend(6);

        assertEquals(3, series.size(), "only the last 6 weeks, oldest first");
        assertEquals(today.minusDays(2), series.get(0).getDate());
        assertEquals(today, series.get(2).getDate());
        assertEquals(182.0, series.get(0).getAverage(), 0.001);
        assertEquals(180.0, series.get(2).getAverage(), 0.001);
        assertEquals(179.0, series.get(0).getLocal(), 0.001);
        assertNull(series.get(1).getLocal(), "missing local price stays null");
        assertEquals(178.0, series.get(2).getLocal(), 0.001);
    }

    private static FuelInfo fuelInfo() {
        FuelInfo info = new FuelInfo();
        info.setDataAvailable(true);
        info.setRegion("Sydney Metro");
        info.setPrices(List.of(
            price("U91", "Unleaded 91", 189.9, 179.9, 199.9, 42),
            price("E10", "E10 Ethanol", 182.0, 175.0, 190.0, 40)));
        info.setLocalStations(List.of(ampolFoodary()));
        return info;
    }

    private static LocalFuelStation ampolFoodary() {
        LocalFuelStation s = new LocalFuelStation();
        s.setName("Ampol Foodary Cherrybrook");
        s.setBrand("Ampol");
        s.setAddress("Cherrybrook");
        s.setPrices(List.of(localPrice("E10", 197.5), localPrice("U91", 201.5)));
        return s;
    }

    private static LocalFuelPrice localPrice(String type, double price) {
        LocalFuelPrice lp = new LocalFuelPrice();
        lp.setFuelType(type);
        lp.setFuelTypeLabel(type);
        lp.setPrice(price);
        return lp;
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
