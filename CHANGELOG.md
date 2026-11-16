# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-11-16

First version of the Java SDK. Feature parity with the PHP SDK (`automapa/map-services`),
without the deprecated speed report service.

### Added
- `ApiClient` with lazy session handling (`getSalt` → `md5(md5(password) + salt)` → `generateSession`), one retry
  with a fresh session after HTTP 403 (not after "no permission"; only the rejected session is cleared, so
  concurrent callers keep a session renewed meanwhile), `openSession()`, low-level `call()` and `request()`
- `Config` with a builder and `Config.fromEnvironment()` (`AUTOMAPA_API_KEY`, `AUTOMAPA_API_PASSWORD`);
  `toString()` masks the API key and the password
- Services: `PingPong`, `Session` (`getSalt`, `generateSession`, `login`), `Geocoder` (`geocode`, `geocodemulti`,
  `revgeocode`, `revgeocodemulti`), `Autocomplete` (`search`, `searchAddress`, `getPostalCodesForStreet`), `Routes`
  (`route`, `matrix`, `optimize`, `optimizeQueue`, `optimizeQueueResult`), `Road` (`getSegmentInfo`, `speedCheck`,
  `speedCheckMulti`)
- `RoadPermit` (`list`, `add`, `overwrite`, `remove`) with `RoadPermitRequest`, `RoadPermitListRequest` and the
  `RoadPermitEnvironment` enum: `client.roadPermit()` for production, `client.roadPermit(RoadPermitEnvironment.UAT)`
  for the test environment
- Request objects with builders and an `extra` map forwarded to the API; named parameters override `extra`
- Response formats `native` and `google` (`GoogleFormatter`) and the `GoogleGeocodeAdapter` drop-in
- Exception hierarchy under `AutomapaException` with messages that explain how to fix the problem
- `DebugHttpClient` printing requests and responses with the `Session-Id` header and the JSON fields `pass`, `key`
  and `sessionId` masked
- `User-Agent: automapa-java-sdk/<version>` header
- Own JSON codec and `java.net.http` transport: zero runtime dependencies, Java 17+
- Runnable examples in `examples/` (`make examples`)
- Distribution as a jar attached to GitHub Releases (installation steps in the README)
- `make package` building the jar with `-sources.jar` and `-javadoc.jar` and verifying their contents; Maven Central
  metadata and a `release` profile (sources, javadoc, GPG signing, Central Portal upload)

### Changed
- `make examples` runs only the examples of the services listed in `AUTOMAPA_API_REQUIRED_ACCESS` and reports the
  others as skipped; `EXAMPLES_DRY_RUN=1 make examples` prints the plan without calling the API

[Unreleased]: https://github.com/automapa/automapa-map-services-java/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/automapa/automapa-map-services-java/releases/tag/v0.1.0
