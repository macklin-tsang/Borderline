package com.borderline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Calls the real endpoints against a real PostGIS database, with visits we place at known times. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApiControllerIT {

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcClient jdbc;

    MockMvc mvc;

    @BeforeEach
    void emptyTables() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        jdbc.sql("TRUNCATE anchorage_visits, vessels").update();
    }

    @Test
    void anchorages_listsEveryAnchoragePositionedAndSized() throws Exception {
        mvc.perform(get("/api/anchorages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(32))
                .andExpect(jsonPath("$[0].id").value("E01"))
                .andExpect(near("$[0].lat", 49.299167))
                .andExpect(near("$[0].lon", -123.238611))
                .andExpect(jsonPath("$[0].radiusM").value(400));
    }

    @Test
    void vessels_returnsRecentShips_withTheirAnchorageWhenAtAnchor() throws Exception {
        insertVessel(316000001L, "ANCHORED ONE", Duration.ofMinutes(1));
        insertVessel(316000002L, "SAILING ONE", Duration.ofMinutes(1));
        insertVessel(316000003L, "LONG GONE", Duration.ofMinutes(40));  // not heard from for over 30 minutes
        insertOpenVisit(316000001L, "E01", Duration.ofHours(2), true);

        mvc.perform(get("/api/vessels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].mmsi").value(316000001))
                .andExpect(jsonPath("$[0].name").value("ANCHORED ONE"))
                .andExpect(jsonPath("$[0].anchorageId").value("E01"))
                .andExpect(jsonPath("$[0].arrivalSeen").value(true))
                .andExpect(jsonPath("$[0].anchoredSince").isNotEmpty())
                .andExpect(jsonPath("$[1].mmsi").value(316000002))
                .andExpect(jsonPath("$[1].anchorageId").value(nullValue()));
    }

    @Test
    void stats_medianAndAverage_ofKnownVisits() throws Exception {
        insertClosedVisit(1L, "E01", Duration.ofDays(2), Duration.ofHours(1), true);
        insertClosedVisit(2L, "E01", Duration.ofDays(3), Duration.ofHours(2), true);
        insertClosedVisit(3L, "E01", Duration.ofDays(4), Duration.ofHours(6), true);
        insertClosedVisit(4L, "E01", Duration.ofDays(5), Duration.ofHours(100), false);  // start not seen: ignored
        insertClosedVisit(5L, "E01", Duration.ofDays(40), Duration.ofHours(10), true);   // outside the 30-day window

        // Dwell times 1 h, 2 h, 6 h: the median is 2 (the middle one), the average is 3
        mvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(32))
                .andExpect(jsonPath("$[0].id").value("E01"))
                .andExpect(jsonPath("$[0].completedVisits").value(3))
                .andExpect(jsonPath("$[0].medianDwellHours").value(2.0))
                .andExpect(jsonPath("$[0].avgDwellHours").value(3.0));

        // A 60-day window brings the 40-day-old visit in: 1, 2, 6, 10 -> median 4, average 4.75 (shown as 4.8)
        mvc.perform(get("/api/stats").param("days", "60"))
                .andExpect(jsonPath("$[0].completedVisits").value(4))
                .andExpect(jsonPath("$[0].medianDwellHours").value(4.0))
                .andExpect(jsonPath("$[0].avgDwellHours").value(4.8));
    }

    @Test
    void stats_countsOpenVisitsAsWaitingNow_andAnEmptyAnchorageIsZero() throws Exception {
        insertOpenVisit(1L, "E01", Duration.ofHours(50), true);
        insertOpenVisit(2L, "E01", Duration.ofHours(5), false);  // already anchored when we started: still waiting

        mvc.perform(get("/api/stats"))
                .andExpect(jsonPath("$[0].waitingNow").value(2))
                .andExpect(jsonPath("$[0].longestWaitHours").value(closeTo(50.0, 0.2)))
                .andExpect(jsonPath("$[0].completedVisits").value(0))
                // E02 has no visits at all: zero, not one (the LEFT JOIN trap), and no averages
                .andExpect(jsonPath("$[1].id").value("E02"))
                .andExpect(jsonPath("$[1].waitingNow").value(0))
                .andExpect(jsonPath("$[1].longestWaitHours").value(nullValue()))
                .andExpect(jsonPath("$[1].medianDwellHours").value(nullValue()));
    }

    @Test
    void stats_explainsAnOutOfRangeDays() throws Exception {
        mvc.perform(get("/api/stats").param("days", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("between 1 and 365")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "366", "abc"})
    void stats_rejectsBadDays_withAProblemJsonAnswerAndNoStackTrace(String days) throws Exception {
        mvc.perform(get("/api/stats").param("days", days))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at com."))));
    }

    // ---- helpers ----

    /** The number at a JSON path is within a millionth of expected. (JsonPath may return a BigDecimal or a Double.) */
    private static ResultMatcher near(String path, double expected) {
        return result -> {
            Number actual = JsonPath.read(result.getResponse().getContentAsString(), path);
            assertThat(actual.doubleValue()).isCloseTo(expected, within(1e-6));
        };
    }

    private void insertVessel(long mmsi, String name, Duration lastHeardAgo) {
        jdbc.sql("""
                        INSERT INTO vessels (mmsi, name, position, sog_knots, updated_at)
                        VALUES (:mmsi, :name, ST_SetSRID(ST_MakePoint(-123.238611, 49.299167), 4326)::geography, 0.1, :at)
                        """)
                .param("mmsi", mmsi)
                .param("name", name)
                .param("at", ago(lastHeardAgo))
                .update();
    }

    private void insertOpenVisit(long mmsi, String anchorage, Duration waited, boolean seenArriving) {
        jdbc.sql("""
                        INSERT INTO anchorage_visits (mmsi, anchorage_id, arrived_at, seen_arriving)
                        VALUES (:mmsi, :anchorage, :arrived, :seen)
                        """)
                .param("mmsi", mmsi)
                .param("anchorage", anchorage)
                .param("arrived", ago(waited))
                .param("seen", seenArriving)
                .update();
    }

    private void insertClosedVisit(long mmsi, String anchorage, Duration arrivedAgo, Duration dwell, boolean seenArriving) {
        OffsetDateTime arrived = ago(arrivedAgo);
        jdbc.sql("""
                        INSERT INTO anchorage_visits (mmsi, anchorage_id, arrived_at, departed_at, seen_arriving)
                        VALUES (:mmsi, :anchorage, :arrived, :departed, :seen)
                        """)
                .param("mmsi", mmsi)
                .param("anchorage", anchorage)
                .param("arrived", arrived)
                .param("departed", arrived.plus(dwell))
                .param("seen", seenArriving)
                .update();
    }

    private static OffsetDateTime ago(Duration d) {
        return OffsetDateTime.now(ZoneOffset.UTC).minus(d);
    }
}
