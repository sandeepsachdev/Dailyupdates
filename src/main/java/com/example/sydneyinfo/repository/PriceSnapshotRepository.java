package com.example.sydneyinfo.repository;

import com.example.sydneyinfo.model.E10TrendPoint;
import com.example.sydneyinfo.model.PricePoint;
import com.example.sydneyinfo.model.PriceSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    /** True when a snapshot has already been recorded for the given day. */
    boolean existsBySnapshotDate(LocalDate snapshotDate);

    /**
     * The most recent average price points for one fuel type, newest first. Pass a
     * {@code Pageable} (e.g. {@code PageRequest.of(0, 5)}) to cap the number of entries.
     */
    @Query("select new com.example.sydneyinfo.model.PricePoint(i.snapshot.snapshotDate, i.averagePrice) "
         + "from PriceSnapshotItem i "
         + "where i.fuelType = :fuelType "
         + "order by i.snapshot.snapshotDate desc")
    List<PricePoint> findRecentPoints(@Param("fuelType") String fuelType, Pageable pageable);

    /**
     * The most recent E10 trend points, newest first: the Sydney metro average
     * paired with the Ampol Foodary Cherrybrook price recorded that day.
     */
    @Query("select new com.example.sydneyinfo.model.E10TrendPoint("
         + "    s.snapshotDate, i.averagePrice, s.localE10Price) "
         + "from PriceSnapshot s join s.items i "
         + "where i.fuelType = 'E10' "
         + "order by s.snapshotDate desc")
    List<E10TrendPoint> findRecentE10Trend(Pageable pageable);
}
