package com.example.sydneyinfo.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One row per dashboard page load, capturing the moment the fuel prices were
 * displayed. The individual per-fuel-type figures live in {@link PriceSnapshotItem}.
 */
@Entity
@Table(name = "price_snapshot")
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** When this snapshot was recorded (page-load time). */
    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "region")
    private String region;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PriceSnapshotItem> items = new ArrayList<>();

    public PriceSnapshot() {
    }

    public PriceSnapshot(OffsetDateTime recordedAt, String region) {
        this.recordedAt = recordedAt;
        this.region = region;
    }

    public void addItem(PriceSnapshotItem item) {
        item.setSnapshot(this);
        this.items.add(item);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(OffsetDateTime recordedAt) { this.recordedAt = recordedAt; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public List<PriceSnapshotItem> getItems() { return items; }
    public void setItems(List<PriceSnapshotItem> items) { this.items = items; }
}
