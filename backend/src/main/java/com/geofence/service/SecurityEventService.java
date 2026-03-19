package com.geofence.service;

import com.geofence.dto.response.SecurityEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Batches security events and flushes them to the user's WebSocket queue every 500ms.
 *
 * Why batch?  Individual sends per request under load would create per-WebSocket-frame
 * overhead.  A 500ms window collapses bursts (e.g. a credential-stuffing wave) into
 * a single frame per user — reducing client-side DOM churn while still being near-real-time.
 *
 * Thread safety: ConcurrentLinkedQueue handles concurrent enqueue from filter threads;
 * the @Scheduled flush runs on a single scheduler thread.
 */
@Service
public class SecurityEventService {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventService.class);

    private final ConcurrentLinkedQueue<QueuedEvent> queue = new ConcurrentLinkedQueue<>();
    private final SimpMessagingTemplate messagingTemplate;

    public SecurityEventService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Enqueues a security event for a specific user. Non-blocking — called from
     * ApiKeyAuthFilter on the servlet thread.
     */
    public void enqueue(String userId, UUID apiKeyId, String type,
                        String endpoint, String detail) {
        queue.offer(new QueuedEvent(userId, new SecurityEventMessage(
                type, apiKeyId, endpoint, detail, Instant.now())));
    }

    /**
     * Drains the queue every 500ms, groups events by userId, and sends each
     * user's batch to /user/{userId}/queue/security-events.
     */
    @Scheduled(fixedDelay = 500)
    public void flush() {
        if (queue.isEmpty()) {
            return;
        }
        Map<String, List<SecurityEventMessage>> byUser = new LinkedHashMap<>();
        QueuedEvent event;
        while ((event = queue.poll()) != null) {
            byUser.computeIfAbsent(event.userId(), k -> new ArrayList<>())
                  .add(event.message());
        }
        byUser.forEach((userId, events) -> {
            try {
                messagingTemplate.convertAndSendToUser(userId, "/queue/security-events", events);
            } catch (Exception e) {
                log.warn("Failed to deliver {} security events to user {}: {}",
                        events.size(), userId, e.getMessage());
            }
        });
    }

    private record QueuedEvent(String userId, SecurityEventMessage message) {}
}
