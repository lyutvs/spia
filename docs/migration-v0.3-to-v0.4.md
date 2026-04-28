# Migration Guide: v0.3 → v0.4.0

This guide covers all changes you need to make when upgrading SPIA from v0.3.x to v0.4.0.

---

## DSL changes

### `ClientOptions` fields added (opt-in, non-breaking)

The `ClientOptions` object accepted by `createApi(options?)` (fetch mode) now supports two new optional fields:

- **`authInterceptor`** — a function `(request: RequestInit) => RequestInit | Promise<RequestInit>` called before every fetch. Use it to attach `Authorization` headers or refresh tokens.
- **`retry`** — an object `{ maxAttempts: number; backoffMs: number; retryOn?: (status: number) => boolean }` that configures automatic retry with fixed backoff for server errors (status >= 500 by default).

Both fields are opt-in. Existing clients that call `createApi()` with no arguments or with only `{ baseUrl }` require no changes.

### `splitByController` DSL option added

`spia { splitByController = true }` now emits one `<slug>.api.ts` file per controller, a `_shared.ts` containing all DTOs/enums, and an `index.ts` barrel. Default is `false` (single-file mode unchanged).

### `npmPackage` DSL block added

`spia { npmPackage { name.set("@your-org/api-sdk") } }` enables the `spiaPackNpm` Gradle task that assembles a publishable npm package under `build/npm/`. No action needed if you do not use npm packaging.

---

## Generated SDK shape changes

### `createApi` signature changed (BREAKING)

**Before (v0.3):**
```typescript
// fetch mode
export function createApi(baseUrl: string): { ... }
```

**After (v0.4):**
```typescript
export interface ClientOptions {
  baseUrl?: string;
  authInterceptor?: (request: RequestInit) => RequestInit | Promise<RequestInit>;
  retry?: { maxAttempts: number; backoffMs?: number; retryOn?: (status: number) => boolean };
}
export function createApi(options?: ClientOptions): { ... }
```

**Migration:** Replace every `createApi("/api")` call site with `createApi({ baseUrl: "/api" })`. If you already configured `baseUrl` via the Gradle DSL (`spia { clientOptions { baseUrl = "..." } }`), you can call `createApi()` with no arguments.

### Kotlin `value class` emitted as branded type (BREAKING)

**Before (v0.3):** A `value class UserId(val raw: String)` property was flattened to `string` in the generated TypeScript interface.

**After (v0.4):**
```typescript
export type UserId = string & { readonly __brand: 'UserId' };
export const UserId = (raw: string): UserId => raw as UserId;
```

**Migration:** Any frontend code that previously assigned a plain `string` where a value-class type is expected will now fail under `tsc --strict`. Wrap raw values with the generated constructor helper: `UserId('abc-123')`.

### `ApiError<T>` class always emitted

Every generated SDK now includes `export class ApiError<T = unknown> extends Error { ... }`. The fetch template throws `ApiError` on non-2xx responses instead of a plain `Error`. Update any `catch` blocks that narrowed on `instanceof Error` to use `instanceof ApiError` for typed error inspection.

### `sealed class` discriminated union support

Kotlin `sealed class` hierarchies annotated with `@JsonTypeInfo(use = NAME, property = "type")` are now emitted as TypeScript discriminated unions:

```typescript
type Shape = ({ kind: 'circle' } & Circle) | ({ kind: 'rectangle' } & Rectangle);
```

Previously these required a manual workaround with nullable fields. Remove those workarounds.

### `Pageable` parameters expanded inline

`org.springframework.data.domain.Pageable` method parameters are now expanded to three inline query fields in the generated signature:

```typescript
// Before: Pageable was omitted or treated as unknown
// After:
listItems(page?: number, size?: number, sort?: string): Promise<Page<Item>>
```

No action required unless you had a manual workaround in place — remove it.

### SSE / reactive endpoints

`Flux<ServerSentEvent<T>>` return types now emit as `AsyncIterable<T>` in the generated SDK. `ResponseEntity<Resource>` (file download endpoints) emits as `Promise<Blob>`.

---

## Annotations newly recognized

| Annotation | Effect |
|---|---|
| `@NotNull`, `@Size`, `@Min`, `@Max`, `@Pattern`, `@NotBlank`, `@Email` (jakarta.validation.constraints) | Emitted as JSDoc tags: `@minLength`, `@maxLength`, `@minimum`, `@maximum`, `@pattern`, `@format email` |
| `@JsonProperty("field_name")` | TypeScript property key is renamed to match the JSON wire name |
| `@JsonAlias("a", "b")` | JSDoc `@alias a, b` comment emitted above the field |
| `@JsonInclude(JsonInclude.Include.NON_NULL)` | Nullable field marked optional (`field?: T \| null`) instead of required-but-nullable |
| `@CookieValue` | Cookie parameters collected into `cookies?: Record<string, string>`; SDK emits a `Cookie: k=v` header |
| `@MatrixVariable` | Treated as a query-string parameter |
| `@ModelAttribute` | DTO fields flattened into individual query-string parameters |
| `@RequestAttribute` | Excluded from the generated signature (server-side only); KSP warning emitted |
| Java `@RestController` / `@GetMapping` / etc. | Recognized by FQN; plain Java POJO fields via JavaBeans getters |
| `@ControllerAdvice` / `@RestControllerAdvice` + `@ExceptionHandler` + `@ResponseStatus` | Typed `ApiError` aliases generated per endpoint |

---

## Breaking changes

1. **`createApi` signature** — from `createApi(baseUrl: string)` to `createApi(options?: ClientOptions)`. See "Generated SDK shape changes" above.
2. **Branded value classes** — `value class` types are no longer flattened to primitives. Frontend code must use the generated constructor helper. See "Generated SDK shape changes" above.
3. **`ApiError` thrown instead of plain `Error`** — `catch (err)` blocks that test `instanceof Error` still match, but to access `err.status` and `err.data` you must narrow to `instanceof ApiError`.

---

## Removed workarounds

The following workarounds from v0.2.0 / v0.3.0 are no longer necessary and should be cleaned up:

- **Sealed class nullable-field DTO workaround** — replace with the generated discriminated union type directly.
- **Pageable manual parameter listing** — remove hand-written `page?: number; size?: number; sort?: string` shims; SPIA now generates them automatically.
- **`@JsonProperty` name mismatch workaround** — remove any Kotlin property aliases or manual field-name adjustments; `@JsonProperty` is now recognized.
- **`@JsonInclude(NON_NULL)` optional marking** — remove TypeScript-side `?` hacks; SPIA now emits the correct optional modifier.
- **Bean Validation TypeScript-side duplication** — remove manual TypeScript validation schemas that duplicated `@NotNull` / `@Size` / etc.; the JSDoc tags from SPIA provide that information to IDE tooling.
