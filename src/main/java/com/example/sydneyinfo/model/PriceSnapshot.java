package com.example.sydneyinfo.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One row per day, capturing the fuel prices at the time of the first dashboard
 * load that day. The per-fuel-type Sydney-metro figures live in
 * {@link PriceSnapshotItem}; the single Ampol Foodary Cherrybrook E10 price is
 * stored inline as {@code localE10Price}.
 *
 * <p>{@code snapshotDate} carries a UNIQUE constraint so at most one snapshot can
 * exist per calendar day (Sydney time), even if two requests race — see
 * {@code PriceRecorder}.
 */
@Entity
@Table(name = "price_snapshot")
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The Sydney calendar day this snapshot belongs to (one snapshot per day). */
    @Column(name = "snapshot_date", nullable = false, unique = true)
    private LocalDate snapshotDate;

    /** The exact instant the snapshot was recorded. */
    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "region")
    private String region;

    /** E10 price at the Ampol Foodary Cherrybrook station, if available that day. */
    @Column(name = "local_e10_price")
    private Double localE10Price;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PriceSnapshotItem> items = new ArrayList<>();

    public PriceSnapshot() {
    }

    public PriceSnapshot(OffsetDateTime recordedAt, LocalDate snapshotDate, String region) {
        this.recordedAt = recordedAt;
        this.snapshotDate = snapshotDate;
        this.region = region;
    }

    public void addItem(PriceSnapshotItem item) {
        item.setSnapshot(this);
        this.items.add(item);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public Double getLocalE10Price() { return localE10Price; }
    public void setLocalE10Price(Double localE10Price) { this.localE10Price = localE10Price; }
    public List<PriceSnapshotItem> getItems() { return items; }
    public void setItems(List<PriceSnapshotItem> items) { this.items = items; }
}
