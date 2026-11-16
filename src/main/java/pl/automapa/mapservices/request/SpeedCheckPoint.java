package pl.automapa.mapservices.request;

import java.util.LinkedHashMap;
import java.util.Map;
import pl.automapa.mapservices.json.JsonValue;

/**
 * A point of {@code Road.speedCheckMulti}, sent as the JSON object {@code {"x": ..., "y": ...}} with {@code "dir"}
 * when the direction is set; {@code x} is the longitude and {@code y} the latitude.
 *
 * <pre>{@code
 * SpeedCheckPoint heading = new SpeedCheckPoint(15.2154636, 52.3118895, 350);
 * SpeedCheckPoint anyDirection = new SpeedCheckPoint(13.8412285, 52.3111549);
 * }</pre>
 *
 * @param x longitude in degrees
 * @param y latitude in degrees
 * @param dir direction of travel in degrees (0-359, -1 = not set), or {@code null} to leave it out
 */
public record SpeedCheckPoint(double x, double y, Integer dir) implements JsonValue {

    private static final String OPERATION = "SpeedCheckPoint";
    private static final String EXAMPLE = "new SpeedCheckPoint(15.2154636, 52.3118895, 350)";

    /**
     * Creates the point.
     *
     * @param x longitude in degrees
     * @param y latitude in degrees
     * @param dir direction of travel in degrees (0-359, -1 = not set), or {@code null} to leave it out
     * @throws IllegalArgumentException when a coordinate is NaN or infinite
     */
    public SpeedCheckPoint {
        Checks.requireFinite(x, OPERATION, "x (longitude)", EXAMPLE);
        Checks.requireFinite(y, OPERATION, "y (latitude)", EXAMPLE);
    }

    /**
     * Creates a point without a direction of travel.
     *
     * @param x longitude in degrees
     * @param y latitude in degrees
     * @throws IllegalArgumentException when a coordinate is NaN or infinite
     */
    public SpeedCheckPoint(double x, double y) {
        this(x, y, null);
    }

    /**
     * Returns the point as the API expects it.
     *
     * @return a new map {@code {"x": x, "y": y}} with {@code "dir"} when it is not {@code null}, in this key order
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", x);
        map.put("y", y);
        if (dir != null) {
            map.put("dir", dir);
        }
        return map;
    }

    /**
     * Returns {@link #toMap()}, so a point inside {@code extra} or a nested map is written as a JSON object.
     *
     * @return the map {@code {"x": x, "y": y}} with {@code "dir"} when set
     */
    @Override
    public Object toJsonValue() {
        return toMap();
    }
}
