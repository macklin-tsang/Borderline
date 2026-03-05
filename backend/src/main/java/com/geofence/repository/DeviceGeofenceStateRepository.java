package com.geofence.repository;

import com.geofence.model.DeviceGeofenceState;
import com.geofence.model.DeviceGeofenceStateId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;

public interface DeviceGeofenceStateRepository extends JpaRepository<DeviceGeofenceState, DeviceGeofenceStateId> {

    // SELECT FOR UPDATE — prevents duplicate ENTER/EXIT events under concurrent pings
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DeviceGeofenceState s WHERE s.id = :id")
    Optional<DeviceGeofenceState> findByIdForUpdate(@Param("id") DeviceGeofenceStateId id);

    List<DeviceGeofenceState> findByIdDeviceId(UUID deviceId);

    // ON CONFLICT upsert — atomically inserts or updates crossing state
    @Modifying
    @Query(value = """
            INSERT INTO device_geofence_state (device_id, geofence_id, inside, updated_at)
            VALUES (:deviceId, :geofenceId, :inside, NOW())
            ON CONFLICT (device_id, geofence_id) DO UPDATE
            SET inside = EXCLUDED.inside, updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void upsert(@Param("deviceId") UUID deviceId,
                @Param("geofenceId") UUID geofenceId,
                @Param("inside") boolean inside);
}
