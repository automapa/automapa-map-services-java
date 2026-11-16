package pl.automapa.mapservices.request;

import java.util.LinkedHashMap;
import java.util.Map;
import pl.automapa.mapservices.json.JsonValue;

/**
 * A point of {@code Routes.optimizeQueue} with its own identifier, sent as the JSON object
 * {@code {"id": ..., "x": ..., "y": ...}}; {@code x} is the longitude and {@code y} the latitude.
 *
 * <pre>{@code
 * QueuePoint warsaw = new QueuePoint("Warszawa", 21.0073642, 52.2297);
 * }</pre>
 *
 * <p>The API returns the optimised order as a list of these identifiers, so they must be unique within a request.
 *
 * @param id identifier of the point, returned in {@code optimizedSequence}
 * @param x longitude in degrees
 * @param y latitude in degrees
 */
public record QueuePoint(String id, double x, double y) implements JsonValue {

    private static final String EXAMPLE = "e.g. new QueuePoint(\"Warszawa\", 21.0073642, 52.2297)";

    /**
     * Creates the point.
     *
     * @param id identifier of the point, returned in {@code optimizedSequence}
     * @param x longitude in degrees
     * @param y latitude in degrees
     * @throws IllegalArgumentException when {@code id} is {@code null} or blank, or a coordinate is NaN or infinite
     */
    public QueuePoint {
        Checks.requireText(id, "QueuePoint", "id", "give every point a unique identifier, " + EXAMPLE);
        requireFinite(x, "x (longitude)");
        requireFinite(y, "y (latitude)");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("QueuePoint: " + name + " must be a finite number but was " + value
                    + "; pass degrees as numbers, " + EXAMPLE);
        }
    }

    /**
     * Returns the point as the API expects it.
     *
     * @return a new map {@code {"id": id, "x": x, "y": y}} in this key order
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("x", x);
        map.put("y", y);
        return map;
    }

    /**
     * Returns {@link #toMap()}, so a point inside {@code extra} or a nested map is written as a JSON object.
     *
     * @return the map {@code {"id": id, "x": x, "y": y}}
     */
    @Override
    public Object toJsonValue() {
        return toMap();
    }
}
