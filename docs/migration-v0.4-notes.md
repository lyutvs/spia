# v0.3 → v0.4 Migration Notes (working draft)

각 feature task 가 자기 변경사항을 한 줄씩 append 한다. task 24 가 종합본 작성.

## DSL changes

- `ClientOptions` (fetch mode) gains two new optional fields: `authInterceptor` for injecting auth headers before each request, and `retry` for configuring automatic retry with backoff on server errors (status >= 500). Both are opt-in; existing clients require no changes.

## Generated SDK shape changes

- Kotlin `sealed class` annotated with `@JsonTypeInfo(use=NAME, property="…")` is now emitted as a TypeScript discriminated union (`type Shape = ({ kind: 'circle' } & Circle) | …`) instead of requiring a manual nullable-field DTO workaround.

## Annotations newly recognized

- `jakarta.validation.constraints.{NotNull,Size,Min,Max,Pattern,NotBlank,Email}` — propagated to TS as JSDoc tags (`@minLength`, `@maxLength`, `@minimum`, `@maximum`, `@pattern`, `@format email`).

## Java support

- Java `@RestController` classes are now processed (minimum support). Plain Java POJO fields are discovered via JavaBeans getter methods (`getXxx()` → `xxx`).
- **Known issue (P-13):** Lombok-generated getters are invisible to KSP at compile time. Lombok POJOs are emitted as empty TypeScript interfaces. Use plain Java POJOs with explicit getters, or Kotlin data classes.

## Breaking changes
