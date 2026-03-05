package com.geofence.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "device_geofence_state")
public class DeviceGeofenceState {

    @EmbeddedId
    private DeviceGeofenceStateId id;

    @Column(nullable = false)
    private boolean inside;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeviceGeofenceState() {}

    public DeviceGeofenceState(DeviceGeofenceStateId id, boolean inside) {
        this.id = id;
        this.inside = inside;
        this.updatedAt = Instant.now();
    }

    public DeviceGeofenceStateId getId() { return id; }
    public boolean isInside() { return inside; }
    public Instant getUpdatedAt() { return updatedAt; }
}
