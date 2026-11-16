package pl.automapa.mapservices.endpoint;

import java.util.List;
import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.request.GeocodeRequest;
import pl.automapa.mapservices.request.RevGeocodeMultiRequest;
import pl.automapa.mapservices.request.RevGeocodeRequest;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * The {@code Geocoder} API service: addresses to coordinates and back.
 *
 * <pre>{@code
 * ApiResponse found = client.geocoder().geocode(
 *         GeocodeRequest.builder("Warszawa").street("Domaniewska").house("37").maxResults(1).build());
 * ApiResponse address = client.geocoder().revgeocode(
 *         RevGeocodeRequest.builder(new Point(21.0073642, 52.18288)).snapToBld(true).build());
 * }</pre>
 *
 * <p>Every method requires a session, which the client opens when needed. {@code format("google")} on a request
 * returns the result in the Google Geocoding API structure; {@link ApiResponse#raw()} keeps the native response.
 */
public final class Geocoder {

    private static final String SERVICE = "Geocoder";

    private final ApiClient client;

    /**
     * Creates the endpoint; usually obtained from {@link ApiClient#geocoder()}.
     *
     * @param client client sending the requests
     * @throws IllegalArgumentException when {@code client} is {@code null}
     */
    public Geocoder(ApiClient client) {
        this.client = EndpointParams.requireNonNull(client, "new Geocoder", "client", "use client.geocoder()");
    }

    /**
     * Geocodes one structured address.
     *
     * @param request the address, e.g. {@code GeocodeRequest.builder("Warszawa").street("Domaniewska").build()}
     * @return the response; the result is the list of matches with {@code x} (longitude), {@code y} (latitude),
     *     {@code quality} and the address fields
     * @throws IllegalArgumentException when {@code request} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse geocode(GeocodeRequest request) {
        EndpointParams.requireNonNull(
                request, "Geocoder.geocode", "request", "build one with GeocodeRequest.builder(city)...build()");
        return client.call(SERVICE, "geocode", request.toParams());
    }

    /**
     * Geocodes many addresses in one call (the API accepts up to 100).
     *
     * @param addresses address objects with the keys of the API, e.g.
     *     {@code List.of(Map.of("city", "Warszawa", "street", "Domaniewska", "house", "37"))}
     * @return the response; the result has one item per address: a list of matches or an error object
     * @throws IllegalArgumentException when {@code addresses} is {@code null}, empty or contains {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse geocodemulti(List<? extends Map<String, ?>> addresses) {
        return geocodemulti(addresses, null);
    }

    /**
     * Geocodes many addresses with additional parameters.
     *
     * @param addresses address objects with the keys of the API; override {@code addresses} in {@code extra}
     * @param extra additional parameters sent unchanged, or {@code null} for none; the {@code format} key selects
     *     the response format and is not sent
     * @return the response; the result has one item per address: a list of matches or an error object
     * @throws IllegalArgumentException when {@code addresses} is {@code null}, empty or contains {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse geocodemulti(List<? extends Map<String, ?>> addresses, Map<String, ?> extra) {
        List<Map<String, ?>> copy = EndpointParams.requireItems(
                addresses,
                "Geocoder.geocodemulti",
                "addresses",
                "pass address maps, e.g. List.of(Map.of(\"city\", \"Warszawa\", \"street\", \"Domaniewska\"))");
        return client.call(SERVICE, "geocodemulti", EndpointParams.merge(extra, "addresses", copy));
    }

    /**
     * Reverse-geocodes one point to an address; the {@code params} object is always sent, also empty.
     *
     * @param request the point and options, e.g. {@code RevGeocodeRequest.builder(new Point(21.0, 52.2)).build()}
     * @return the response; the result is the address object ({@code city}, {@code street}, {@code house}, ...)
     * @throws IllegalArgumentException when {@code request} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse revgeocode(RevGeocodeRequest request) {
        EndpointParams.requireNonNull(
                request,
                "Geocoder.revgeocode",
                "request",
                "build one with RevGeocodeRequest.builder(new Point(x, y))...build()");
        return client.call(SERVICE, "revgeocode", request.toParams());
    }

    /**
     * Reverse-geocodes many points in one call; the {@code params} object is always sent, also empty.
     *
     * @param request the points and options, e.g. {@code RevGeocodeMultiRequest.builder(List.of(point)).build()}
     * @return the response; the result has one address object per point
     * @throws IllegalArgumentException when {@code request} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse revgeocodemulti(RevGeocodeMultiRequest request) {
        EndpointParams.requireNonNull(
                request,
                "Geocoder.revgeocodemulti",
                "request",
                "build one with RevGeocodeMultiRequest.builder(List.of(new Point(x, y)))...build()");
        return client.call(SERVICE, "revgeocodemulti", request.toParams());
    }
}
