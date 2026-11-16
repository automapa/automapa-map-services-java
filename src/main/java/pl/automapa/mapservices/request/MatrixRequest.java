package pl.automapa.mapservices.request;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request of {@code Routes.matrix}: route length and travel time between every pair of points.
 *
 * <pre>{@code
 * MatrixRequest request = MatrixRequest.builder(
 *                 List.of(
 *                         new Point(21.0073642, 52.2297), // Warszawa
 *                         new Point(18.6282, 54.352), // Gdansk
 *                         new Point(16.9252, 52.4064), // Poznan
 *                         new Point(19.9448, 50.0647)), // Krakow
 *                 MatrixType.TIME)
 *         .simple(true)
 *         .build();
 * // {"points": [[21.0073642, 52.2297], ...], "type": 1, "precise": false, "simple": true}
 * }</pre>
 *
 * <p>{@code precise} and {@code simple} are always sent ({@code false} unless set). The API needs at least 4
 * different points and answers HTTP 400 "At least 4 different points needed." otherwise; the SDK does not check
 * it and leaves the validation to the API.
 */
public final class MatrixRequest extends ApiRequest {

    private final List<Point> points;
    private final MatrixType type;
    private final boolean precise;
    private final boolean simple;

    private MatrixRequest(Builder builder) {
        super(builder);
        this.points = builder.points;
        this.type = builder.type;
        this.precise = builder.precise;
        this.simple = builder.simple;
    }

    /**
     * Starts a request for points.
     *
     * @param points the points ({@code x} = longitude, {@code y} = latitude), at least 4 different ones for the
     *     API; the list is copied
     * @param type what to measure: {@link MatrixType#TIME} or {@link MatrixType#DISTANCE}
     * @return the builder
     * @throws IllegalArgumentException when {@code points} is {@code null}, empty or contains {@code null}, or
     *     {@code type} is {@code null}
     */
    public static Builder builder(List<Point> points, MatrixType type) {
        List<Point> copy = Checks.requireItems(
                points,
                "MatrixRequest.builder",
                "points",
                "pass at least 4 different points, e.g. List.of(new Point(21.0073642, 52.2297), ...)");
        MatrixType checkedType = Checks.requireNonNull(
                type, "MatrixRequest.builder", "type", "pass MatrixType.TIME or MatrixType.DISTANCE");
        return new Builder(copy, checkedType);
    }

    @Override
    protected Map<String, Object> namedParams() {
        List<List<Double>> coordinates = new ArrayList<>(points.size());
        for (Point point : points) {
            coordinates.add(point.toList());
        }
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("points", coordinates);
        named.put("type", type.apiValue());
        named.put("precise", precise);
        named.put("simple", simple);
        return named;
    }

    /** Builder of {@link MatrixRequest}; {@code precise} and {@code simple} default to {@code false}. */
    public static final class Builder extends ApiRequest.Builder<Builder> {

        private final List<Point> points;
        private final MatrixType type;
        private boolean precise;
        private boolean simple;

        private Builder(List<Point> points, MatrixType type) {
            this.points = points;
            this.type = type;
        }

        /**
         * Uses the exact coordinates instead of allowing the API to round them (up to 500 m).
         *
         * @param precise {@code true} for exact coordinates; {@code false} (default) allows rounding
         * @return this builder
         */
        public Builder precise(boolean precise) {
            this.precise = precise;
            return this;
        }

        /**
         * Computes only the unique pairs of points instead of every point with every other.
         *
         * @param simple {@code true} for unique pairs; {@code false} (default) for all pairs
         * @return this builder
         */
        public Builder simple(boolean simple) {
            this.simple = simple;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        /**
         * Builds the request.
         *
         * @return the request
         */
        public MatrixRequest build() {
            return new MatrixRequest(this);
        }
    }
}
