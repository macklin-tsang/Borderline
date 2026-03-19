package com.geofence.controller;

import com.geofence.dto.request.ApiKeyCreateRequest;
import com.geofence.dto.response.ApiKeyCreatedResponse;
import com.geofence.dto.response.ApiKeyResponse;
import com.geofence.security.AuthUtils;
import com.geofence.service.ApiKeyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_write')")
    public ApiKeyCreatedResponse create(@Valid @RequestBody ApiKeyCreateRequest request,
                                        Authentication auth) {
        return apiKeyService.generate(userId(auth), request);
    }

    @GetMapping
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_read') or hasAuthority('SCOPE_write')")
    public List<ApiKeyResponse> list(Authentication auth) {
        return apiKeyService.list(userId(auth));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('USER') or hasAuthority('SCOPE_write')")
    public void revoke(@PathVariable UUID id, Authentication auth) {
        apiKeyService.revoke(userId(auth), id);
    }

    private UUID userId(Authentication auth) {
        return AuthUtils.userId(auth);
    }
}
