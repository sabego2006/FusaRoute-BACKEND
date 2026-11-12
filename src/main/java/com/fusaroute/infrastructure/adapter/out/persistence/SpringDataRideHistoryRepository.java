package com.fusaroute.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpringDataRideHistoryRepository extends JpaRepository<RideHistoryJpaEntity, Long> {

    /** Lee en el orden de ix_ride_history_user_boarded y trae nombre y letrero de la ruta. */
    @Query(value = """
            SELECT h.id AS id, h.user_id AS userId, h.route_id AS routeId,
                   r.name AS routeName, r.sign_file AS signFile, r.status AS routeStatus,
                   h.destination_label AS destinationLabel, h.destination_place_id AS destinationPlaceId,
                   h.destination_lat AS destinationLat, h.destination_lng AS destinationLng,
                   h.origin_label AS originLabel, h.boarded_at AS boardedAt
              FROM ride_history h
              JOIN routes r ON r.id = h.route_id
             WHERE h.user_id = :userId
             ORDER BY h.boarded_at DESC, h.id DESC
             LIMIT :limit
            """, nativeQuery = true)
    List<RideHistoryRow> findRecentByUser(@Param("userId") Long userId, @Param("limit") int limit);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM ride_history
             WHERE user_id = :userId
               AND id NOT IN (
                   SELECT id FROM ride_history
                    WHERE user_id = :userId
                    ORDER BY boarded_at DESC, id DESC
                    LIMIT :keep)
            """, nativeQuery = true)
    int deleteOlderBeyond(@Param("userId") Long userId, @Param("keep") int keep);
}
