package com.borderline;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class AnchorageTracker {

    private final JdbcClient jdbc;

    public AnchorageTracker(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Called once per ship position. For now it only remembers the latest position of each ship. */
    public void handle(PositionReport r) {
        jdbc.sql("""
                INSERT INTO vessels (mmsi, name, position, sog_knots, updated_at)
                VALUES (:mmsi, :name, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :sog, now())
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
                .update();
    }
}
