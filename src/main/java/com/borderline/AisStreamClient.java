package com.borderline;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps one WebSocket open to AISStream and hands every ship position to the tracker.
 * If the connection drops or goes silent, it reconnects with a growing, jittered delay.
 */
@Component
public class AisStreamClient implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AisStreamClient.class);

    private static final String URL = "wss://stream.aisstream.io/v0/stream";
    private static final long BASE_BACKOFF_MS = 1_000;
    private static final long MAX_BACKOFF_MS = 60_000;
    private static final Duration SILENCE_LIMIT = Duration.ofMinutes(2);   // no message this long = dead connection
    private static final Duration HEALTHY_AFTER = Duration.ofMinutes(1);   // stayed up this long = reset the backoff

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final AnchorageTracker tracker;
    private final TaskScheduler scheduler;
    private final String apiKey;
    private final String subscription;

    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private final AtomicInteger failures = new AtomicInteger();
    private volatile Instant openedAt = Instant.now();
    private volatile Instant lastMessageAt = Instant.now();
    private volatile boolean stopped;

    public AisStreamClient(AnchorageTracker tracker, TaskScheduler scheduler,
                           @Value("${aisstream.api-key:}") String apiKey) {
        this.tracker = tracker;
        this.scheduler = scheduler;
        this.apiKey = apiKey;
        // Only large ships' position reports, inside a box around English Bay, Burrard Inlet and Indian Arm
        this.subscription = JsonMapper.builder().build().writeValueAsString(Map.of(
                "APIKey", apiKey,
                "BoundingBoxes", List.of(List.of(List.of(49.20, -123.40), List.of(49.47, -122.83))),
                "FilterMessageTypes", List.of("PositionReport")));
    }

    @Override
    public void run(ApplicationArguments args) {
        if (apiKey.isBlank()) {
            log.warn("AISSTREAM_API_KEY is not set; not connecting to AISStream");
            return;
        }
        connect();
    }

    private void connect() {
        if (stopped) return;
        log.info("Connecting to AISStream");
        HTTP.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(URL), new Feed())
                .exceptionally(e -> {
                    log.warn("AISStream connect failed: {}", e.toString());
                    scheduleReconnect();
                    return null;
                });
    }

    /** Called when a connection has ended, however it ended. Only the first call per connection counts. */
    private void lost(WebSocket ws) {
        if (!socket.compareAndSet(ws, null)) return;
        if (Duration.between(openedAt, Instant.now()).compareTo(HEALTHY_AFTER) > 0) {
            failures.set(0);
        }
        scheduleReconnect();
    }

    /** Wait 1 s, 2 s, 4 s ... up to 60 s; the random part stops many clients retrying in lockstep. */
    private void scheduleReconnect() {
        if (stopped) return;
        long ceiling = Math.min(MAX_BACKOFF_MS, BASE_BACKOFF_MS << Math.min(failures.getAndIncrement(), 6));
        long delay = ceiling / 2 + ThreadLocalRandom.current().nextLong(ceiling / 2 + 1);
        log.info("Reconnecting to AISStream in {} ms", delay);
        scheduler.schedule(this::connect, Instant.now().plusMillis(delay));
    }

    /** A connection can die without telling us (half-open TCP); silence is the only sign. */
    @Scheduled(fixedDelay = 30_000)
    void watchdog() {
        WebSocket ws = socket.get();
        if (ws != null && Duration.between(lastMessageAt, Instant.now()).compareTo(SILENCE_LIMIT) > 0) {
            log.warn("No messages for {} min, reconnecting", SILENCE_LIMIT.toMinutes());
            ws.abort();
            lost(ws);
        }
    }

    @PreDestroy
    void stop() {
        stopped = true;
        WebSocket ws = socket.getAndSet(null);
        if (ws != null) ws.abort();
    }

    private void process(String message) {
        try {
            Optional<PositionReport> report = PositionReport.parse(message);
            if (report.isPresent()) {
                tracker.handle(report.get(), Instant.now());
            } else {
                // Subscription confirmation, an error such as a bad API key, or an unusable position
                log.info("AISStream: {}", message.length() > 200 ? message.substring(0, 200) + "..." : message);
            }
        } catch (Exception e) {
            // One bad message must never stop the feed
            log.warn("Skipping bad message: {}", e.toString());
        }
    }

    /** One instance per connection, so half-received messages never leak into the next connection. */
    private class Feed implements WebSocket.Listener {

        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        public void onOpen(WebSocket ws) {
            socket.set(ws);
            openedAt = lastMessageAt = Instant.now();
            log.info("Connected to AISStream");
            ws.sendText(subscription, true);  // AISStream closes the connection if this takes over 3 s
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            receive(ws, chunk, last);
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            receive(ws, data.toString().getBytes(StandardCharsets.UTF_8), last);
            return null;
        }

        /** A message can arrive in several frames; collect them, then process the whole message. */
        private void receive(WebSocket ws, byte[] chunk, boolean last) {
            buffer.writeBytes(chunk);
            if (last) {
                String message = buffer.toString(StandardCharsets.UTF_8);
                buffer.reset();
                lastMessageAt = Instant.now();
                process(message);
            }
            ws.request(1);  // ask for the next frame only now, so messages are handled strictly one at a time
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.info("AISStream closed the connection: {} {}", statusCode, reason);
            lost(ws);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("AISStream connection error: {}", error.toString());
            lost(ws);
        }
    }
}
