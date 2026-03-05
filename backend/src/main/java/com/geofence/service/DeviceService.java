package com.geofence.service;

import com.geofence.dto.request.DeviceCreateRequest;
import com.geofence.dto.request.LocationPingRequest;
import com.geofence.dto.response.DeviceResponse;
import com.geofence.exception.DeviceNotFoundException;
import com.geofence.model.Device;
import com.geofence.model.DeviceGeofenceState;
import com.geofence.model.DeviceGeofenceStateId;
import com.geofence.model.Geofence;
import com.geofence.repository.DeviceGeofenceStateRepository;
import com.geofence.repository.DeviceRepository;
import com.geofence.repository.GeofenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class DeviceService {

    private final DeviceRepository deviceRepo;
    private final GeofenceRepository geofenceRepo;
    private final DeviceGeofenceStateRepository stateRepo;
    private final AlertService alertService;

    public DeviceService(DeviceRepository deviceRepo,
                         GeofenceRepository geofenceRepo,
                         DeviceGeofenceStateRepository stateRepo,
                         AlertService alertService) {
        this.deviceRepo = deviceRepo;
        this.geofenceRepo = geofenceRepo;
        this.stateRepo = stateRepo;
        this.alertService = alertService;
    }

    public DeviceResponse create(UUID userId, DeviceCreateRequest req) {
        Device device = new Device(userId, req.name());
        return toResponse(deviceRepo.save(device));
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> list(UUID userId) {
        return deviceRepo.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponse getOne(UUID userId, UUID deviceId) {
        return deviceRepo.findByIdAndUserId(deviceId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));
    }

    /**
     * Records a location ping and fires ENTER/EXIT WebSocket alerts for any
     * geofence boundary crossings.
     *
     * Concurrency: each device_geofence_state row is locked with SELECT FOR UPDATE
     * so concurrent pings for the same device cannot produce duplicate crossing events.
     */
    public DeviceResponse locationPing(UUID userId, UUID deviceId, LocationPingRequest req) {
        Device device = deviceRepo.findByIdAndUserId(deviceId, userId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));

        device.setLastLat(req.lat());
        device.setLastLon(req.lon());
        device.setLastSeen(Instant.now());
        deviceRepo.save(device);

        // All active geofences owned by the user
        List<Geofence> allFences = geofenceRepo.findByUserIdAndActiveTrue(userId);

        // Geofences that spatially contain the new point (two-step: && + ST_Contains)
        Set<UUID> containingIds = geofenceRepo.findContaining(userId, req.lat(), req.lon())
                .stream().map(Geofence::getId).collect(Collectors.toSet());

        for (Geofence fence : allFences) {
            boolean nowInside = containingIds.contains(fence.getId());

            // SELECT FOR UPDATE — prevents duplicate events under concurrent pings
            DeviceGeofenceStateId stateId = new DeviceGeofenceStateId(deviceId, fence.getId());
            boolean wasInside = stateRepo.findByIdForUpdate(stateId)
                    .map(DeviceGeofenceState::isInside)
                    .orElse(false);

            if (wasInside != nowInside) {
                String alertType = nowInside ? "ENTER" : "EXIT";
                boolean shouldAlert = (nowInside && fence.isAlertOnEntry())
                        || (!nowInside && fence.isAlertOnExit());
                if (shouldAlert) {
                    alertService.sendAlert(userId.toString(), device, fence, alertType);
                }
            }

            stateRepo.upsert(deviceId, fence.getId(), nowInside);
        }

        return toResponse(device);
    }

    private DeviceResponse toResponse(Device d) {
        return new DeviceResponse(
                d.getId(),
                d.getName(),
                d.getLastLat(),
                d.getLastLon(),
                d.getLastSeen(),
                d.getCreatedAt()
        );
    }
}
