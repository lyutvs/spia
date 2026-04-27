# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- Multi-module `outputPath` conflict now emits a `KSPLogger.warn` when two subprojects write the same SDK file (closes EC-10). A `<outputPath>.spia-lock` sidecar tracks module name + SHA-256 digest + ISO 8601 timestamp per writer.

## [0.3.0] - 2026-04-27

### Changed

- **Breaking (toolchain)** — Minimum Kotlin raised to **2.3.x** (from 2.1.x). Consumers must upgrade their Kotlin plugin version accordingly.
- **Breaking (toolchain)** — Minimum KSP raised to **2.3.x** (from 2.1.x-1.0.31). The `com.google.devtools.ksp` plugin version on the consumer side must match the Kotlin version (e.g., `2.3.21` Kotlin with KSP `2.3.7`).
- **Breaking (toolchain)** — Minimum Gradle raised to **9.x** (from 8.10.x). The published plugin is compiled against Gradle 9 APIs; consumers must upgrade their Gradle wrapper.
- **Breaking** — `spia` plugin default `apiClient` flipped from `"axios"` to
  `"fetch"`. Consumers who do not explicitly pin `apiClient` in their
  `spia { }` block will receive a fetch-based SDK whose `createApi`
  signature is `createApi(baseUrl: string)` instead of
  `createApi(client: AxiosInstance)`. To preserve the previous behavior,
  add `apiClient = "axios"` to your `spia { }` configuration.
- Fetch template now emits `if (!res.ok) throw new Error(…)` before every
  response parse, bringing HTTP error semantics in line with axios (which
  throws on 4xx/5xx automatically). Previously, 4xx/5xx responses were
  silently passed to `res.json()`.

### Added

- README "Quick Start" section demonstrating the 3-line fetch-based setup.
- `ProcessorSmokeTest`: new `fetch emits createApi(baseUrl: string) …` case
  covering the fetch branch. Existing tests now pin `apiClient = "axios"`
  explicitly so they are insulated from default-flip behavior changes.

### Internal

- Publishing plugin: `com.vanniktech.maven.publish` 0.30 → 0.36. `publishToMavenCentral()` now targets Central Portal by default (no `SonatypeHost` argument needed).
- Test infrastructure: `kctfork` 0.5 → 0.12 (KSP2 is the only mode), `junit-jupiter` 5 → 6, `typescript` 5.9 → 6.0 (requires `"types": ["node"]` in `tsconfig.json` for consumers using `@types/node` globals).
- Demo module: Spring Boot 3.4 → 4.0. SPIA's processor has no Spring runtime dependency; annotation detection by FQN is unchanged.
- GitHub Actions CI: processor tests + KSP + `tsc --strict` + JaCoCo → Codecov.
- Tag-push release automation: `v*` tag triggers signed build, Central Portal staging, and a draft GitHub Release.
- Supply-chain hygiene: Dependabot across 6 ecosystems, CodeQL for Kotlin/Java, PR-time `dependency-review-action`.
- Community files: issue/PR templates, `SECURITY.md` (GitHub Private Vulnerability Reporting), branch protection on `main`.

### Migration

1. Run a build; if the generated `api-sdk.ts` now shows
   `createApi(baseUrl: string)` where you previously had
   `createApi(client: AxiosInstance)`, the default flip affected you.
2. Either migrate consumer call sites to the 3-line fetch form, or pin
   `apiClient = "axios"` to restore the previous shape.

## [0.2.0] - 2026-04-23

### Added
- `@RequestPart` / `MultipartFile` support: SDK builds `FormData` and renders `File | Blob` / `(File | Blob)[]` types in axios and fetch templates (EC-03).
- `@RequestHeader` transmission: extracts annotation `value`/`name` as the wire key, passes via axios `headers` config and fetch `headers` object (EC-07).

### Changed
- **Breaking** — `kotlin.Any` and `java.lang.Object` return types now map to TypeScript `unknown` instead of generating an empty `interface Any {}` (EC-02).
- **Breaking** — Nullable rendering unified: both `renderDto` and `renderGenericInterface` emit `T | null` for nullable fields (previously `Generic` interfaces used `T?`) (EC-05).
- `@PathVariable` regex constraints now stripped from generated URL templates (`{id:[0-9]+}` → correct `${id}` substitution) in both `buildTsPath` and `buildFetchPath` (EC-08).
- `@PathVariable` custom binding name resolved from `value`/`name` annotation attributes instead of always using the Kotlin parameter name (EC-08).

### Fixed
- `Multipart / file upload endpoints` — previously skipped, now fully supported (was Known Issue in v0.1.0).

### Known Issues (carried over from v0.1.0 audit)
- Multi-module `outputPath` conflict — last-write-wins, no warning emitted (EC-10, deferred to v0.3.0).
- `processor` module test coverage at 0.2% line / 0% branch — JaCoCo report available, dedicated test infrastructure deferred (EC-11, deferred to v0.3.0).

### Breaking Changes
See `### Changed` items marked **Breaking**:
- `kotlin.Any` / `java.lang.Object` mapping change
- Nullable rendering unification

v0.1.0 consumers depending on `interface Any {}` or `field?: T` rendering must update their TypeScript code.

## [0.1.0] - 2026-04-21

### Added

- Initial public release as Maven Central artifacts
  (`io.github.lyutvs:gradle-plugin`, `io.github.lyutvs:processor`). The plugin
  id is `io.github.lyutvs.spia` — the plugin auto-registers the processor on
  the consumer's `ksp` configuration, so consumers only need the
  `plugins { id("io.github.lyutvs.spia") version "..." }` block.
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

[Unreleased]: https://github.com/lyutvs/spia/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/lyutvs/spia/releases/tag/v0.3.0
[0.2.0]: https://github.com/lyutvs/spia/releases/tag/v0.2.0
[0.1.0]: https://github.com/lyutvs/spia/releases/tag/v0.1.0
