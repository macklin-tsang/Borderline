package com.geofence.repository;

import com.geofence.model.Device;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    List<Device> findByUserId(UUID userId);

    Optional<Device> findByIdAndUserId(UUID id, UUID userId);

    // Used in locationPing to serialize concurrent pings for the same device.
    // A SELECT FOR UPDATE on the device row prevents two concurrent pings from
    // both reading "no state row exists" and both firing a duplicate ENTER event.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Device d WHERE d.id = :id AND d.userId = :userId")
    Optional<Device> findByIdAndUserIdForUpdate(@Param("id") UUID id,
                                                @Param("userId") UUID userId);
}
