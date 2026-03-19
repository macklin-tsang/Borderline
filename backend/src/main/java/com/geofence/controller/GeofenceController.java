package com.geofence.controller;

import com.geofence.dto.request.GeofenceRequest;
import com.geofence.dto.response.GeofenceResponse;
import com.geofence.security.AuthUtils;
import com.geofence.service.GeofenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/geofences")
public class GeofenceController {

    private final GeofenceService geofenceService;

    public GeofenceController(GeofenceService geofenceService) {
        this.geofenceService = geofenceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_write')")
    public GeofenceResponse create(@Valid @RequestBody GeofenceRequest request, Authentication auth) {
        return geofenceService.create(userId(auth), request);
    }

    @GetMapping
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_read') or hasAuthority('SCOPE_write')")
    public List<GeofenceResponse> list(Authentication auth) {
        return geofenceService.list(userId(auth));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_read') or hasAuthority('SCOPE_write')")
    public GeofenceResponse getOne(@PathVariable UUID id, Authentication auth) {
        return geofenceService.getOne(userId(auth), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_write')")
    public GeofenceResponse update(@PathVariable UUID id,
                                   @Valid @RequestBody GeofenceRequest request,
                                   Authentication auth) {
        return geofenceService.update(userId(auth), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_write')")
    public void delete(@PathVariable UUID id, Authentication auth) {
        geofenceService.delete(userId(auth), id);
    }

    private UUID userId(Authentication auth) {
        return AuthUtils.userId(auth);
    }
}
