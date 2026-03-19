package com.geofence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DeviceGeofenceStateId implements Serializable {

    // Explicit @Column names guard against naming-strategy changes (e.g. switching to
    // PhysicalNamingStrategyStandardImpl which would map deviceId → "deviceId" not "device_id").
    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "geofence_id")
    private UUID geofenceId;

    protected DeviceGeofenceStateId() {}

    public DeviceGeofenceStateId(UUID deviceId, UUID geofenceId) {
        this.deviceId = deviceId;
        this.geofenceId = geofenceId;
    }

    public UUID getDeviceId() { return deviceId; }
    public UUID getGeofenceId() { return geofenceId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeviceGeofenceStateId that)) return false;
        return Objects.equals(deviceId, that.deviceId) && Objects.equals(geofenceId, that.geofenceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, geofenceId);
    }
}
