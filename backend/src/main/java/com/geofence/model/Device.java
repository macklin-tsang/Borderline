package com.geofence.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(name = "last_lat")
    private Double lastLat;

    @Column(name = "last_lon")
    private Double lastLon;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    protected Device() {}

    public Device(UUID userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public Double getLastLat() { return lastLat; }
    public Double getLastLon() { return lastLon; }
    public Instant getLastSeen() { return lastSeen; }
    public Instant getCreatedAt() { return createdAt; }

    public void setLastLat(Double lastLat) { this.lastLat = lastLat; }
    public void setLastLon(Double lastLon) { this.lastLon = lastLon; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
}
