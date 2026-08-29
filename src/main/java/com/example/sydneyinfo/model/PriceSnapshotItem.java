package com.example.sydneyinfo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * The average/min/max price for a single fuel type within a {@link PriceSnapshot}.
 */
@Entity
@Table(name = "price_snapshot_item")
public class PriceSnapshotItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private PriceSnapshot snapshot;

    @Column(name = "fuel_type", nullable = false)
    private String fuelType;

    @Column(name = "fuel_label")
    private String fuelLabel;

    @Column(name = "average_price")
    private double averagePrice;

    @Column(name = "min_price")
    private double minPrice;

    @Column(name = "max_price")
    private double maxPrice;

    @Column(name = "station_count")
    private int stationCount;

    public PriceSnapshotItem() {
    }

    public PriceSnapshotItem(String fuelType, String fuelLabel, double averagePrice,
                             double minPrice, double maxPrice, int stationCount) {
        this.fuelType = fuelType;
        this.fuelLabel = fuelLabel;
        this.averagePrice = averagePrice;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.stationCount = stationCount;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public PriceSnapshot getSnapshot() { return snapshot; }
    public void setSnapshot(PriceSnapshot snapshot) { this.snapshot = snapshot; }
    public String getFuelType() { return fuelType; }
    public void setFuelType(String fuelType) { this.fuelType = fuelType; }
    public String getFuelLabel() { return fuelLabel; }
    public void setFuelLabel(String fuelLabel) { this.fuelLabel = fuelLabel; }
    public double getAveragePrice() { return averagePrice; }
    public void setAveragePrice(double averagePrice) { this.averagePrice = averagePrice; }
    public double getMinPrice() { return minPrice; }
    public void setMinPrice(double minPrice) { this.minPrice = minPrice; }
    public double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(double maxPrice) { this.maxPrice = maxPrice; }
    public int getStationCount() { return stationCount; }
    public void setStationCount(int stationCount) { this.stationCount = stationCount; }
}
