package com.borderline;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Runs the tracker against a real PostGIS database with the real anchorages from V2. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AnchorageTrackerIT {

    // E01's centre (49 17 57 N, 123 14 19 W): reach is 400 m radius + 100 m margin
    private static final double E01_LAT = 49.299167, E01_LON = -123.238611;
    // 490 m from E01 and 464 m from E03: E03 is nearer, but E01 still covers it
    private static final double BETWEEN_LAT = 49.300166, BETWEEN_LON = -123.232047;
    // Stanley Park, nowhere near an anchorage
    private static final double OUTSIDE_LAT = 49.3017, OUTSIDE_LON = -123.1417;

    private static final Instant T0 = Instant.parse("2026-10-04T12:00:00Z");
    private static final long SHIP = 316000001L;
    private static final long OTHER_SHIP = 316000002L;

    @Autowired
    JdbcClient jdbc;

    AnchorageTracker tracker;

    @BeforeEach
    void freshTrackerAndEmptyTables() {
        jdbc.sql("TRUNCATE anchorage_visits, vessels").update();
        tracker = new AnchorageTracker(jdbc);  // fresh, so no feed state leaks between tests
    }

    @Test
    void stoppedOutsideAnchorage_noVisit() {
        report(SHIP, OUTSIDE_LAT, OUTSIDE_LON, 0.0, T0);

        assertThat(visits(SHIP)).isEmpty();
        assertThat(jdbc.sql("SELECT count(*) FROM vessels").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void stoppedInAnchorage_opensVisit() {
        report(SHIP, OUTSIDE_LAT, OUTSIDE_LON, 8.0, T0);                    // we watch it sail in
        report(SHIP, E01_LAT, E01_LON, 0.2, T0.plusSeconds(60));

        List<Visit> visits = visits(SHIP);
        assertThat(visits).hasSize(1);
        assertThat(visits.get(0).anchorage()).isEqualTo("E01");
        assertThat(visits.get(0).arrived().toInstant()).isEqualTo(T0.plusSeconds(60));
        assertThat(visits.get(0).departed()).isNull();
        assertThat(visits.get(0).seenArriving()).isTrue();
    }

    @Test
    void movingThroughAnchorage_noVisit() {
        report(SHIP, E01_LAT, E01_LON, 3.0, T0);

        assertThat(visits(SHIP)).isEmpty();
    }

    @Test
    void speedBetweenThresholds_neverOpensAVisit() {
        report(SHIP, E01_LAT, E01_LON, 1.0, T0);

        assertThat(visits(SHIP)).isEmpty();
    }

    @Test
    void speedBetweenThresholds_keepsAnOpenVisitOpen() {
        report(SHIP, E01_LAT, E01_LON, 0.2, T0);
        report(SHIP, E01_LAT, E01_LON, 1.0, T0.plus(Duration.ofHours(1)));  // swinging at anchor

        List<Visit> visits = visits(SHIP);
        assertThat(visits).hasSize(1);
        assertThat(visits.get(0).departed()).isNull();
    }

    @Test
    void speedingUp_closesVisitWithTheRightDuration() {
        report(SHIP, E01_LAT, E01_LON, 0.2, T0);
        report(SHIP, E01_LAT, E01_LON, 3.0, T0.plus(Duration.ofHours(2)));

        Visit visit = visits(SHIP).get(0);
        assertThat(Duration.between(visit.arrived().toInstant(), visit.departed().toInstant()))
                .isEqualTo(Duration.ofHours(2));
    }

    @Test
    void leavingTheAnchorage_closesVisitEvenWhenStillSlow() {
        report(SHIP, E01_LAT, E01_LON, 0.2, T0);
        report(SHIP, OUTSIDE_LAT, OUTSIDE_LON, 0.3, T0.plus(Duration.ofHours(1)));

        List<Visit> visits = visits(SHIP);
        assertThat(visits).hasSize(1);
        assertThat(visits.get(0).departed()).isNotNull();
    }

    @Test
    void nearestAnchorageChanges_butShipIsStillInTheOne_visitStaysOpen() {
        report(SHIP, E01_LAT, E01_LON, 0.1, T0);
        report(SHIP, BETWEEN_LAT, BETWEEN_LON, 0.1, T0.plusSeconds(180));

        List<Visit> visits = visits(SHIP);
        assertThat(visits).hasSize(1);
        assertThat(visits.get(0).anchorage()).isEqualTo("E01");
        assertThat(visits.get(0).departed()).isNull();
    }

    @Test
    void unknownSpeed_changesNothing() {
        report(SHIP, E01_LAT, E01_LON, 0.2, T0);
        report(SHIP, OUTSIDE_LAT, OUTSIDE_LON, null, T0.plusSeconds(180));  // would close it if speed were known
        report(OTHER_SHIP, E01_LAT, E01_LON, null, T0);                     // would open one if speed were known

        assertThat(visits(SHIP).get(0).departed()).isNull();
        assertThat(visits(OTHER_SHIP)).isEmpty();
    }

    @Test
    void firstSeenAlreadyAnchored_isNotCountedAsSeenArriving() {
        report(SHIP, E01_LAT, E01_LON, 0.0, T0);

        assertThat(visits(SHIP).get(0).seenArriving()).isFalse();
    }

    @Test
    void backAfterALongGap_isNotCountedAsSeenArriving() {
        report(SHIP, OUTSIDE_LAT, OUTSIDE_LON, 8.0, T0);
        report(SHIP, E01_LAT, E01_LON, 0.0, T0.plus(Duration.ofMinutes(31)));  // we were not watching in between

        assertThat(visits(SHIP).get(0).seenArriving()).isFalse();
    }

    @Test
    void silentShip_visitClosedAtLastSeenTime_whenFeedIsHealthy() {
        report(SHIP, E01_LAT, E01_LON, 0.0, T0);
        Instant now = T0.plus(Duration.ofHours(7));
        reportOtherShipEvery4Minutes(now.minus(Duration.ofMinutes(20)), now);

        assertThat(tracker.closeStaleVisits(now)).isEqualTo(1);
        assertThat(visits(SHIP).get(0).departed().toInstant()).isEqualTo(T0);
    }

    @Test
    void silentShip_visitKept_whenFeedJustStarted() {
        report(SHIP, E01_LAT, E01_LON, 0.0, T0);
        Instant now = T0.plus(Duration.ofHours(7));
        reportOtherShipEvery4Minutes(now.minus(Duration.ofMinutes(4)), now);  // only 4 minutes of feed

        assertThat(tracker.closeStaleVisits(now)).isZero();
        assertThat(visits(SHIP).get(0).departed()).isNull();
    }

    @Test
    void silentShip_visitKept_whenTheFeedWentQuiet() {
        report(SHIP, E01_LAT, E01_LON, 0.0, T0);
        Instant now = T0.plus(Duration.ofHours(7));
        reportOtherShipEvery4Minutes(now.minus(Duration.ofMinutes(20)), now);

        // e.g. the laptop slept: no position for 10 minutes
        assertThat(tracker.closeStaleVisits(now.plus(Duration.ofMinutes(10)))).isZero();
    }

    @Test
    void savingAVessel_keepsTheOldNameWhenTheNewReportHasNone_andAllowsNullSpeed() {
        tracker.handle(new PositionReport(SHIP, "OSPREY 1", OUTSIDE_LAT, OUTSIDE_LON, 5.0), T0);
        tracker.handle(new PositionReport(SHIP, null, OUTSIDE_LAT, OUTSIDE_LON, null), T0.plusSeconds(10));

        assertThat(jdbc.sql("SELECT name FROM vessels WHERE mmsi = :m").param("m", SHIP)
                .query(String.class).single()).isEqualTo("OSPREY 1");
        assertThat(jdbc.sql("SELECT sog_knots IS NULL FROM vessels WHERE mmsi = :m").param("m", SHIP)
                .query(Boolean.class).single()).isTrue();
    }

    // ---- helpers ----

    private void report(long mmsi, double lat, double lon, Double knots, Instant at) {
        tracker.handle(new PositionReport(mmsi, "SHIP " + mmsi, lat, lon, knots), at);
    }

    /** A second ship sailing around outside the anchorages, so the tracker sees a healthy feed. */
    private void reportOtherShipEvery4Minutes(Instant from, Instant to) {
        for (Instant t = from; !t.isAfter(to); t = t.plus(Duration.ofMinutes(4))) {
            report(OTHER_SHIP, OUTSIDE_LAT, OUTSIDE_LON, 5.0, t);
        }
    }

    private List<Visit> visits(long mmsi) {
        return jdbc.sql("""
                        SELECT anchorage_id, arrived_at, departed_at, seen_arriving
                        FROM anchorage_visits WHERE mmsi = :mmsi ORDER BY arrived_at
                        """)
                .param("mmsi", mmsi)
                .query((rs, row) -> new Visit(
                        rs.getString("anchorage_id"),
                        rs.getObject("arrived_at", OffsetDateTime.class),
                        rs.getObject("departed_at", OffsetDateTime.class),
                        rs.getBoolean("seen_arriving")))
                .list();
    }

    private record Visit(String anchorage, OffsetDateTime arrived, OffsetDateTime departed, boolean seenArriving) {}
}
