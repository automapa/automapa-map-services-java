# Automapa Map Services Java SDK

> European Geocoding, Routing & Address Autocomplete

[![Java](https://img.shields.io/badge/Java-17%2B-blue)](https://openjdk.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

Java SDK for the Automapa Map Services REST API. Simplifies integration with geocoding, routing, road data and
address autocomplete services for Europe.

---

## Getting started with your `.env` file

You will receive your API credentials from us. Copy the template and fill in the values in the root directory of
this repository:

```bash
cp .env.example .env
```

```
AUTOMAPA_API_KEY=your-key
AUTOMAPA_API_PASSWORD=your-password
```

Once the `.env` file is in place, you can **test all endpoints against the live API using Docker** — no local Java
or Maven installation required:

```bash
# Run the full test suite (unit + live API tests) inside Docker
make test

# Run only the live API tests (calls https://api.automapa.pl)
make test-integration

# Run only the unit tests (no API calls)
make test-unit

# Run the quality gate: formatting, Checkstyle, compilation with -Werror, Javadoc, unit tests + coverage
make check

# Run every program from examples/ against the live API
make examples
```

Run `make help` to see every target. Never commit the `.env` file.

> **Zero dependencies.** The SDK has no runtime dependencies — it uses only the JDK (`java.net.http`, its own
> JSON codec). It is compatible with **Java 17 and above**.

---

## 1. Overview

The SDK covers:

- **Geocoding** — convert an address to coordinates and back
- **Routing** — calculate routes, distance matrices, and point-order optimisation
- **Address autocomplete** — suggestions as the user types
- **Road data** — road segment metadata and speed limits
- **Road permits** — weight and size permits tied to a GeoJSON polygon (`RoadPermit`)
- **Google Geocoding API compatibility** — optional `google` response format and a drop-in `GoogleGeocodeAdapter`

The deprecated Speed service is not supported.

Every call is a `POST https://api.automapa.pl/v3/{Service}/{method}` with a JSON body. Responses are plain
`Map`/`List` structures, exactly as returned by the API. For the full list of endpoints and parameters, see the
[Automapa API documentation](https://api.automapa.com).

---

## 2. Installation

Maven:

```xml
<dependency>
  <groupId>pl.automapa</groupId>
  <artifactId>map-services</artifactId>
  <version>0.1.0</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
implementation("pl.automapa:map-services:0.1.0")
```

Requirements: Java 17+, zero runtime dependencies. The artifact is not published to Maven Central yet; until then
build the jars with `make package` (`target/map-services-<version>.jar` plus `-sources.jar` and `-javadoc.jar`,
checked for test classes, fixtures and `.env`) and add the jar to your project.

---

## 3. Client configuration

```java
import pl.automapa.mapservices.ApiClient;
import pl.automapa.mapservices.Config;

ApiClient client = ApiClient.create(Config.builder("YOUR_API_KEY", "YOUR_PASSWORD").build());
```

Read the credentials from the environment variables `AUTOMAPA_API_KEY` and `AUTOMAPA_API_PASSWORD` (a missing
variable throws `IllegalArgumentException` naming it):

```java
ApiClient client = ApiClient.create(Config.fromEnvironment());
```

Optional settings (defaults shown in the comments):

```java
Config config = Config.builder(System.getenv("AUTOMAPA_API_KEY"), System.getenv("AUTOMAPA_API_PASSWORD"))
        .baseUrl("https://api.automapa.pl/") // Config.DEFAULT_BASE_URL
        .version("v3") // Config.DEFAULT_VERSION
        .timeout(Duration.ofSeconds(30)) // Config.DEFAULT_TIMEOUT
        .defaultFormat("native") // "native" or "google"
        .userAgent("my-app/1.0") // default: "automapa-java-sdk/" + ApiClient.SDK_VERSION
        .build();
ApiClient client = ApiClient.create(config);
```

To print every request and response (the `Session-Id` header and the `pass` field are masked as `***`), wrap the
HTTP transport in `DebugHttpClient`:

```java
Config config = Config.fromEnvironment();
ApiClient client = new ApiClient(config, new DebugHttpClient(new JdkHttpClient(config.getTimeout())));
```

### API keys with access to some services only

An API key may cover only some services (for example only `RoadPermit`); calling another service then fails with
an access error from the API. A session belongs to one key, so create a separate `ApiClient` for each key and use
each client only for the services its key covers:

```java
ApiClient permitsClient = ApiClient.create(Config.builder("PERMITS_KEY", "PERMITS_PASSWORD").build());
ApiClient mapsClient = ApiClient.create(Config.builder("MAPS_KEY", "MAPS_PASSWORD").build());

permitsClient.roadPermit(RoadPermitEnvironment.UAT).list();
mapsClient.geocoder().geocode(GeocodeRequest.builder("Warszawa").build());
```

In this repository `AUTOMAPA_API_REQUIRED_ACCESS` in `.env` lists the services the key in `.env` covers
(comma-separated, e.g. `roadpermit` or `autocomplete,geocoder,road,routes,pingpong`). `make test-integration`
runs the live tests of the listed services and skips the others (the `Session` tests always run); `make examples` runs only the examples of the
listed services (see [Examples](#6-examples)).

---

## 4. Session handling

A session is opened automatically (lazy) on the first call that requires authorisation, and renewed once when the
API answers HTTP 403. To open it manually (fetches a salt, hashes the configured password and stores the session id
for subsequent calls):

```java
String sessionId = client.openSession();
```

The raw `Session` endpoints are also exposed, but note that the API expects a **hashed** credential, not the
plain-text password: `pass = md5(md5(password) + salt)`, where `salt` comes from `getSalt()`. Passing the
plain-text password to `generateSession()` results in an authentication error. `login()` does the hashing for you:

```java
Config config = Config.fromEnvironment();

ApiResponse salt = client.session().getSalt(config.getKey()); // salt.dataAsMap().get("salt")
ApiResponse session = client.session().login(config.getKey(), config.getPassword());
ApiResponse manual = client.session().generateSession(config.getKey(), "md5(md5(password) + salt)");
```

Sessions opened through `Session.login()` / `generateSession()` are **not** stored in the client; use
`client.openSession()` when you want the client itself to reuse the session.

---

## 5. Available services

| Class | Methods |
|-------|---------|
| `PingPong` | `ping()` |
| `Session` | `getSalt()`, `generateSession()`, `login()` |
| `Geocoder` | `geocode()`, `geocodemulti()`, `revgeocode()`, `revgeocodemulti()` |
| `Autocomplete` | `search()`, `searchAddress()`, `getPostalCodesForStreet()` |
| `Routes` | `route()`, `matrix()`, `optimize()`, `optimizeQueue()`, `optimizeQueueResult()` |
| `Road` | `getSegmentInfo()`, `speedCheck()`, `speedCheckMulti()` |
| `RoadPermit` | `list()`, `add()`, `overwrite()`, `remove()` |

Each service is available from the client: `client.pingPong()`, `client.session()`, `client.geocoder()`,
`client.autocomplete()`, `client.routes()`, `client.road()`, `client.roadPermit()` (production) and
`client.roadPermit(RoadPermitEnvironment.UAT)` (test environment). Methods with several parameters take a request object
built with a builder: required values go to `builder(...)`, optional ones are builder methods, and `build()`
validates the input (`IllegalArgumentException` with a hint how to fix it).

Coordinates follow the API convention: `x` = longitude, `y` = latitude. Points are `Point(x, y)`, sent as `[x, y]`.

### PingPong

```java
ApiResponse response = client.pingPong().ping("hello");
Object reply = response.dataAsMap().get("msg");
```

### Geocoder

#### `geocode()` — address to coordinates

```java
ApiResponse response = client.geocoder().geocode(GeocodeRequest.builder("Warszawa")
        .street("Domaniewska")
        .house("37")
        .maxResults(1)
        .build());
List<Object> results = response.dataAsList(); // results, each with x, y, city, street, ...
```

Other optional fields: `country`, `province`, `district`, `community`, `quarter`, `pcode`.

#### `geocodemulti()` — batch geocoding (up to 100 addresses)

```java
ApiResponse response = client.geocoder().geocodemulti(List.of(
        Map.of("city", "Warszawa", "street", "Domaniewska", "house", "37"),
        Map.of("city", "Krakow", "street", "Florianska", "house", "1")));
List<Object> results = response.dataAsList(); // one result list per input address
```

#### `revgeocode()` — coordinates to address

```java
ApiResponse response = client.geocoder().revgeocode(RevGeocodeRequest.builder(new Point(21.0073642, 52.18288))
        .snapToBld(true) // snap to the nearest building
        .getPostalCode(true)
        .build());
Map<String, Object> address = response.dataAsMap(); // city, street, house, ...
```

#### `revgeocodemulti()` — batch reverse geocoding

```java
ApiResponse response = client.geocoder().revgeocodemulti(RevGeocodeMultiRequest.builder(
                List.of(new Point(21.0073642, 52.18288), new Point(19.9449799, 50.0646501)))
        .snapToBld(true)
        .snapToBldRadius(50)
        .build());
List<Object> addresses = response.dataAsList(); // one address per input point
```

### Autocomplete

Address and POI autocomplete — returns suggestions as the user types.

#### `search()` — search by phrase

```java
ApiResponse response = client.autocomplete().search(AutocompleteSearchRequest.builder("Warszawa Domaniewska 37")
        .types(List.of("place")) // optional: "place", "poi" or both (not set = both)
        .build());

List<?> items = (List<?>) response.dataAsMap().get("items");
for (Object entry : items) {
    Map<?, ?> item = (Map<?, ?>) entry;
    Map<?, ?> coords = (Map<?, ?>) item.get("coords");
    System.out.println(item.get("name") + " " + coords.get("x") + " " + coords.get("y"));
}
```

Each `items` entry contains: `type`, `subtype`, `name`, `province`, `district`, `community`, `place`, `quarter`,
`street`, `number`, `postal_code`, `coords.{x,y}`.

#### `searchAddress()` — step-by-step address search

Useful for multi-field address forms (city → street → number → postcode).

```java
Map<String, Object> query = new LinkedHashMap<>();
query.put("city", "Warszawa");
query.put("street", "Dom"); // partial street name
query.put("number", "");
query.put("zipcode", "02-672");

ApiResponse response = client.autocomplete().searchAddress(SearchAddressRequest.builder(query, "street")
        .selected(List.of("city", "zipcode")) // optional: fields already confirmed by the user
        .limit(10) // optional: maximum number of results
        .build());
List<?> items = (List<?>) response.dataAsMap().get("items"); // city, street, number, zipcode, count
```

The second `builder` argument is the field being searched: `place`, `street`, `number` or `zipcode`.

#### `getPostalCodesForStreet()` — postal codes of a street

A helper built on `searchAddress()`: returns the distinct postal codes of a street, sorted.

```java
List<String> codes = client.autocomplete().getPostalCodesForStreet("Warszawa", "Domaniewska");
List<String> firstCodes = client.autocomplete().getPostalCodesForStreet("Warszawa", "Domaniewska", 20); // limit
```

> **Note on Autocomplete coordinates:** Unlike `Geocoder`, coordinates are nested:
> `coords.x` = longitude, `coords.y` = latitude.

### Routes

Route calculation, distance matrix, and point-order optimisation. The nested `route` and `object` structures are
plain maps forwarded to the API as they are.

#### `route()` — calculate a route

```java
Map<String, Object> route = new LinkedHashMap<>();
route.put("type", "short"); // "quick" | "short" | "optimal"
route.put("traffic", true); // take live traffic into account
route.put("desc", true); // manoeuvre list; the API returns the travel time "eta" only with desc = true

ApiResponse response = client.routes().route(RouteRequest.builder(
                List.of(new Point(21.0073642, 52.2297), new Point(18.6282, 54.3520)), // Warsaw -> Gdansk
                route,
                Map.of("type", "car"))
        .build());

Map<String, Object> data = response.dataAsMap();
System.out.println(((Number) data.get("length")).longValue() / 1000 + " km");
System.out.println(data.get("eta") + " min"); // estimated travel time
System.out.println(data.get("mapurl")); // link to the map
// data.get("desc") - turn-by-turn manoeuvre list, data.get("tolls") - toll charges
```

#### `matrix()` — distance / time matrix

The API requires at least 4 different points.

```java
ApiResponse response = client.routes().matrix(MatrixRequest.builder(
                List.of(
                        new Point(21.0073642, 52.2297),
                        new Point(18.6282, 54.3520),
                        new Point(16.9252, 52.4064),
                        new Point(19.9449799, 50.0646501)),
                MatrixType.TIME) // TIME or DISTANCE
        .precise(false) // always sent, default false
        .simple(true) // always sent, default false
        .build());

for (Object entry : response.dataAsList()) {
    Map<?, ?> row = (Map<?, ?>) entry;
    System.out.println(row.get("points") + ": " + row.get("length") + " m, " + row.get("duration") + " min");
}
```

#### `optimize()` — synchronous optimisation (max 10 points)

```java
ApiResponse response = client.routes().optimize(OptimizeRequest.builder(
                List.of(new Point(21.0, 52.2), new Point(18.6, 54.4), new Point(16.9, 52.4)),
                OptimizeType.TIME) // TIME or DISTANCE
        .fixedEnd(false) // true (default) = last point is the destination; false = open end
        .object(Map.of("type", "car"))
        .build());

Map<String, Object> data = response.dataAsMap();
Object order = data.get("order"); // optimal index sequence, e.g. "0,2,1"
Object sections = data.get("sections"); // segment details: x, y, eta, dist
```

#### `optimizeQueue()` + `optimizeQueueResult()` — asynchronous optimisation (max 50 points)

```java
// Step 1: submit - every point needs a unique id
ApiResponse queued = client.routes().optimizeQueue(OptimizeQueueRequest.builder(
                List.of(
                        new QueuePoint("Warsaw", 21.0, 52.2),
                        new QueuePoint("Gdansk", 18.6, 54.4),
                        new QueuePoint("Poznan", 16.9, 52.4)),
                OptimizeBy.TIME) // TIME or DISTANCE
        .object(Map.of("type", "car"))
        .build());
String requestId = String.valueOf(queued.dataAsMap().get("requestId"));

// Step 2: poll for the result ("resultReady" is true or 1 when done)
Map<String, Object> result;
Object ready;
do {
    Thread.sleep(2000);
    result = client.routes().optimizeQueueResult(requestId).dataAsMap();
    ready = result.get("resultReady");
} while (!Boolean.TRUE.equals(ready) && !(ready instanceof Number number && number.intValue() == 1));

Object sequence = result.get("optimizedSequence"); // point ids in the optimal order
```

The SDK does not poll by itself; limit the number of attempts in production code (see
`examples/RoutesOptimizeAsync.java`).

### Road

Road segment metadata and speed limits.

```java
// Road segment information nearest to a point
ApiResponse segmentInfo = client.road().getSegmentInfo(SegmentInfoRequest.builder(21.00736, 52.18288)
        .distLimit(50) // search radius in metres (optional)
        .useTolls(1) // 1 = accept tolls, 0 = reject, -1 = ignore (optional)
        .useHighways(-1) // 1 = accept, 0 = reject, -1 = ignore (optional)
        .build());
Object segment = segmentInfo.dataAsMap().get("segment"); // id, streetName, speedLimit, isToll, ...

// The speed limit for a single point
ApiResponse speedCheck = client.road().speedCheck(SpeedCheckRequest.builder(15.2154636, 52.3118895)
        .dir(350) // direction of travel in degrees (optional)
        .build());
Object speedLimit = speedCheck.dataAsMap().get("speedLimit"); // cars
Object speedLimitTruck = speedCheck.dataAsMap().get("speedLimitTruck"); // trucks

// The speed limits for up to 100 points in one request
ApiResponse speedCheckMulti = client.road().speedCheckMulti(List.of(
        new SpeedCheckPoint(15.2154636, 52.3118895, 350), // x, y, direction
        new SpeedCheckPoint(13.8412285, 52.3111549)));
for (Object entry : speedCheckMulti.dataAsList()) {
    System.out.println(((Map<?, ?>) entry).get("speedLimit"));
}
```

`SegmentInfoRequest` also accepts `dir`, `minCategory`, `ignoreNoWaySegments`, `treatTruckTollsAsTolls`,
`ignoreSegmentsUnderConstruction`, `ignorePhysicallyBlockedSegs` and `useFerries`; `SpeedCheckRequest` accepts
`distLimit`.

### RoadPermit

Manage road permits (weight and size limits) tied to a geographic polygon. Permits live in one of two environments:

- `client.roadPermit()` — **production** (`RoadPermitEnvironment.PROD`), the permits used by real routing,
- `client.roadPermit(RoadPermitEnvironment.UAT)` — the test environment; use it for development and tests.

The polygon is a GeoJSON `Polygon` map: coordinates are `[lon, lat]` (longitude first, like `x`, `y` elsewhere in
the API) and every ring must be closed — its first and last points are equal.

```java
RoadPermit permits = client.roadPermit(RoadPermitEnvironment.UAT); // test environment
RoadPermitEnvironment environment = permits.environment(); // UAT

// GeoJSON Polygon: [lon, lat] points, the ring is closed (first point == last point)
Map<String, Object> poly = Map.of(
        "type", "Polygon",
        "coordinates", List.of(List.of(
                List.of(21.000, 52.220),
                List.of(21.020, 52.220),
                List.of(21.020, 52.240),
                List.of(21.000, 52.240),
                List.of(21.000, 52.220))));

// Add a permit; the API returns its id
ApiResponse added = permits.add(RoadPermitRequest.builder(Map.of("tr_tonnage", 12, "tr_tonn_axis", 8.5), poly)
        .dateStart("2026-01-01")
        .dateEnd("2026-12-31")
        .description("Weight limit - city centre")
        .documentName("DOC-001")
        .location("Warsaw")
        .isValidAfterExpiry(false)
        .extra("myCustomField", "value") // forwarded to the API as is
        .build());
String id = String.valueOf(added.dataAsMap().get("id"));

try {
    // Replace a permit: every field is replaced, not merged
    permits.overwrite(id, RoadPermitRequest.builder(Map.of("tr_tonnage", 24), poly).build());

    // List all permits of the environment, or filter by id or document name
    for (Object permit : permits.list().dataAsList()) {
        System.out.println(((Map<?, ?>) permit).get("document_name"));
    }
    permits.list(RoadPermitListRequest.builder().id(id).build());
    permits.list(RoadPermitListRequest.builder().documentName("DOC-001").build());
} finally {
    // Remove the permit
    permits.remove(id);
}
```

Available `permissions` keys: `tr_tonnage` (total weight, t), `tr_tonn_axis` (axle load, t), `tr_height` (m).
`RoadPermitRequest` sends the optional fields with the API names `date_start`, `date_end`, `description`,
`document_name`, `location` and `is_valid_after_expiry`; the SDK does not validate the geometry or the dates — the
API does. `overwrite(id, request)` always sends the given `id` (it overrides an `id` in `extra`), and every method
sends the environment of its `RoadPermit` object.

---

## 6. Examples

Every example is a single-file Java program (`java -cp target/classes examples/<Name>.java`) reading the
credentials from `AUTOMAPA_API_KEY` / `AUTOMAPA_API_PASSWORD`:

```bash
# Copy configuration
cp .env.example .env
# Fill in AUTOMAPA_API_KEY and AUTOMAPA_API_PASSWORD in .env

# Run all examples against the live API (inside Docker)
make examples
```

When `AUTOMAPA_API_REQUIRED_ACCESS` is set, `make examples` runs only the examples of the listed services and
reports the others as `SKIPPED (<service> not in AUTOMAPA_API_REQUIRED_ACCESS)`; an empty or unset list runs every
example. `EXAMPLES_DRY_RUN=1 make examples` prints the `RUN`/`SKIP` plan without calling the API.

| Example | Shows |
|---------|-------|
| `examples/PingPong.java` | opening a session and `ping()` |
| `examples/GeocoderGeocode.java` | single address → coordinates |
| `examples/GeocoderGeocodeMulti.java` | batch address geocoding |
| `examples/GeocoderRevGeocode.java` | coordinates → single address |
| `examples/GeocoderRevGeocodeMulti.java` | coordinates → multiple addresses |
| `examples/AutocompleteSearch.java` | search places and POI by phrase |
| `examples/AutocompleteSearchAddress.java` | search address objects |
| `examples/AutocompleteGetPostalCodes.java` | postal codes of a street |
| `examples/RoutesRoute.java` | point-to-point route |
| `examples/RoutesMatrix.java` | distance/time matrix |
| `examples/RoutesOptimize.java` | sync optimisation (max 10 points) |
| `examples/RoutesOptimizeAsync.java` | async optimisation (max 50 points) |
| `examples/RoadSegmentInfo.java` | road segment metadata |
| `examples/RoadSpeedCheck.java` | speed limit for a single point |
| `examples/RoadSpeedCheckMulti.java` | speed limits for multiple points |
| `examples/RoadPermitList.java` | road permits of the test (`uat`) environment, read only |
| `examples/RoadPermitLifecycle.java` | add → overwrite → remove a permit in `uat` (removed in `finally`) |
| `examples/GoogleGeocodeAdapterExample.java` | Google Geocoding API style input and output |

---

## 7. Additional parameters (`extra`)

The SDK does not restrict unknown parameters — all fields are forwarded to the API as they are. Every request
builder has `extra(key, value)` and `extra(map)`; methods without a request object have an overload taking an
`extra` map as the last argument. Named parameters always take priority over `extra`:

```java
// Request objects
client.autocomplete().search(AutocompleteSearchRequest.builder("Warszawa")
        .extra("myCustomParam", "value")
        .build());

// Named parameters override extra when the same key appears in both: maxResults = 1 is sent
client.geocoder().geocode(GeocodeRequest.builder("Warszawa")
        .extra(Map.of("maxResults", 5, "myCustomParam", "value"))
        .maxResults(1)
        .build());

// Methods without a request object
client.pingPong().ping("hello", Map.of("myCustomParam", "value"));
client.routes().optimizeQueueResult("request-id", Map.of("myCustomParam", "value"));
```

---

## 8. Low-level `call()` and `request()`

```java
// Via service + method name (preferred): session, formats and error mapping included
ApiResponse viaCall = client.call("Geocoder", "newMethod", Map.of("param", "value"));

// Or via the URL path (fallback): no session header and no formatter
ApiResponse viaRequest = client.request("POST", "/v3/Session/getSalt", Map.of("key", "YOUR_API_KEY"));
```

---

## 9. Response formats: `native` vs `google`

```java
// Native format (default)
ApiResponse response = client.geocoder().geocode(GeocodeRequest.builder("Krakow").build());
Object data = response.data(); // API data: the "result" field
Map<String, Object> raw = response.raw(); // full raw response (always available)

// Google format
ApiResponse google = client.geocoder().geocode(GeocodeRequest.builder("Krakow").format("google").build());
Map<String, Object> googleData = google.dataAsMap(); // status, results[], _raw
```

`format` is never sent to the API. The `google` format applies to `Geocoder` and `Autocomplete` (`search`,
`searchAddress`) responses; other methods return the raw response with an added `_raw` key. Set the default for
every call with `Config.builder(...).defaultFormat("google")`.

### `GoogleGeocodeAdapter` — drop-in for Google Geocoding API code

Accepts Google Geocoding API parameters (`components` or `address`) and returns the Google structure:

```java
GoogleGeocodeAdapter adapter = new GoogleGeocodeAdapter(client);

Map<String, Object> byComponents =
        adapter.geocode(Map.of("components", "locality:Warszawa|route:Domaniewska|street_number:37"));
Map<String, Object> byAddress = adapter.geocode(Map.of("address", "Domaniewska 37, 02-672 Warszawa"));
Map<String, Object> byMap =
        adapter.geocode(Map.of("address", Map.of("city", "Warszawa", "street", "Domaniewska", "house", "37")));
Object status = byAddress.get("status"); // "OK" or "ZERO_RESULTS"
```

Google-only parameters (`language`, `key`, `region`, `result_type`, `location_type`) are accepted and not sent;
`maxResults` defaults to `1`.

---

## 10. Google format limitations

| Google field | Value |
|---|---|
| `geometry.viewport` | always `null` (not provided by the API) |
| `place_id` | always `null` (not provided by the API) |

> **Note:** The Automapa API uses `x` = longitude and `y` = latitude — the opposite of Google's convention. The
> `google` format maps them to `geometry.location.lng` and `geometry.location.lat`.

---

## 11. Error handling

Every API error is an unchecked `AutomapaException` subclass; the message says what happened, the HTTP status,
the API message and what to do. Invalid input to a builder or method throws `IllegalArgumentException` before any
request is sent.

| Situation | Exception class |
|-----------|----------------|
| Invalid API key / password (HTTP 401) | `AuthenticationException` |
| Expired or rejected session (HTTP 403 after one retry) | `SessionException` |
| Parameter validation failure (HTTP 400) | `ValidationException` |
| Resource not found (HTTP 404) | `NotFoundException` |
| Rate limit exceeded (HTTP 429) | `RateLimitException` |
| Network error, timeout | `NetworkException` |
| Server error (HTTP 5xx) | `ServerException` |
| Unexpected response (not JSON, other status) | `UnknownResponseException` |

```java
try {
    ApiResponse response = client.geocoder().geocode(GeocodeRequest.builder("Warszawa").build());
} catch (AuthenticationException e) {
    System.err.println("Authentication error: " + e.getMessage());
} catch (AutomapaException e) {
    System.err.println("API error " + e.getStatusCode() + ": " + e.getApiMessage() + " (" + e.getApiCode() + ")");
}
```

---

## Thread safety

`ApiClient` is thread safe: share one instance across threads. Concurrent calls on a fresh client open exactly one
session. Endpoint objects (`client.geocoder()` and others) are stateless, and request objects copy their input when
they are built.

---

## Development

All commands run in Docker through `make` (`make help` lists them). Before committing run the quality gate
(formatting, Checkstyle, compilation with `-Werror`, Javadoc, unit tests with line coverage of at least 85%):

```bash
make format
make check
```

---

## License

[MIT](LICENSE) © 2026 Automapa sp. z o.o.
