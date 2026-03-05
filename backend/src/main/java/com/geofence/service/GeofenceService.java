package com.geofence.service;

import com.geofence.dto.request.GeofenceRequest;
import com.geofence.dto.response.GeofenceResponse;
import com.geofence.exception.GeofenceNotFoundException;
import com.geofence.model.Geofence;
import com.geofence.repository.GeofenceRepository;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.IntStream;

@Service
@Transactional
public class GeofenceService {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    private final GeofenceRepository repo;

    public GeofenceService(GeofenceRepository repo) {
        this.repo = repo;
    }

    public GeofenceResponse create(UUID userId, GeofenceRequest req) {
        Geometry geom = toGeometry(req.geometry());
        Geofence geofence = new Geofence(userId, req.name(), geom, req.alertOnEntry(), req.alertOnExit());
        return toResponse(repo.save(geofence));
    }

    @Transactional(readOnly = true)
    public List<GeofenceResponse> list(UUID userId) {
        return repo.findByUserIdAndActiveTrue(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GeofenceResponse getOne(UUID userId, UUID id) {
        return repo.findByIdAndUserId(id, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new GeofenceNotFoundException(id));
    }

    public GeofenceResponse update(UUID userId, UUID id, GeofenceRequest req) {
        Geofence geofence = repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GeofenceNotFoundException(id));
        geofence.setName(req.name());
        geofence.setGeometry(toGeometry(req.geometry()));
        geofence.setAlertOnEntry(req.alertOnEntry());
        geofence.setAlertOnExit(req.alertOnExit());
        return toResponse(repo.save(geofence));
    }

    public void delete(UUID userId, UUID id) {
        Geofence geofence = repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GeofenceNotFoundException(id));
        geofence.setActive(false);
        repo.save(geofence);
    }

    // ── GeoJSON ↔ JTS conversion (manual — avoids jts-io-extra dependency) ──

    @SuppressWarnings("unchecked")
    private Geometry toGeometry(Map<String, Object> geo) {
        String type = (String) geo.get("type");
        return switch (type) {
            case "Point" -> {
                List<Number> c = (List<Number>) geo.get("coordinates");
                yield GF.createPoint(coord(c));
            }
            case "Polygon" -> buildPolygon((List<List<List<Number>>>) geo.get("coordinates"));
            case "MultiPolygon" -> {
                List<List<List<List<Number>>>> parts = (List<List<List<List<Number>>>>) geo.get("coordinates");
                Polygon[] polys = parts.stream().map(this::buildPolygon).toArray(Polygon[]::new);
                yield GF.createMultiPolygon(polys);
            }
            default -> throw new IllegalArgumentException("Unsupported geometry type: " + type);
        };
    }

    private Polygon buildPolygon(List<List<List<Number>>> rings) {
        LinearRing shell = GF.createLinearRing(ring(rings.get(0)));
        LinearRing[] holes = rings.subList(1, rings.size()).stream()
                .map(r -> GF.createLinearRing(ring(r)))
                .toArray(LinearRing[]::new);
        return GF.createPolygon(shell, holes);
    }

    private Coordinate[] ring(List<List<Number>> positions) {
        return positions.stream().map(this::coord).toArray(Coordinate[]::new);
    }

    private Coordinate coord(List<Number> pos) {
        return new Coordinate(pos.get(0).doubleValue(), pos.get(1).doubleValue());
    }

    private Map<String, Object> fromGeometry(Geometry geom) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (geom instanceof Point p) {
            out.put("type", "Point");
            out.put("coordinates", List.of(p.getX(), p.getY()));
        } else if (geom instanceof Polygon p) {
            out.put("type", "Polygon");
            out.put("coordinates", polygonCoords(p));
        } else if (geom instanceof MultiPolygon mp) {
            out.put("type", "MultiPolygon");
            out.put("coordinates", IntStream.range(0, mp.getNumGeometries())
                    .mapToObj(i -> polygonCoords((Polygon) mp.getGeometryN(i)))
                    .toList());
        } else {
            throw new IllegalStateException("Unsupported geometry type: " + geom.getGeometryType());
        }
        return out;
    }

    private List<List<List<Double>>> polygonCoords(Polygon p) {
        List<List<List<Double>>> rings = new ArrayList<>();
        rings.add(ringCoords(p.getExteriorRing()));
        IntStream.range(0, p.getNumInteriorRing())
                .forEach(i -> rings.add(ringCoords(p.getInteriorRingN(i))));
        return rings;
    }

    private List<List<Double>> ringCoords(LineString ring) {
        return Arrays.stream(ring.getCoordinates())
                .map(c -> List.of(c.getX(), c.getY()))
                .toList();
    }

    private GeofenceResponse toResponse(Geofence g) {
        return new GeofenceResponse(
                g.getId(),
                g.getName(),
                fromGeometry(g.getGeometry()),
                g.isActive(),
                g.isAlertOnEntry(),
                g.isAlertOnExit(),
                g.getCreatedAt()
        );
    }
}
