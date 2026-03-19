package com.geofence.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geofence.model.ApiKey;
import com.geofence.service.ApiKeyService;
import com.geofence.service.RateLimitService;
import com.geofence.service.RequestLogService;
import com.geofence.service.SecurityEventService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates API key authentication and rate limiting.
 *
 * Not @Component — registered as a @Bean in SecurityConfig to avoid
 * double-registration in the servlet container filter chain.
 *
 * Filter responsibilities (delegating pattern — each step is a single concern):
 *   1. Check for X-Api-Key header — skip if absent (falls through to JWT filter)
 *   2. Validate key: prefix lookup → HMAC verify → expiry check (ApiKeyService)
 *   3. Scope check: does this key have permission for the requested method? (ScopeValidator)
 *   4. Rate limit: per-key bucket + per-IP bucket (RateLimitService) → 429 if exceeded
 *   5. Set Authentication in SecurityContext with ApiKeyPrincipal + scope authorities
 *   6. Log the request + enqueue any security events for the dashboard
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    static final String API_KEY_HEADER = "X-Api-Key";

    private final ApiKeyService apiKeyService;
    private final ScopeValidator scopeValidator;
    private final RateLimitService rateLimitService;
    private final RequestLogService requestLogService;
    private final SecurityEventService securityEventService;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService,
                            ScopeValidator scopeValidator,
                            RateLimitService rateLimitService,
                            RequestLogService requestLogService,
                            SecurityEventService securityEventService,
                            ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.scopeValidator = scopeValidator;
        this.rateLimitService = rateLimitService;
        this.requestLogService = requestLogService;
        this.securityEventService = securityEventService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String rawKey = request.getHeader(API_KEY_HEADER);
        if (rawKey == null) {
            chain.doFilter(request, response);   // no API key — let JWT filter handle it
            return;
        }

        long startMs = System.currentTimeMillis();
        String clientIp = extractClientIp(request);
        String endpoint = request.getRequestURI();
        String method = request.getMethod();

        Optional<ApiKey> apiKeyOpt = apiKeyService.validate(rawKey);
        if (apiKeyOpt.isEmpty()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Invalid or expired API key");
            saveLog(null, endpoint, method, 401, startMs, true, "INVALID_KEY");
            return;
        }

        ApiKey apiKey = apiKeyOpt.get();
        String userId = apiKey.getUserId().toString();

        // Scope check
        if (!scopeValidator.isAllowed(apiKey.getScopes(), request)) {
            writeError(response, HttpStatus.FORBIDDEN, "Insufficient scope for this endpoint");
            saveLog(apiKey.getId(), endpoint, method, 403, startMs, true, "SCOPE_VIOLATION");
            securityEventService.enqueue(userId, apiKey.getId(), "SCOPE_VIOLATION",
                    endpoint, "Key scope does not permit " + method + " requests");
            return;
        }

        // Rate limiting
        if (!rateLimitService.isAllowed(apiKey.getId().toString(),
                apiKey.getRateLimitPerMinute(), clientIp)) {
            response.setHeader("Retry-After", "60");
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
            saveLog(apiKey.getId(), endpoint, method, 429, startMs, true, "RATE_LIMIT");
            securityEventService.enqueue(userId, apiKey.getId(), "RATE_LIMIT",
                    endpoint, "Rate limit exceeded from " + clientIp);
            return;
        }

        ApiKeyPrincipal principal = new ApiKeyPrincipal(
                apiKey.getUserId(), apiKey.getId(), apiKey.getScopes());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(request, response);

        // Log after chain completes so we capture the real response status code
        saveLog(apiKey.getId(), endpoint, method, response.getStatus(), startMs, false, null);
    }

    private void saveLog(UUID apiKeyId, String endpoint, String method,
                         int statusCode, long startMs, boolean wasBlocked, String blockReason) {
        int durationMs = (int) (System.currentTimeMillis() - startMs);
        requestLogService.enqueue(apiKeyId, endpoint, method, statusCode,
                durationMs, wasBlocked, blockReason);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeError(HttpServletResponse response,
                            HttpStatus status, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(status.getReasonPhrase());
        problem.setDetail(detail);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
