package com.geofence.service;

import com.geofence.dto.response.RequestLogResponse;
import com.geofence.model.ApiRequestLog;
import com.geofence.repository.ApiRequestLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Buffers API request log entries in memory and flushes them to the database
 * every second via a @Scheduled batch INSERT.
 *
 * Why async? This service sits in the ApiKeyAuthFilter hot path. A synchronous
 * @Transactional INSERT on every request competes for HikariCP connections under
 * load and adds latency to every API response. Enqueuing to a ConcurrentLinkedQueue
 * is non-blocking; the flush runs on the scheduler thread pool and issues a single
 * saveAll() per second, amortising transaction and network overhead across all
 * requests that arrived in the window.
 *
 * Thread safety: ConcurrentLinkedQueue handles concurrent enqueue from multiple
 * servlet threads; poll() in flush() is atomic so no entry is processed twice.
 */
@Service
public class RequestLogService {

    private static final Logger log = LoggerFactory.getLogger(RequestLogService.class);

    private final ConcurrentLinkedQueue<ApiRequestLog> queue = new ConcurrentLinkedQueue<>();
    private final ApiRequestLogRepository repo;

    public RequestLogService(ApiRequestLogRepository repo) {
        this.repo = repo;
    }

    /**
     * Non-blocking enqueue — called from ApiKeyAuthFilter on the servlet thread.
     * No DB interaction, no transaction opened.
     */
    public void enqueue(UUID apiKeyId, String endpoint, String method, int statusCode,
                        Integer durationMs, boolean wasBlocked, String blockReason) {
        queue.offer(new ApiRequestLog(apiKeyId, endpoint, method, statusCode,
                durationMs, wasBlocked, blockReason));
    }

    /**
     * Drains the queue every second and batch-inserts into api_request_logs.
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void flush() {
        if (queue.isEmpty()) {
            return;
        }
        List<ApiRequestLog> batch = new ArrayList<>();
        ApiRequestLog entry;
        while ((entry = queue.poll()) != null) {
            batch.add(entry);
        }
        if (!batch.isEmpty()) {
            repo.saveAll(batch);
        }
    }

    @Transactional(readOnly = true)
    public List<RequestLogResponse> findRecentByUserId(UUID userId, int limit) {
        int clamped = Math.min(limit, 500);
        return repo.findRecentByUserId(userId, PageRequest.of(0, clamped))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private RequestLogResponse toResponse(ApiRequestLog l) {
        return new RequestLogResponse(
                l.getId(), l.getApiKeyId(), l.getEndpoint(), l.getMethod(),
                l.getStatusCode(), l.getDurationMs(),
                l.isWasBlocked(), l.getBlockReason(), l.getCreatedAt());
    }

    /**
     * Deletes log entries older than 30 days. Runs daily at 03:00 UTC.
     * Keeps the api_request_logs table from growing unbounded.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void purgeOldLogs() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        int deleted = repo.deleteOlderThan(cutoff);
        log.info("Purged {} api_request_log entries older than 30 days", deleted);
    }
}
