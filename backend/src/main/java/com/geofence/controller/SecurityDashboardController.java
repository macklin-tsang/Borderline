package com.geofence.controller;

import com.geofence.dto.response.RequestLogResponse;
import com.geofence.security.AuthUtils;
import com.geofence.service.RequestLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/security")
@PreAuthorize("isAuthenticated()")
public class SecurityDashboardController {

    private final RequestLogService requestLogService;

    public SecurityDashboardController(RequestLogService requestLogService) {
        this.requestLogService = requestLogService;
    }

    /**
     * Returns recent API request logs for the authenticated user's keys.
     * Used by the security dashboard to show traffic, blocked requests, and geo data.
     *
     * @param limit max records to return (default 100, max 500)
     */
    @GetMapping("/events")
    public ResponseEntity<List<RequestLogResponse>> recentEvents(
            Authentication auth,
            @RequestParam(defaultValue = "100") int limit) {
        List<RequestLogResponse> logs = requestLogService.findRecentByUserId(AuthUtils.userId(auth), limit);
        return ResponseEntity.ok(logs);
    }
}
