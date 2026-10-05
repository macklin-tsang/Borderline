package com.borderline;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the stream of ship positions into anchorage visits: when a ship settles at an
 * anchorage, when it leaves, and therefore how long it waited.
 */
@Component
public class AnchorageTracker {

    private static final Logger log = LoggerFactory.getLogger(AnchorageTracker.class);

    // A ship is "in" an anchorage when it is within the anchorage's radius plus this margin.
    // Measured on 22 ships at anchor: the farthest sat 62 m beyond its anchorage's radius, so 100 m
    // left too little room and a drifting ship would split its wait into two visits.
    private static final int ZONE_MARGIN_M = 150;

    // Arrive below 0.5 knots, depart above 2.0. Speeds in between change nothing, so a ship
    // swinging at anchor does not flip between arrived and departed.
    private static final double ARRIVE_BELOW_KNOTS = 0.5;
    private static final double DEPART_ABOVE_KNOTS = 2.0;

    // If we last heard from a ship longer ago than this, we were not watching it continuously,
    // so we cannot know when its wait began.
    private static final Duration SEEN_GAP = Duration.ofMinutes(30);

    // Close a visit when its ship has been silent this long ...
    private static final Duration STALE_AFTER = Duration.ofHours(6);
    // ... but only while our own feed is healthy: positions arriving with no gap longer than
    // FEED_GAP, for at least FEED_WARMUP. Otherwise silence may be our outage, not the ship leaving.
    private static final Duration FEED_GAP = Duration.ofMinutes(5);
    private static final Duration FEED_WARMUP = Duration.ofMinutes(15);

    private final JdbcClient jdbc;

    // Written by the WebSocket thread, read by the scheduler thread.
    // ponytail: two separate volatiles, a cleanup tick can see them one update apart; harmless.
    private volatile Instant lastPositionAt;
    private volatile Instant feedHealthySince;

    public AnchorageTracker(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Called once per ship position; {@code at} is when we received it. */
    @Transactional
    public void handle(PositionReport r, Instant at) {
        noteFeedActivity(at);
        OffsetDateTime now = utc(at);

        // Must be read before saveVessel() overwrites updated_at
        boolean watchedRecently = jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM vessels WHERE mmsi = :mmsi AND updated_at > :since)")
                .param("mmsi", r.mmsi())
                .param("since", utc(at.minus(SEEN_GAP)))
                .query(Boolean.class).single();

        saveVessel(r, now);

        if (r.sogKnots() == null) {
            return;  // speed unknown: change nothing
        }
        double knots = r.sogKnots();

        List<String> inReach = anchoragesInReach(r);  // nearest first
        Optional<String> open = jdbc.sql(
                        "SELECT anchorage_id FROM anchorage_visits WHERE mmsi = :mmsi AND departed_at IS NULL")
                .param("mmsi", r.mmsi())
                .query(String.class).optional();

        // An open visit is judged against its own anchorage, not the nearest one, so a ship swinging
        // between two overlapping anchorages does not flip between visits.
        if (open.isPresent() && (knots > DEPART_ABOVE_KNOTS || !inReach.contains(open.get()))) {
            jdbc.sql("UPDATE anchorage_visits SET departed_at = :at WHERE mmsi = :mmsi AND departed_at IS NULL")
                    .param("at", now)
                    .param("mmsi", r.mmsi())
                    .update();
            log.info("Ship {} left anchorage {}", r.mmsi(), open.get());
            open = Optional.empty();
        }

        if (open.isEmpty() && knots < ARRIVE_BELOW_KNOTS && !inReach.isEmpty()) {
            jdbc.sql("""
                            INSERT INTO anchorage_visits (mmsi, anchorage_id, arrived_at, seen_arriving)
                            VALUES (:mmsi, :anchorage, :at, :seen)
                            """)
                    .param("mmsi", r.mmsi())
                    .param("anchorage", inReach.get(0))
                    .param("at", now)
                    .param("seen", watchedRecently)
                    .update();
            log.info("Ship {} arrived at anchorage {}", r.mmsi(), inReach.get(0));
        }
    }

    /** Every 10 minutes: close visits of ships that have been silent for hours. */
    @Scheduled(fixedDelay = 600_000, initialDelay = 600_000)
    public void closeStaleVisits() {
        closeStaleVisits(Instant.now());
    }

    /** Returns how many visits were closed. A stale visit ends at the time we last heard from the ship. */
    public int closeStaleVisits(Instant now) {
        Instant healthySince = feedHealthySince;
        Instant lastPosition = lastPositionAt;
        if (healthySince == null || lastPosition == null
                || Duration.between(healthySince, now).compareTo(FEED_WARMUP) < 0
                || Duration.between(lastPosition, now).compareTo(FEED_GAP) > 0) {
            return 0;  // our feed is not healthy, so a ship's silence proves nothing
        }
        int closed = jdbc.sql("""
                        UPDATE anchorage_visits v SET departed_at = s.updated_at
                        FROM vessels s
                        WHERE s.mmsi = v.mmsi AND v.departed_at IS NULL AND s.updated_at < :cutoff
                        """)
                .param("cutoff", utc(now.minus(STALE_AFTER)))
                .update();
        if (closed > 0) {
            log.info("Closed {} visits of ships silent for {} hours", closed, STALE_AFTER.toHours());
        }
        return closed;
    }

    /** The feed counts as healthy until two positions arrive more than FEED_GAP apart. */
    private void noteFeedActivity(Instant at) {
        if (lastPositionAt == null || Duration.between(lastPositionAt, at).compareTo(FEED_GAP) > 0) {
            feedHealthySince = at;
        }
        lastPositionAt = at;
    }

    private void saveVessel(PositionReport r, OffsetDateTime now) {
        jdbc.sql("""
                        INSERT INTO vessels (mmsi, name, position, sog_knots, updated_at)
                        VALUES (:mmsi, :name, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :sog, :at)
                        ON CONFLICT (mmsi) DO UPDATE SET
                            name       = COALESCE(EXCLUDED.name, vessels.name),
                            position   = EXCLUDED.position,
                            sog_knots  = EXCLUDED.sog_knots,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("mmsi", r.mmsi())
                .param("name", r.name())
                .param("lon", r.lon())
                .param("lat", r.lat())
                .param("sog", r.sogKnots())
                .param("at", now)
                .update();
    }

    /** Ids of the anchorages whose zone contains the ship, nearest first. */
    private List<String> anchoragesInReach(PositionReport r) {
        return jdbc.sql("""
                        SELECT id FROM anchorages
                        WHERE ST_DWithin(center, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography,
                                         radius_m + :margin)
                        ORDER BY ST_Distance(center, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography)
                        """)
                .param("lon", r.lon())
                .param("lat", r.lat())
                .param("margin", ZONE_MARGIN_M)
                .query(String.class).list();
    }

    private static OffsetDateTime utc(Instant t) {
        return t.atOffset(ZoneOffset.UTC);
    }
}
