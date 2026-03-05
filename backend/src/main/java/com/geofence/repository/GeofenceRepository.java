package com.geofence.repository;

import com.geofence.model.Geofence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GeofenceRepository extends JpaRepository<Geofence, UUID> {

    List<Geofence> findByUserIdAndActiveTrue(UUID userId);

    Optional<Geofence> findByIdAndUserId(UUID id, UUID userId);

    // Two-step spatial filter: && bounding box (GIST) then ST_Contains (precise)
    @Query(value = """
            SELECT * FROM geofences
            WHERE user_id = :userId
              AND active = true
              AND geometry && ST_SetSRID(ST_Point(:lon, :lat), 4326)
              AND ST_Contains(geometry, ST_SetSRID(ST_Point(:lon, :lat), 4326))
            """, nativeQuery = true)
    List<Geofence> findContaining(@Param("userId") UUID userId,
                                  @Param("lat") double lat,
                                  @Param("lon") double lon);
}
