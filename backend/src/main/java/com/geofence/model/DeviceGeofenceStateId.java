package com.geofence.model;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DeviceGeofenceStateId implements Serializable {

    private UUID deviceId;
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
