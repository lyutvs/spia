<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white" alt="TypeScript"/>
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/KSP-Powered-blueviolet?style=for-the-badge" alt="KSP"/>
</p>

# SPIA

[![CI](https://github.com/lyutvs/spia/actions/workflows/ci.yml/badge.svg)](https://github.com/lyutvs/spia/actions/workflows/ci.yml) [![codecov](https://codecov.io/gh/lyutvs/spia/branch/main/graph/badge.svg)](https://codecov.io/gh/lyutvs/spia) [![Maven Central (plugin)](https://img.shields.io/maven-central/v/io.github.lyutvs/gradle-plugin?label=gradle-plugin)](https://central.sonatype.com/artifact/io.github.lyutvs/gradle-plugin) [![Maven Central (processor)](https://img.shields.io/maven-central/v/io.github.lyutvs/processor?label=processor)](https://central.sonatype.com/artifact/io.github.lyutvs/processor) [![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

> Scan your Spring Boot controllers at compile time and automatically
> generate a **type-safe TypeScript SDK** ready to drop into your frontend.

SPIA is a KSP symbol processor + Gradle plugin. It reads standard Spring Web
annotations on your controllers and DTOs and emits a single `.ts` file
containing interfaces, enums, and a `createApi()` function that wraps your
endpoints. When your backend types change, the regenerated SDK breaks the
frontend build immediately — catching API drift at compile time.

## Why SPIA?

Spring Boot + TypeScript teams usually keep API types in sync one of three
ways: copy-pasting DTOs by hand, running an OpenAPI pipeline, or giving up
and typing `any`. SPIA is a fourth option — the compiler does it for you.
Write a `@RestController`, run `./gradlew build`, and your frontend gets a
single typed `.ts` file. Change the DTO, and the frontend build breaks
first. No drift, no runtime spec, no template fight.

### Depending on where you're coming from

| Coming from | What SPIA replaces | Core win |
|---|---|---|
| **Hand-written types** | Manually mirroring DTOs in `*.ts` | DTO changes surface as a frontend `tsc` error, not a production 500. Zero boilerplate per endpoint. |
| **`springdoc-openapi` + `openapi-typescript` / `openapi-generator`** | Boot the server → fetch `/v3/api-docs` → run codegen | No intermediate JSON schema, no server boot to regenerate, no Mustache templates. Kotlin `T?` and multi-parameter generics (`ApiResponse<D, E>`) are preserved as-is. |
| **tRPC-style fullstack type sharing** | RPC runtime + shared TS package + monorepo plumbing | Your backend stays standard Spring REST. The frontend imports one generated `.ts`. No shared package, no RPC protocol. |

### How SPIA compares

| Criterion | SPIA | `springdoc + openapi-typescript` | Manual |
|---|---|---|---|
| Generation time | compile (KSP) | runtime (server must boot) | — |
| Intermediate artifact | none (direct `.ts`) | OpenAPI JSON | — |
| Kotlin `T?` nullability | `T \| null` preserved | depends on Jackson config | manual |
| Multi-parameter generics (`Page<T>`, `ApiResponse<D, E>`) | emitted as interfaces | typically flattened | manual |
| Runtime endpoint exposed | no | `/v3/api-docs` required | — |
| Bean Validation (`@Valid`, `@Size`, …) | ❌ not in v0.2.0 | ✅ | — |
| Multi-language clients (Swift, Android, …) | ❌ TS only | ✅ | — |
| Spring Security / auth flows | handled in your axios instance | partial | — |

### When SPIA is *not* the right fit

SPIA is deliberately narrow. Reach for `openapi-generator` or
`springdoc-openapi` directly when you need:

- **Multi-language clients** — iOS/Android native alongside web.
- **OpenAPI spec as a deliverable** — for external partners, API gateways,
  or contract-first workflows.
- **Bean Validation reflected in the generated SDK** — `@Valid`, `@Size`,
  `@NotNull` carried through to the frontend types.
- **A Java-only backend with Lombok** — Lombok-generated getters are not visible
  to KSP at annotation-processing time; Lombok POJOs will be emitted as empty interfaces.
  Plain Java POJOs (no Lombok) are supported.

For everything else — a Kotlin Spring Boot backend talking to a TypeScript
frontend — SPIA is the shortest path.

## Quick Start

**Prerequisites:** Gradle + JDK 21 on the backend, Node.js 18+ (or any
environment with a `fetch` global) on the frontend.

**1. Apply the plugin to your Spring Boot project:**

```kotlin
// build.gradle.kts (consumer)
plugins {
    id("com.google.devtools.ksp") version "2.1.20-1.0.31"
    id("io.github.lyutvs.spia") version "0.2.0"
}

spia {
    outputPath = "src/main/frontend/generated/api-sdk.ts"
}
```

The plugin resolves the matching `io.github.lyutvs:processor` artifact for
you — no separate `ksp(...)` declaration needed.

**2. Run `./gradlew build`.** KSP writes the generated `api-sdk.ts` to the
configured `outputPath`.

**3. Use it from your frontend in three lines:**

```typescript
import { createApi } from './generated/api-sdk';
const api = createApi('http://localhost:8080');
const user = await api.user.getUserProfile(1);
```

No runtime dependency is installed — the generated SDK uses the platform's
built-in `fetch`. To switch to an axios-based SDK (e.g., for interceptors or
custom auth), see [Configuration options](#configuration-options).

## What's supported in v0.2.0

| Pattern | Status | Notes |
|---|:---:|---|
| `@RestController` + `@RequestMapping` | ✅ | Base path extracted |
| `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` / `@PatchMapping` | ✅ | All 5 HTTP method annotations |
| `@PathVariable` | ✅ | Rendered with `encodeURIComponent` |
| `@RequestBody` | ✅ | POST/PUT/PATCH body typed against the DTO |
| `@RequestParam` | ✅ | `required=false` and `defaultValue` map to optional params + JSDoc `@default` |
| `@RequestHeader` | ✅ | Typed header parameters; transmitted via axios `headers` config (annotation value used as key) |
| `@RequestPart` / `MultipartFile` | ✅ | `File` / `File[]` mapped to `File \| Blob`; SDK builds `FormData` |
| `@ModelAttribute` | ✅ | DTO fields flattened into individual query-string parameters |
| `@CookieValue` | ✅ | Cookie params collected into `cookies?: Record<string, string>`; SDK builds `Cookie: k=v` header |
| `@RequestAttribute` | ✅ (excluded) | Server-side only — excluded from the generated TS signature; KSP warn emitted |
| `@MatrixVariable` | ✅ | Treated as query-string parameter (`;key=value` path segment fallback) |
| Primitives, `String`, `Boolean`, `Int`, `Long`, `Double`, … | ✅ | `Long` configurable (`number` / `string` / `bigint`) |
| `List<T>`, `Set<T>`, `Collection<T>` → `T[]` | ✅ | |
| `Map<K, V>` → `{ [key: K]: V }` | ✅ | |
| Nullability (`T?`) | ✅ | `T \| null` in output |
| Nested DTOs (2+ levels) | ✅ | Dedup by fully-qualified name; same-simple-name classes in different packages get package-prefixed |
| Single-parameter generics (`Page<T>`) | ✅ | Emitted once as `interface Page<T>`; usage sites fill args |
| Multi-parameter generics (`ApiResponse<Data, Error>`) | ✅ | |
| `ResponseEntity<T>` | ✅ | Unwrapped; `T` becomes the response type |
| Kotlin `enum class` | ✅ | Rendered as union or `enum`, configurable |
| `java.time.*`, `java.util.UUID`, `java.util.Date` | ✅ | Mapped to `string` |
| fetch template | ✅ | Default; `createApi(baseUrl: string)`, `URLSearchParams`-backed query building, `encodeURIComponent` on paths, `if (!res.ok) throw` on every call |
| axios template | ✅ | Opt-in via `apiClient = "axios"`; `createApi(client: AxiosInstance)` — delegate to the passed instance for interceptors/auth/retries |

## Not supported in v0.2.0 (known exclusions)

These are intentionally out of scope for the initial release. PRs welcome
after v0.2.0.

| Pattern | Workaround |
|---|---|
| Bean Validation (`@Valid`, `@NotNull`, `@Size`, …) | Use TypeScript-side validation; Spring validates at runtime |
| Spring Security (`@PreAuthorize`, auth headers) | Handle auth in the axios instance passed to `createApi` |
| `Pageable` / `Page` (Spring Data) | Now supported — `Pageable` parameters expand to `page?: number; size?: number; sort?: string` inline query fields |
| `@JsonProperty` / `@JsonAlias` name overrides | Use matching Kotlin property names |
| `ProblemDetail` / RFC 9457 error bodies | Wrap in your own DTO |

## Example — what the SDK looks like

A controller like this:

```kotlin
data class Address(val street: String, val city: String, val zipCode: String)

data class UserProfileDto(
    val id: Long,
    val name: String,
    val email: String,
    val bio: String?,
    val address: Address? = null,
)

data class Page<T>(
    val content: List<T>,
    val totalElements: Long,
    val page: Int,
    val size: Int,
)

@RestController
@RequestMapping("/api/users")
class UserController {
    @GetMapping("/{id}")
    fun getUserProfile(@PathVariable id: Long): UserProfileDto = ...

    @GetMapping("/list")
    fun listUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) keyword: String?,
    ): Page<UserProfileDto> = ...
}
```

produces (excerpt):

```typescript
// Auto-generated by SPIA — do not edit manually.

export interface Address {
  street: string;
  city: string;
  zipCode: string;
}

export interface UserProfileDto {
  id: number;
  name: string;
  email: string;
  bio: string | null;
  address: Address | null;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  page: number;
  size: number;
}

export function createApi(baseUrl: string) {
  return {
    user: {
      getUserProfile: async (id: number): Promise<UserProfileDto> => {
        const res = await fetch(`${baseUrl}/api/users/${encodeURIComponent(String(id))}`, { method: 'GET' });
        if (!res.ok) throw new Error(`SPIA GET ${res.url} failed: ${res.status} ${res.statusText}`);
        return res.json();
      },

      /**
       * @param {number} [page=0] Server default: 0
       * @param {number} [size=20] Server default: 20
       */
      listUsers: async (page?: number, size?: number, keyword?: string): Promise<Page<UserProfileDto>> => {
        const params = new URLSearchParams();
        if (page !== undefined) params.append('page', String(page));
        if (size !== undefined) params.append('size', String(size));
        if (keyword !== undefined) params.append('keyword', String(keyword));
        const qs = params.toString();
        const res = await fetch(`${baseUrl}/api/users/list${qs ? `?${qs}` : ''}`, { method: 'GET' });
        if (!res.ok) throw new Error(`SPIA GET ${res.url} failed: ${res.status} ${res.statusText}`);
        return res.json();
      },
    },
  };
}
```

Use from the frontend:

```typescript
import { createApi, type UserProfileDto } from './generated/api-sdk';

const api = createApi('http://localhost:8080');
const user: UserProfileDto = await api.user.getUserProfile(1);
```

See `app/` for a full working demo (Spring Boot server + TypeScript
consumer), and `app/frontend/src/main.ts` for a type-exercising example that
compiles under `tsc --strict`.

### Alternative: axios

If you need interceptors, automatic 4xx/5xx throwing, retries, or any other
axios feature, set `apiClient = "axios"` in the `spia { }` block and pass an
`AxiosInstance` to `createApi`:

```typescript
import axios from 'axios';
import { createApi } from './generated/api-sdk';

const api = createApi(axios.create({ baseURL: 'http://localhost:8080' }));
```

With `apiClient = "axios"`, the generated `createApi(client: AxiosInstance)`
delegates every call to the passed instance, so headers, baseURL, and auth
are controlled entirely from your own axios setup.

## Configuration options

The `spia { ... }` DSL accepts:

| Option | Type | Default | Meaning |
|---|---|---|---|
| `outputPath` | `String` | `"build/generated/spia/api-sdk.ts"` | Destination of the generated SDK. Resolved relative to the project dir. |
| `apiClient` | `"fetch"` \| `"axios"` | `"fetch"` | Which HTTP client to emit calls against. `"fetch"` uses the platform's built-in global with no runtime dependency. `"axios"` requires axios as a peer dependency on the consumer side. |
| `enumStyle` | `"union"` \| `"enum"` | `"union"` | `"union"` emits `type Color = 'RED' \| 'GREEN';` ; `"enum"` emits a TS `enum`. |
| `longType` | `"number"` \| `"string"` \| `"bigint"` | `"number"` | How Kotlin `Long` is surfaced in TS. |

## Multi-module setup

In a Gradle multi-project build each subproject that exposes REST controllers
can apply the `spia` plugin independently. The recommended pattern is to give
each module its own `outputPath` so the generated SDK files stay separate:

```kotlin
// user-service/build.gradle.kts
spia {
    outputPath = "../frontend/src/generated/user-api-sdk.ts"
}

// order-service/build.gradle.kts
spia {
    outputPath = "../frontend/src/generated/order-api-sdk.ts"
}
```

If two modules accidentally point to the **same** `outputPath`, SPIA emits a
KSP warning in the second module's build output and creates a
`<outputPath>.spia-lock` sidecar file. The lockfile records one line per
writer in `moduleName:sha256:iso8601` format so you can identify which
modules are in conflict:

```
module-a:a1b2c3...:2026-04-27T10:00:00Z
module-b:d4e5f6...:2026-04-27T10:01:00Z
```

The warning contains the marker `EC-10` for easy filtering in CI logs.
No warning is emitted when the same module regenerates its own file (same
module name, any digest) or when a new module writes content that happens to
produce the same digest as an existing entry.

## Bundle splitting

By default the processor emits a single `api-sdk.ts` file containing every
controller. For large APIs this defeats bundler tree-shaking — even if the
frontend only calls one controller, the unused controllers ship with the
bundle.

Enable per-controller splitting in your `spia { … }` block:

```kotlin
spia {
    outputPath = "src/main/frontend/generated/api-sdk.ts"
    splitByController = true
}
```

When `splitByController = true`, the processor emits the following files in
the same directory as `outputPath` (so for the example above,
`src/main/frontend/generated/`):

| File | Contents |
|---|---|
| `_shared.ts` | All DTOs, enums, generics, sealed unions, value classes, the `ApiError` / `ApiTimeoutError` classes, and (for `apiClient = "fetch"`) the `ClientOptions` interface. |
| `<slug>.api.ts` | One file per controller. Exports a `create<Controller>Api(...)` factory and re-exports everything from `_shared`. The slug is the kebab-case form of the controller name with the `Controller` suffix stripped (e.g. `UserController` → `user.api.ts`). |
| `index.ts` | Barrel module that `export *`'s from `_shared` and every per-controller file. |

For *N* controllers, the processor writes *N + 2* files. Consumers can either
import from `index.ts` (no tree-shaking) or, to opt into tree-shaking, import
the per-controller factory directly:

```typescript
// Tree-shaken — bundler drops every other controller's emitted code.
import { createUserApi } from './generated/user.api';
const api = createUserApi({ baseUrl: '/api' });
```

### Verifying tree-shaking with esbuild

A quick way to confirm a bundler is dropping unused controllers is to run
esbuild's `--analyze` against a sample entry:

```bash
echo "import { createUserApi } from './generated/user.api';
const api = createUserApi({ baseUrl: '/api' });
console.log(api);" > /tmp/entry.ts

npx esbuild --bundle --tree-shaking=true --analyze /tmp/entry.ts > /dev/null
```

The analyze output should list `_shared.ts` and `user.api.ts` but NOT any
other `*.api.ts` file. If you see other controllers in the output, double-
check that you imported from `./<slug>.api` directly rather than from the
`index.ts` barrel.

The default for `splitByController` is `false` so existing single-file
consumers see no change.

## Development

Requirements: JDK 21, Node.js 18+, Gradle wrapper (bundled).

```bash
./gradlew build                          # full build incl. tsc --strict gate
./gradlew :app:bootRun                   # start the demo server
cd app/frontend && npm run integration   # hit the running server via the SDK
```

See `docs/RELEASING.md` for the Maven Central release procedure and
`docs/samples/mavenlocal-consumer/` for the dry-run consumer sample.
See `docs/configuration-cache.md` for Gradle Configuration Cache and
incremental build compatibility notes.

## License

Apache 2.0 — see [LICENSE](LICENSE).

## Jackson annotation support

SPIA now recognizes the following Jackson annotations on DTO fields:

| Annotation | Effect on generated SDK |
|---|---|
| `@JsonProperty("field_name")` | The TypeScript interface uses `field_name` as the property key instead of the Kotlin property name. |
| `@JsonAlias("a", "b")` | A JSDoc comment `/** @alias a, b */` is emitted above the field documenting the accepted deserialization aliases. |
| `@JsonInclude(JsonInclude.Include.NON_NULL)` | A nullable field is marked optional (`field?: T \| null`) rather than required-but-nullable (`field: T \| null`), matching the server's omit-when-null behavior. |

Example:

```kotlin
data class UserDto(
    @JsonProperty("user_name")
    @JsonAlias(value = ["name", "userName"])
    val userName: String,

    @JsonInclude(JsonInclude.Include.NON_NULL)
    val bio: String? = null,
)
```

Generates:

```typescript
export interface UserDto {
  /** @alias name, userName */
  user_name: string;
  bio?: string | null;
}
```

## Kotlin value classes

SPIA emits Kotlin `value class` (annotated with `@JvmInline`) as a TypeScript **branded type**,
preserving compile-time type safety that would otherwise be lost if the underlying primitive were used directly.

For example:

```kotlin
@JvmInline
value class UserId(val raw: String)

data class UserDto(val id: UserId, val name: String)
```

generates:

```typescript
export type UserId = string & { readonly __brand: 'UserId' };
export const UserId = (raw: string): UserId => raw as UserId;

export interface UserDto {
  id: UserId;
  name: string;
}
```

The branded type ensures that a plain `string` cannot be accidentally passed where a `UserId` is expected.
The constructor helper (`const UserId = ...`) lets callers wrap a raw value ergonomically: `UserId('abc-123')`.

## Java support (minimum)

SPIA supports Java `@RestController` classes and plain Java POJOs (JavaBeans) as of v0.4.

### What works

- Java `@RestController` / `@RequestMapping` / `@GetMapping` etc. are recognized by their fully-qualified annotation names — the same as Kotlin.
- Plain Java POJO fields are discovered from public getter methods following the JavaBeans convention (`getXxx()` → `xxx`, `isXxx()` → `xxx`).
- Nullable detection: a getter annotated with `@org.jetbrains.annotations.Nullable` or `@jakarta.annotation.Nullable` is emitted as `T | null`.

### Known limitations (P-13)

- **Lombok-generated getters are not supported.** KSP processes Java source before Lombok's annotation processor generates accessor methods. As a result, Lombok `@Data` / `@Getter` POJOs will be emitted as empty interfaces. Use plain Java POJOs (field + constructor + explicit getters) with SPIA, or switch to Kotlin data classes.
- Kotlin-only features (value classes, sealed unions) are not applicable to Java sources.

## Handling errors

SPIA generates a typed `ApiError<T>` class that is thrown whenever a fetch call receives a non-2xx response.
The generated SDK always includes this base class at the top of the output file:

```typescript
export class ApiError<T = unknown> extends Error {
  constructor(public status: number, public data: T, message?: string) { super(message); }
}
```

When your Spring backend defines `@ExceptionHandler` methods annotated with `@ResponseStatus`, SPIA
additionally emits per-endpoint typed error aliases so you can narrow the error type in `catch` blocks:

```typescript
// Generated alias — present when the endpoint's controller or a @ControllerAdvice declares an error handler
export type GetItemsError = ApiError<NotFoundError | BadRequestError>;
```

### Usage example

```typescript
import { createApi, ApiError } from './api-sdk';

const api = createApi('https://api.example.com');

try {
  const user = await api.user.getUserProfile(42);
} catch (err) {
  if (err instanceof ApiError) {
    console.error(`HTTP ${err.status}`, err.data);
  }
}
```

### How error types are collected

| Spring annotation | Effect |
|---|---|
| `@ControllerAdvice` / `@RestControllerAdvice` | SPIA scans all `@ExceptionHandler` methods and merges their return types as global error responses for every endpoint. |
| `@ExceptionHandler` inside a `@RestController` | Local error handlers override global ones for that controller's endpoints. |
| `@ResponseStatus(HttpStatus.X)` on an `@ExceptionHandler` method | Maps the return type to the given HTTP status code. |

## Configuring the client

When using `apiClient = "fetch"` (the default), the generated `createApi` accepts a `ClientOptions` object with two optional fields: `authInterceptor` and `retry`.

### Auth interceptor

`authInterceptor` is a function that receives the `RequestInit` object before each fetch call and returns a (potentially modified) `RequestInit`. This supports synchronous and asynchronous flows such as attaching an `Authorization` header or refreshing a token:

```typescript
import { createApi } from './generated/api-sdk';

const api = createApi({
  baseUrl: 'https://api.example.com',
  authInterceptor: async (request) => ({
    ...request,
    headers: {
      ...(request.headers as Record<string, string>),
      Authorization: `Bearer ${await getAccessToken()}`,
    },
  }),
});
```

### Retry

`retry` configures automatic retry for failed requests. By default retry is **off** (`maxAttempts: 0`). When enabled, the SDK retries on server errors (status >= 500) with a fixed backoff:

```typescript
import { createApi, ApiError } from './generated/api-sdk';

const api = createApi({
  baseUrl: 'https://api.example.com',
  retry: {
    maxAttempts: 3,      // retry up to 3 additional times
    backoffMs: 200,      // wait 200 ms between attempts
    retryOn: (status) => status >= 500,  // default — retries 503, not 400
  },
});
```

The retry logic catches `ApiError` specifically (not the base `Error` class) and inspects `error.status` to decide whether to retry:

- Status `503` (or any `>= 500`): retried up to `maxAttempts` times with `backoffMs` delay.
- Status `400` (or any `< 500`): immediately re-thrown — client errors are not retried.

### Combined example

```typescript
const api = createApi({
  baseUrl: 'https://api.example.com',
  authInterceptor: async (req) => ({
    ...req,
    headers: { ...(req.headers as Record<string, string>), Authorization: `Bearer ${token}` },
  }),
  retry: { maxAttempts: 2, backoffMs: 300 },
});
```
