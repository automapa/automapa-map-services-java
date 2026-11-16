package pl.automapa.mapservices.endpoint;

import java.util.LinkedHashMap;
import java.util.Map;
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.request.RoadPermitEnvironment;
import pl.automapa.mapservices.request.RoadPermitListRequest;
import pl.automapa.mapservices.request.RoadPermitRequest;
import pl.automapa.mapservices.response.ApiResponse;

/**
 * The {@code RoadPermit} API service: road permit documents (permitted vehicle limits inside an area) of one
 * environment.
 *
 * <pre>{@code
 * RoadPermit roadPermit = client.roadPermit(RoadPermitEnvironment.UAT);
 * List<Object> documents = roadPermit.list().dataAsList();
 * }</pre>
 *
 * <p>Every method requires a session, which the client opens when needed, and sends {@code environment} of this
 * endpoint; it overrides {@code environment} given in {@code extra}. Use {@link RoadPermitEnvironment#UAT} for
 * development and tests, {@link RoadPermitEnvironment#PROD} changes production documents.
 */
public final class RoadPermit {

    private static final String SERVICE = "RoadPermit";
    private static final String ENVIRONMENT_PARAM = "environment";
    private static final String ID_PARAM = "id";
    private static final String REQUEST_HINT =
            "build one with RoadPermitRequest.builder(Map.of(\"tr_tonnage\", 12), poly).build()";

    private final ApiClient client;
    private final RoadPermitEnvironment environment;

    /**
     * Creates the endpoint; usually obtained from {@link ApiClient#roadPermit(RoadPermitEnvironment)}.
     *
     * @param client client sending the requests
     * @param environment environment of the documents
     * @throws IllegalArgumentException when any argument is {@code null}
     */
    public RoadPermit(ApiClient client, RoadPermitEnvironment environment) {
        this.client = EndpointParams.requireNonNull(
                client, "new RoadPermit", "client", "use client.roadPermit(RoadPermitEnvironment.UAT)");
        this.environment = EndpointParams.requireNonNull(
                environment,
                "new RoadPermit",
                "environment",
                "pass RoadPermitEnvironment.UAT for tests or RoadPermitEnvironment.PROD for production");
    }

    /**
     * Returns the environment sent with every request of this endpoint.
     *
     * @return the environment
     */
    public RoadPermitEnvironment environment() {
        return environment;
    }

    /**
     * Lists every document of the environment.
     *
     * @return the response; the result is a list of documents, each with {@code id}, {@code document_name},
     *     {@code permissions}, {@code poly} (GeoJSON {@code Polygon}), {@code date_start}, {@code date_end},
     *     {@code description}, {@code location} and {@code is_valid_after_expiry}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse list() {
        return list(RoadPermitListRequest.builder().build());
    }

    /**
     * Lists the documents of the environment that match the filters.
     *
     * @param request filters, e.g. {@code RoadPermitListRequest.builder().id("60a79ba2").build()}
     * @return the response; the result is a list of documents with the keys of {@link #list()}
     * @throws IllegalArgumentException when {@code request} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse list(RoadPermitListRequest request) {
        EndpointParams.requireNonNull(
                request,
                "RoadPermit.list",
                "request",
                "build one with RoadPermitListRequest.builder().build() or call list() without filters");
        return client.call(SERVICE, "list", withEnvironment(request.toParams()));
    }

    /**
     * Adds a document, like the PHP SDK: when {@code extra} of the request has an {@code id} other than
     * {@code null}, the request goes to {@code overwrite} of that document instead; an {@code id} equal to
     * {@code null} is left out.
     *
     * <pre>{@code
     * String id = (String) client.roadPermit(RoadPermitEnvironment.UAT)
     *         .add(RoadPermitRequest.builder(Map.of("tr_tonnage", 12), poly).build())
     *         .dataAsMap()
     *         .get("id");
     * }</pre>
     *
     * @param request the document, e.g. {@code RoadPermitRequest.builder(Map.of("tr_tonnage", 12), poly).build()}
     * @return the response; the result object has {@code id} of the new (or overwritten) document
     * @throws IllegalArgumentException when {@code request} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse add(RoadPermitRequest request) {
        EndpointParams.requireNonNull(request, "RoadPermit.add", "request", REQUEST_HINT);
        Map<String, Object> params = withEnvironment(request.toParams());
        if (params.containsKey(ID_PARAM) && params.get(ID_PARAM) == null) {
            params.remove(ID_PARAM);
        }
        String method = params.get(ID_PARAM) == null ? "add" : "overwrite";
        return client.call(SERVICE, method, params);
    }

    /**
     * Replaces a document with new content.
     *
     * @param id document id returned by {@code add} or {@code list}; overrides {@code id} in {@code extra} of the
     *     request (the PHP SDK lets {@code extra} win)
     * @param request the new content of the document, e.g.
     *     {@code RoadPermitRequest.builder(Map.of("tr_tonnage", 24), poly).build()}
     * @return the response; the result object has {@code id} of the overwritten document
     * @throws IllegalArgumentException when {@code id} is {@code null} or blank, or {@code request} is {@code null}
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse overwrite(String id, RoadPermitRequest request) {
        String operation = "RoadPermit.overwrite";
        EndpointParams.requireText(
                id, operation, "id", "pass the id of a document from the result of RoadPermit.add or RoadPermit.list");
        EndpointParams.requireNonNull(request, operation, "request", REQUEST_HINT);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(ID_PARAM, id);
        params.putAll(request.toParams());
        params.put(ID_PARAM, id);
        return client.call(SERVICE, "overwrite", withEnvironment(params));
    }

    /**
     * Removes a document.
     *
     * @param id document id returned by {@code add} or {@code list}
     * @return the response; the result object has {@code id} and {@code deleted} ({@code true} on success)
     * @throws IllegalArgumentException when {@code id} is {@code null} or blank
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse remove(String id) {
        return remove(id, null);
    }

    /**
     * Removes a document with additional parameters.
     *
     * @param id document id returned by {@code add} or {@code list}; overrides {@code id} in {@code extra}
     * @param extra additional parameters sent unchanged, or {@code null} for none; never modified
     * @return the response; the result object has {@code id} and {@code deleted} ({@code true} on success)
     * @throws IllegalArgumentException when {@code id} is {@code null} or blank
     * @throws pl.automapa.mapservices.exception.AutomapaException on an HTTP, API or transport error
     */
    public ApiResponse remove(String id, Map<String, ?> extra) {
        EndpointParams.requireText(
                id, "RoadPermit.remove", "id", "pass the id of a document from the result of RoadPermit.list");
        return client.call(SERVICE, "remove", withEnvironment(EndpointParams.merge(extra, ID_PARAM, id)));
    }

    private Map<String, Object> withEnvironment(Map<String, Object> params) {
        params.put(ENVIRONMENT_PARAM, environment.apiValue());
        return params;
    }
}
