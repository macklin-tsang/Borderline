package com.geofence.controller;

import com.geofence.dto.request.DeviceCreateRequest;
import com.geofence.dto.request.LocationPingRequest;
import com.geofence.dto.response.DeviceResponse;
import com.geofence.security.JwtUserDetails;
import com.geofence.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    public DeviceResponse create(@Valid @RequestBody DeviceCreateRequest request, Authentication auth) {
        return deviceService.create(userId(auth), request);
    }

    @GetMapping
    public List<DeviceResponse> list(Authentication auth) {
        return deviceService.list(userId(auth));
    }

    @GetMapping("/{id}")
    public DeviceResponse getOne(@PathVariable UUID id, Authentication auth) {
        return deviceService.getOne(userId(auth), id);
    }

    @PostMapping("/{id}/location")
    public DeviceResponse locationPing(@PathVariable UUID id,
                                       @Valid @RequestBody LocationPingRequest request,
                                       Authentication auth) {
        return deviceService.locationPing(userId(auth), id, request);
    }

    private UUID userId(Authentication auth) {
        return ((JwtUserDetails) auth.getPrincipal()).userId();
    }
}
