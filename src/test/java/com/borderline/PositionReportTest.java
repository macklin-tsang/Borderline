package com.borderline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PositionReportTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A real message captured from AISStream (public broadcast data). */
    private static final String REAL = readFixture();

    @Test
    void parse_readsARealMessage() {
        PositionReport r = PositionReport.parse(REAL).orElseThrow();

        assertThat(r.mmsi()).isEqualTo(316005998L);
        assertThat(r.name()).isEqualTo("OSPREY 1");  // the padding is trimmed
        assertThat(r.lat()).isCloseTo(49.29136666666667, within(1e-9));
        assertThat(r.lon()).isCloseTo(-123.058365, within(1e-9));
        assertThat(r.sogKnots()).isZero();
    }

    @Test
    void parse_speed1023MeansNotAvailable() {
        assertThat(PositionReport.parse(with("Sog", 102.3)).orElseThrow().sogKnots()).isNull();
    }

    @Test
    void parse_latitude91MeansNoGpsFix() {
        assertThat(PositionReport.parse(with("Latitude", 91))).isEmpty();
    }

    @Test
    void parse_longitude181MeansNoGpsFix() {
        assertThat(PositionReport.parse(with("Longitude", 181))).isEmpty();
    }

    @Test
    void parse_ignoresOtherMessageTypes() {
        String confirmation = "{\"Message\":{\"CompressionEnabled\":true},\"MessageType\":\"SubscriptionConfirmation\"}";
        assertThat(PositionReport.parse(confirmation)).isEmpty();
    }

    @Test
    void parse_throwsWhenNotJson() {
        assertThatThrownBy(() -> PositionReport.parse("not json")).isInstanceOf(RuntimeException.class);
    }

    /** The real message with one field of its position report changed. */
    private static String with(String field, double value) {
        ObjectNode root = (ObjectNode) JSON.readTree(REAL);
        ((ObjectNode) root.at("/Message/PositionReport")).put(field, value);
        return JSON.writeValueAsString(root);
    }

    private static String readFixture() {
        try (var in = PositionReportTest.class.getResourceAsStream("/position-report.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
