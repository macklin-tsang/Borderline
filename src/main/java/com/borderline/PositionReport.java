package com.borderline;

import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** One ship position from AISStream, with the "not available" placeholders already handled. */
public record PositionReport(long mmsi, String name, double lat, double lon, Double sogKnots) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * Reads a position out of one AISStream message. Returns empty if the message is not a
     * usable position (another message type, missing fields, or "not available" coordinates).
     * Throws if the text is not JSON at all.
     */
    public static Optional<PositionReport> parse(String json) {
        JsonNode root = JSON.readTree(json);
        if (!"PositionReport".equals(root.path("MessageType").asString())) {
            return Optional.empty();
        }
        JsonNode p = root.path("Message").path("PositionReport");
        if (!p.path("UserID").isNumber() || !p.path("Latitude").isNumber() || !p.path("Longitude").isNumber()) {
            return Optional.empty();
        }

        double lat = p.path("Latitude").asDouble();
        double lon = p.path("Longitude").asDouble();
        if (Math.abs(lat) > 90 || Math.abs(lon) > 180) {
            return Optional.empty();  // ships send 91 / 181 when they have no GPS fix
        }

        Double sog = p.path("Sog").isNumber() ? p.path("Sog").asDouble() : null;
        if (sog != null && sog >= 102.3) {
            sog = null;  // 102.3 knots means "speed not available"
        }

        // Names arrive padded with spaces ("OSPREY 1            ")
        String name = root.path("MetaData").path("ShipName").asString().trim();
        return Optional.of(new PositionReport(p.path("UserID").asLong(), name.isEmpty() ? null : name, lat, lon, sog));
    }
}
