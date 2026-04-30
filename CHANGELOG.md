# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.1] - 2026-04-30

### Fixed

- Generated SDK fetch error path no longer drops `res.status` when the
  server returns a non-JSON error body. `await res.json()` is now wrapped
  in nested try/catch (json → text → null) so `ApiError(res.status, body, …)`
  is constructed unconditionally (#23).
- `renderSealedUnion` now validates `@JsonTypeName` values at KSP time and
  fails the build (`KSPLogger.error` + `IllegalArgumentException`) when a
  tag contains `'`, `\`, backtick, `\n`, or `\r` — characters that would
  produce syntactically invalid TypeScript in the discriminated-union
  emission (#25, marker `EC-12`).
- **BREAKING (on-disk format)** — `<outputPath>.spia-lock` lines are now
  tab-separated (`moduleName<TAB>sha256<TAB>iso8601`) instead of colon-
  separated, eliminating the colon-collision risk for module names
  containing `:`. The lockfile is now written via `Files.createTempFile`
  + `Files.move(ATOMIC_MOVE)` to close the TOCTOU window between
  existence check and write. Stale colon-delimited lines from earlier
  versions are silently dropped on read and re-validated on next build —
  no manual migration required (#26, marker `EC-13`).

## [0.4.0] - 2026-04-28

### Breaking Changes

- **BREAKING CHANGE** — `createApi` for the fetch client no longer accepts a bare `baseUrl: string` argument. The signature is now `createApi(options?: ClientOptions)` where `ClientOptions` is `{ baseUrl?: string; authInterceptor?: ...; retry?: ... }`. The `baseUrl` is resolved as: caller-provided `options.baseUrl` > buildtime `config.baseUrl` (set via `spia { clientOptions { baseUrl = "..." } }`) > `""`. Consumers must migrate call sites from `createApi("/api")` to `createApi({ baseUrl: "/api" })` or configure the base URL at build time via the Gradle DSL.
- **BREAKING CHANGE** — Kotlin `value class` / `inline class` types are now emitted as branded TypeScript types (`type UserId = string & { readonly __brand: 'UserId' }`) with a constructor helper instead of being flattened to their underlying primitive. Any frontend code that previously assigned a plain `string` where a value-class type is expected will now fail to compile under `tsc --strict`.

### Added

- Bean Validation constraints (`@NotNull`, `@Size`, `@Min`, `@Max`, `@Pattern`, `@NotBlank`, `@Email` from `jakarta.validation.constraints`) are now propagated to TypeScript as JSDoc tags (`@minLength`, `@maxLength`, `@minimum`, `@maximum`, `@pattern`, `@format email`) (EC-01).
- Kotlin `sealed class` annotated with `@JsonTypeInfo(use=NAME, property="…")` is now emitted as a TypeScript discriminated union (`type Shape = ({ kind: 'circle' } & Circle) | …`) instead of requiring a manual nullable-field DTO workaround (EC-04).
- `Pageable` parameters (`org.springframework.data.domain.Pageable`) are now expanded inline as `page?: number; size?: number; sort?: string` query fields in the generated SDK signature (EC-06).
- SSE endpoints (`Flux<ServerSentEvent<T>>`) now emit as `AsyncIterable<T>` in the generated TypeScript SDK (EC-09 / reactive support).
- `ResponseEntity<Resource>` (file download endpoints) now emits as `Promise<Blob>` (EC-09).
- Java `@RestController` classes and plain Java POJOs (JavaBeans getter convention) are now processed (minimum support, P-13).
- `ClientOptions` (fetch mode) gains two new optional fields: `authInterceptor` for injecting auth headers before each request, and `retry` for configuring automatic retry with backoff on server errors (status >= 500). Both are opt-in; existing clients that pass no `ClientOptions` require no changes.
- Kotlin `value class` (`@JvmInline`) is emitted as a TypeScript branded type with a constructor helper (e.g., `type UserId = string & { readonly __brand: 'UserId' }` + `const UserId = (raw: string): UserId => raw as UserId`).
- `@JsonProperty`, `@JsonAlias`, and `@JsonInclude(NON_NULL)` annotations on DTO fields are now recognized and reflected in the generated TypeScript interface (field key rename, JSDoc alias comment, optional marking respectively).
- Per-controller bundle splitting via `spia { splitByController = true }`: emits `_shared.ts`, `<slug>.api.ts` per controller, and `index.ts` barrel.
- npm package assembly via `spiaPackNpm` Gradle task and `spia { npmPackage { name.set("...") } }` DSL block.
- Typed `ApiError<T>` class emitted in every SDK; per-endpoint typed error aliases generated from `@ExceptionHandler` / `@ControllerAdvice` methods annotated with `@ResponseStatus`.
- `@CookieValue` parameters collected into `cookies?: Record<string, string>` and transmitted via a `Cookie: k=v` header.
- `@MatrixVariable` parameters treated as query-string parameters.
- `@ModelAttribute` DTO fields flattened into individual query-string parameters.
- `@RequestAttribute` parameters excluded from the generated TS signature (server-side only); a KSP warn is emitted.

### Changed

- `longType` DSL option now also accepts `"bigint"` in addition to `"number"` and `"string"`.
- Fetch template `URLSearchParams` query building now skips `undefined` values at runtime (no wire pollution).

### Fixed

- Multi-module `outputPath` conflict now emits a `KSPLogger.warn` when two subprojects write the same SDK file (closes EC-10). A `<outputPath>.spia-lock` sidecar tracks module name + SHA-256 digest + ISO 8601 timestamp per writer.
- `processor` test coverage raised to 83% line / 52% branch; JaCoCo violation rule enforces ≥50% line / ≥30% branch (closes EC-11).

### Internal

- New parametrized tests cover all `TypeInfo` sealed `when` branches in `TypeScriptGenerator.renderType()`, nullable/generic/map/list/collection type variants in `TypeResolver.resolveByName()`, and all `ParameterKind` variants in `ControllerAnalyzer`.

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
- `processor` module test coverage at 0.2% line / 0% branch — JaCoCo report available, dedicated test infrastructure deferred (EC-11).
- EC-11 is now closed: `processor` coverage raised to 83% line / 52% branch; JaCoCo rule enforces ≥50% line / ≥30% branch (see [Unreleased]).

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

[Unreleased]: https://github.com/lyutvs/spia/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/lyutvs/spia/releases/tag/v0.4.0
[0.3.0]: https://github.com/lyutvs/spia/releases/tag/v0.3.0
[0.2.0]: https://github.com/lyutvs/spia/releases/tag/v0.2.0
[0.1.0]: https://github.com/lyutvs/spia/releases/tag/v0.1.0
