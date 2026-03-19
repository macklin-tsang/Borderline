package com.geofence.controller;

import com.geofence.dto.request.DeviceCreateRequest;
import com.geofence.dto.request.DeviceRenameRequest;
import com.geofence.dto.request.LocationPingRequest;
import com.geofence.dto.response.DeviceResponse;
import com.geofence.security.AuthUtils;
import com.geofence.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_write')")
    public DeviceResponse create(@Valid @RequestBody DeviceCreateRequest request, Authentication auth) {
        return deviceService.create(userId(auth), request);
    }

    @GetMapping
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_read') or hasAuthority('SCOPE_write')")
    public List<DeviceResponse> list(Authentication auth) {
        return deviceService.list(userId(auth));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_read') or hasAuthority('SCOPE_write')")
    public DeviceResponse getOne(@PathVariable UUID id, Authentication auth) {
        return deviceService.getOne(userId(auth), id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_write')")
    public DeviceResponse rename(@PathVariable UUID id,
                                 @Valid @RequestBody DeviceRenameRequest request,
                                 Authentication auth) {
        return deviceService.rename(userId(auth), id, request.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_write')")
    public void delete(@PathVariable UUID id, Authentication auth) {
        deviceService.delete(userId(auth), id);
    }

    @PostMapping("/{id}/location")
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_write')")
    public DeviceResponse locationPing(@PathVariable UUID id,
                                       @Valid @RequestBody LocationPingRequest request,
                                       Authentication auth) {
        return deviceService.locationPing(userId(auth), id, request);
    }

    private UUID userId(Authentication auth) {
        return AuthUtils.userId(auth);
    }
}
