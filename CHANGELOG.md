# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-04-21

### Added

- Initial public release as Maven Central artifacts (`io.spia:gradle-plugin`,
  `io.spia:processor`). The plugin auto-registers the processor on the
  consumer's `ksp` configuration — consumers only need the `plugins { }` block.
- Spring Boot `@RestController` scanning via KSP with no Spring dependency
  on the processor (annotation detection by fully-qualified name).
- HTTP method support: `@GetMapping`, `@PostMapping`, `@PutMapping`,
  `@DeleteMapping`, `@PatchMapping`.
- `@PathVariable`, `@RequestBody`, `@RequestParam`, `@RequestHeader`
  parameter kinds.
- `@RequestParam` `required` / `defaultValue` extraction. Optional query
  parameters render as `name?: type` in the SDK signature, and the
  server-side `defaultValue` is preserved in a JSDoc `@param [name=value]`
  block without being injected into the HTTP request.
- Nested DTO traversal to arbitrary depth.
- Multi-parameter generic response types (e.g. `ApiResponse<Data, Error>`,
  `Page<T>`). One `interface` per declaration; usage sites substitute the
  concrete type arguments.
- `ResponseEntity<T>` unwrapping.
- Kotlin `enum class` support with configurable output (`union` type alias
  or TypeScript `enum`).
- Collection, map, and nullability type mapping.
- `java.time.*`, `java.util.UUID`, `java.util.Date` mapped to `string`.
- axios and fetch API client templates; fetch queries built via
  `URLSearchParams` so `undefined` values skip the wire.
- `spia { outputPath, apiClient, enumStyle, longType }` DSL.
- Processor unit test scaffold using `dev.zacsweers.kctfork:ksp:0.5.1`
  (KSP 2.x-compatible fork).
- `app/` demo covering CRUD (GET/POST/PUT/DELETE), nested DTO, single- and
  multi-parameter generics, `@RequestParam` pagination with defaults.
- `app/frontend/` TypeScript consumer (`main.ts`) that imports the
  generated SDK and compiles under `tsc --strict`.
- `./gradlew :app:build` release gate that runs `npm install` and
  `tsc --noEmit --strict` against the generated SDK.
- Manual integration script (`app/frontend/npm run integration`) that hits
  a running Spring server through the SDK.
- Apache 2.0 LICENSE.
- `docs/RELEASING.md` with the Sonatype Central Portal + GPG procedure.
- `docs/samples/mavenlocal-consumer/` dry-run sample for validating the
  published bundle.

### Fixed

- DTO deduplication now uses fully-qualified class names via a two-pass
  name resolver, preventing collisions between same-named DTOs in different
  packages. Colliding names are retroactively disambiguated with a
  package-prefix.
- Circular-reference DTOs no longer emit as empty interfaces. The previous
  `getOrPut` pattern discarded the lambda's real return value; the fix
  installs a placeholder explicitly before recursing and overwrites it
  with the full DTO after field resolution.
- `fetch` template URLs are now encoded via `encodeURIComponent` on both
  path variables and query values, and the query string is actually
  appended to the request URL (it was previously computed but dropped).
- `SpiaPlugin` now forwards `spia.projectDir` as a KSP argument, so the
  output path is resolved reliably against the consumer project's root.

### Known Issues (out of scope for v0.1.0)

- Bean Validation (`@Valid`, `@NotNull`, `@Size`, …) annotations are not
  reflected in TypeScript types.
- Spring Security annotations are not processed.
- `Pageable` / Spring Data `Page` is not a first-class type (plain DTOs
  work).
- Kotlin `sealed class` is not mapped to TypeScript discriminated unions.
- `ProblemDetail` / RFC 9457 error bodies are not emitted.
- `@JsonProperty` / `@JsonAlias` name overrides are ignored.
- Multipart / file upload endpoints are skipped.

[Unreleased]: https://github.com/spia/spia/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/spia/spia/releases/tag/v0.1.0
