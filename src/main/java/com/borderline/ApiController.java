package com.borderline;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only JSON API for the map page. Nothing here changes any data. */
@RestController
@RequestMapping("/api")
public class ApiController {

    // Ships we heard from within this many minutes are shown on the map
    private static final int VESSEL_FRESH_MINUTES = 30;
    private static final int MAX_STATS_DAYS = 365;

    public record AnchorageInfo(String id, String name, String area, double lat, double lon, int radiusM) {}

    /** anchorageId, anchoredSince and arrivalSeen are null when the ship is not at anchor. */
    public record VesselInfo(long mmsi, String name, double lat, double lon, Double sogKnots,
                             OffsetDateTime updatedAt, String anchorageId, OffsetDateTime anchoredSince,
                             Boolean arrivalSeen) {}

    public record AnchorageStats(String id, String name, String area, long waitingNow, Double longestWaitHours,
                                 long completedVisits, Double avgDwellHours, Double medianDwellHours) {}

    private final JdbcClient jdbc;

    public ApiController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/anchorages")
    public List<AnchorageInfo> anchorages() {
        return jdbc.sql("""
                        SELECT id, name, area, ST_Y(center::geometry) AS lat, ST_X(center::geometry) AS lon, radius_m
                        FROM anchorages
                        ORDER BY id
                        """)
                .query(AnchorageInfo.class).list();
    }

    @GetMapping("/vessels")
    public List<VesselInfo> vessels() {
        // The partial unique index on open visits means at most one visit joins per ship,
        // so this LEFT JOIN can never duplicate a ship.
        return jdbc.sql("""
                        SELECT v.mmsi, v.name,
                               ST_Y(v.position::geometry) AS lat, ST_X(v.position::geometry) AS lon,
                               v.sog_knots, v.updated_at,
                               visit.anchorage_id, visit.arrived_at AS anchored_since,
                               visit.seen_arriving AS arrival_seen
                        FROM vessels v
                        LEFT JOIN anchorage_visits visit ON visit.mmsi = v.mmsi AND visit.departed_at IS NULL
                        WHERE v.updated_at > now() - make_interval(mins => :minutes)
                        ORDER BY v.mmsi
                        """)
                .param("minutes", VESSEL_FRESH_MINUTES)
                .query(VesselInfo.class).list();
    }

    /**
     * One row per anchorage. "Waiting now" and "longest wait" use every open visit (for a ship that was
     * already anchored when we started watching, the wait is "at least this long"). The dwell figures
     * use only visits we saw begin and end inside the window, because only those have a trustworthy duration.
     */
    @GetMapping("/stats")
    public List<AnchorageStats> stats(@RequestParam(defaultValue = "30") int days) {
        if (days < 1 || days > MAX_STATS_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "days must be between 1 and " + MAX_STATS_DAYS);
        }
        // count(v.id), not count(*): an anchorage with no visits still produces one row of NULLs
        // from the LEFT JOIN, and count(*) would wrongly count it as a waiting ship.
        return jdbc.sql("""
                        SELECT a.id, a.name, a.area,
                               count(v.id) FILTER (WHERE v.departed_at IS NULL) AS waiting_now,
                               round((max(EXTRACT(EPOCH FROM now() - v.arrived_at))
                                      FILTER (WHERE v.departed_at IS NULL) / 3600)::numeric, 1)::double precision
                                   AS longest_wait_hours,
                               count(v.id) FILTER (WHERE v.departed_at IS NOT NULL) AS completed_visits,
                               round((avg(EXTRACT(EPOCH FROM v.departed_at - v.arrived_at)) / 3600)::numeric, 1)::double precision
                                   AS avg_dwell_hours,
                               round((percentile_cont(0.5) WITHIN GROUP
                                          (ORDER BY EXTRACT(EPOCH FROM v.departed_at - v.arrived_at)::double precision)
                                      / 3600)::numeric, 1)::double precision AS median_dwell_hours
                        FROM anchorages a
                        LEFT JOIN anchorage_visits v ON v.anchorage_id = a.id
                             AND (v.departed_at IS NULL
                                  OR (v.seen_arriving AND v.departed_at >= now() - make_interval(days => :days)))
                        GROUP BY a.id, a.name, a.area
                        ORDER BY a.id
                        """)
                .param("days", days)
                .query(AnchorageStats.class).list();
    }
}
