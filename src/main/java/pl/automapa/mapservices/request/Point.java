package pl.automapa.mapservices.request;

import java.util.List;
import pl.automapa.mapservices.json.JsonValue;

/**
 * A point in the API order: {@code x} is the longitude and {@code y} the latitude (the reverse of Google), sent as
 * the JSON array {@code [x, y]}.
 *
 * <pre>{@code
 * Point warsaw = new Point(21.0073642, 52.18288); // Domaniewska 37, Warszawa
 * }</pre>
 *
 * @param x longitude in degrees
 * @param y latitude in degrees
 */
public record Point(double x, double y) implements JsonValue {

    private static final String EXAMPLE = "pass degrees as numbers, e.g. new Point(21.0073642, 52.18288)";

    /**
     * Creates the point.
     *
     * @param x longitude in degrees
     * @param y latitude in degrees
     * @throws IllegalArgumentException when a coordinate is NaN or infinite
     */
    public Point {
        requireFinite(x, "x (longitude)");
        requireFinite(y, "y (latitude)");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    "Point: " + name + " must be a finite number but was " + value + "; " + EXAMPLE);
        }
    }

    /**
     * Returns the coordinates as the API expects them.
     *
     * @return the immutable list {@code [x, y]}
     */
    public List<Double> toList() {
        return List.of(x, y);
    }

    /**
     * Returns {@link #toList()}, so a point inside {@code extra} or a nested map is written as {@code [x, y]}.
     *
     * @return the list {@code [x, y]}
     */
    @Override
    public Object toJsonValue() {
        return toList();
    }
}
