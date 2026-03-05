package com.geofence.model;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Geometry;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "geofences")
public class Geofence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "geometry(Geometry,4326)", nullable = false)
    private Geometry geometry;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "alert_on_entry", nullable = false)
    private boolean alertOnEntry = false;

    @Column(name = "alert_on_exit", nullable = false)
    private boolean alertOnExit = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    protected Geofence() {}

    public Geofence(UUID userId, String name, Geometry geometry, boolean alertOnEntry, boolean alertOnExit) {
        this.userId = userId;
        this.name = name;
        this.geometry = geometry;
        this.alertOnEntry = alertOnEntry;
        this.alertOnExit = alertOnExit;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public Geometry getGeometry() { return geometry; }
    public boolean isActive() { return active; }
    public boolean isAlertOnEntry() { return alertOnEntry; }
    public boolean isAlertOnExit() { return alertOnExit; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setGeometry(Geometry geometry) { this.geometry = geometry; }
    public void setActive(boolean active) { this.active = active; }
    public void setAlertOnEntry(boolean alertOnEntry) { this.alertOnEntry = alertOnEntry; }
    public void setAlertOnExit(boolean alertOnExit) { this.alertOnExit = alertOnExit; }
}
