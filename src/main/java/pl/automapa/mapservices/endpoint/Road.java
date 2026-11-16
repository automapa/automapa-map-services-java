package pl.automapa.mapservices.endpoint;

import java.util.List;
import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.request.SegmentInfoRequest;
import pl.automapa.mapservices.request.SpeedCheckPoint;
import pl.automapa.mapservices.request.SpeedCheckRequest;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * The {@code Road} API service: the road segment nearest to a point and the speed limits for cars and trucks.
 *
 * <pre>{@code
 * ApiResponse response = client.road().speedCheck(
 *         SpeedCheckRequest.builder(15.2154636, 52.3118895).dir(350).build());
 * long carLimit = ((Number) response.dataAsMap().get("speedLimit")).longValue();
 * }</pre>
 *
 * <p>Every method requires a session, which the client opens when needed. {@code x} is the longitude and {@code y}
 * the latitude.
 */
public final class Road {

    private static final String SERVICE = "Road";

    private final ApiClient client;

    /**
     * Creates the endpoint; usually obtained from {@link ApiClient#road()}.
     *
     * @param client client sending the requests
     * @throws IllegalArgumentException when {@code client} is {@code null}
     */
    public Road(ApiClient client) {
        this.client = EndpointParams.requireNonNull(client, "new Road", "client", "use client.road()");
    }

    /**
     * Returns the road segment nearest to a point.
     *
     * @param request the point and options, e.g. {@code SegmentInfoRequest.builder(21.00736, 52.18288).build()}
     * @return the response; the result object has {@code segment} (with {@code id}, {@code speedLimit},
     *     {@code streetName}, {@code placeName}, {@code category}, {@code isToll}, {@code isHighway} and more),
     *     {@code attractedX}, {@code attractedY} (the snapped point) and {@code pureDistance} (metres)
     * @throws IllegalArgumentException when {@code request} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse getSegmentInfo(SegmentInfoRequest request) {
        EndpointParams.requireNonNull(
                request,
                "Road.getSegmentInfo",
                "request",
                "build one with SegmentInfoRequest.builder(x, y).build(), e.g. builder(21.00736, 52.18288)");
        return client.call(SERVICE, "getSegmentInfo", request.toParams());
    }

    /**
     * Returns the speed limits at one point.
     *
     * @param request the point and options, e.g. {@code SpeedCheckRequest.builder(15.2154636, 52.3118895).build()}
     * @return the response; the result object has {@code speedLimit} and {@code speedLimitTruck} (km/h, {@code -1}
     *     = no data, {@code 0} = no limit), {@code urbanArea}, {@code x}, {@code y} (the snapped point) and
     *     {@code distance} (metres)
     * @throws IllegalArgumentException when {@code request} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse speedCheck(SpeedCheckRequest request) {
        EndpointParams.requireNonNull(
                request,
                "Road.speedCheck",
                "request",
                "build one with SpeedCheckRequest.builder(x, y).build(), e.g. builder(15.2154636, 52.3118895)");
        return client.call(SERVICE, "speedCheck", request.toParams());
    }

    /**
     * Returns the speed limits at many points; the API accepts up to 100 points, the SDK does not check the limit.
     *
     * @param points the points, e.g. {@code List.of(new SpeedCheckPoint(15.2154636, 52.3118895, 350))}
     * @return the response; the result is a list with one object per point, in the order of {@code points}, with
     *     the keys of {@link #speedCheck(SpeedCheckRequest)}
     * @throws IllegalArgumentException when {@code points} is {@code null}, empty or contains {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse speedCheckMulti(List<SpeedCheckPoint> points) {
        return speedCheckMulti(points, null);
    }

    /**
     * Returns the speed limits at many points with additional parameters.
     *
     * @param points the points; override {@code points} in {@code extra}
     * @param extra additional parameters sent unchanged, or {@code null} for none; the {@code format} key selects
     *     the response format and is not sent
     * @return the response; the result is a list with one object per point, in the order of {@code points}
     * @throws IllegalArgumentException when {@code points} is {@code null}, empty or contains {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse speedCheckMulti(List<SpeedCheckPoint> points, Map<String, ?> extra) {
        List<SpeedCheckPoint> copy = EndpointParams.requireItems(
                points,
                "Road.speedCheckMulti",
                "points",
                "pass e.g. List.of(new SpeedCheckPoint(15.2154636, 52.3118895, 350))");
        return client.call(SERVICE, "speedCheckMulti", EndpointParams.merge(extra, "points", copy));
    }
}
