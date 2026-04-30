# E2E Findings

This file accumulates SPIA SDK bugs and rough edges discovered while running the e2e testbed against published 0.4.x artifacts.

## F1 — Generated client fails strict typecheck under Node-only `fetch` typings

**Discovered in:** Task 5 (Animal sealed hierarchy round-trip)
**SDK version:** 0.4.1

**Symptom:** `tsc --noEmit` against the generated `api-sdk.ts` fails with:

```
src/generated/api-sdk.ts:69:13 - error TS2322: Type 'unknown' is not assignable to type '<RetType>[]'.
src/generated/api-sdk.ts:115:13 - error TS2322: Type 'unknown' is not assignable to type '<RetType>'.
```

**Cause:** The generator emits client methods of the form

```ts
async function someEndpoint(...): Promise<RetType> {
  const res = await fetch(...);
  return res.json();        // <-- Promise<unknown>, not Promise<RetType>
}
```

Under modern strict TS configs (`lib: ["ES2022"], types: ["node"]`), `Response.json()` returns `Promise<unknown>` (Node 20+ undici). Without an explicit cast or `<RetType>` parameterization, the bare `return res.json()` is rejected.

**Mitigations a consumer can take:**
- Add `"DOM"` to `tsconfig.compilerOptions.lib` (browser typings make `res.json()` return `Promise<any>` and the error vanishes — but pollutes test files with DOM globals).
- `exclude` the generated dir from typecheck (what this testbed does).
- Cast at every call site (defeats the purpose of the SDK).

**Suggested SDK fix:** In the generated client, change `return res.json();` to `return res.json() as Promise<RetType>;` (or refactor the helper that wraps `fetch`). Affects:
- Per-controller `<verb><Path>` functions where the return type is non-`void`.

**Workaround in this testbed:** `e2e/e2e-client/tsconfig.json` adds `"DOM"` to its `lib` array. Browser typings make `Response.json()` return `Promise<any>`, which lets the SPIA-generated bare `return res.json()` typecheck. Side effect: DOM globals (`window`, `document`, …) leak into test files, but our tests don't use them.

A previous attempt to use `"exclude": ["src/generated/**"]` did NOT work: TypeScript follows imports for *type-checking*, not just *type resolution*, so the generated file gets pulled back into the program by `import type { Animal } from '../src/generated/api-sdk'` in the test files.

## F2 — Polymorphic discriminator erased through user-defined generic wrappers

**Discovered in:** Task 6 (Cases 2/3/4 — Animal nested in collections)
**Component:** Spring/Jackson/Kotlin runtime — NOT the SPIA generator. SDK codegen is correct here; the bug is at request/response binding time.

**Symptom:** When a `@JsonTypeInfo(NAME, "type")` sealed hierarchy (e.g. `Animal`) is nested inside a user-defined Kotlin generic wrapper (`Page<T>`), the discriminator `type` field is lost in BOTH directions.

- **Outbound (serialize):** `GET /poly/animals/page/fixtures` returns:
  ```json
  {"items":[{"name":"Rex","breed":"Husky"}, ...], "page":0, "total":3}
  ```
  Note the missing `"type":"dog"` etc.

- **Inbound (deserialize):** `POST /poly/animals/page` with a well-formed body containing `{"items":[{"type":"dog",...}]}` returns 400 with backend log:
  ```
  JSON parse error: Could not resolve subtype of Animal: missing type id property 'type' (for POJO property 'items')
  ```

**Scope of damage:**
- `List<Animal>` and `Map<String, Animal>` work correctly in both directions (Cases 2 and 3 are green).
- Only USER-DEFINED generic wrappers like `Page<T>` lose the discriminator. Spring's `org.springframework.data.domain.Page<T>` may behave differently — not tested here.
- Likely affects any DTO of the form `data class X<T>(val item: T, ...)` or `data class X<T>(val items: List<T>, ...)` when `T` is polymorphic.

**Why it happens (background):** Kotlin's reified generics don't survive into Jackson's `JavaType` resolution chain when the property is declared via a user-defined parameterized class without `@JsonTypeInfo` propagation. Jackson sees `List<T>` where `T` is erased and falls back to default serialization, dropping the type tag.

**Suggested fixes (in priority order):**
1. **Document the limitation prominently in SDK docs:** "Polymorphic types must not be nested inside user-defined generic wrappers; use concrete sub-classes instead." Lowest cost, immediate.
2. **Spring/Jackson configuration knob** to propagate `@JsonTypeInfo` through generic property type resolution. Requires research — may exist as a `MapperFeature` or `TypeFactory` customization.
3. **Discourage in the SPIA processor** by emitting a KSP warning when a controller method's return type or `@RequestBody` has a user-defined generic class containing a `@JsonTypeInfo` member.

**Workaround in this testbed:** Case 4's two tests use Vitest's `it.fails(...)` so they exercise the broken path and succeed when the bug is present. If/when the runtime is fixed, those tests will fail and signal that `it.fails` should be removed. See `tests/nested-collection.test.ts`.

**Implications for downstream tasks:**
- Task 9 (Case 7 — `Envelope` containing two polymorphic hierarchies) is a non-generic class and should NOT hit this. But verify carefully if any future case uses a generic envelope.
- Real-world consumers using a `Page<T>`-style pagination wrapper around polymorphic items WILL hit this in production.

## F3 — `EXTERNAL_PROPERTY` mode is emitted identically to `PROPERTY` mode

**Discovered in:** Task 7 (Case 5 — `PaymentEvent` with EXTERNAL_PROPERTY discriminator)
**Component:** SPIA generator
**SDK version:** 0.4.1

**Symptom:** Given a Kotlin sealed class annotated with `@JsonTypeInfo(use = NAME, include = EXTERNAL_PROPERTY, property = "kind")`, SPIA emits the TS type as

```ts
export type PaymentPayload = ({ kind: 'bank' } & BankTransferPayload) | ({ kind: 'card' } & CardPayload);
```

This is identical to what SPIA emits for `include = PROPERTY` (the default). The `EXTERNAL_PROPERTY` semantic — that the discriminator lives OUTSIDE the polymorphic value, as a sibling on the parent — is not represented in the generated TS.

**Why it still works (accidentally):** Jackson's runtime behavior with EXTERNAL_PROPERTY when the parent has a same-named regular field (here, `PaymentEvent.kind: String`) is to consume the parent field as a normal value AND require the discriminator inside the polymorphic value. The wire format ends up requiring `kind` at BOTH parent level AND inside `payload`. SPIA's PROPERTY-style emission accidentally matches this wire shape. So round-trip tests pass — but only because of a Jackson quirk, not because the SDK understands EXTERNAL_PROPERTY.

**Concrete demonstration:** Sending `{kind: "card", payload: {last4, brand}, amountCents}` (no `kind` inside payload, matching the documented EXTERNAL_PROPERTY contract) yields HTTP 400 with `missing type id property 'kind' (for POJO property 'payload')`. Sending `{kind: "card", payload: {kind: "card", last4, brand}, amountCents}` (duplicate `kind`) succeeds. The SDK's emitted type forces consumers into the second shape.

**Suggested SDK fix (in priority order):**
1. **Detect `include = EXTERNAL_PROPERTY` in KSP** and emit a TS type without the inlined discriminator on the union members:
   ```ts
   export type PaymentPayload = BankTransferPayload | CardPayload;
   export interface PaymentEvent {
     kind: 'bank' | 'card';   // discriminator at parent
     payload: PaymentPayload;
     amountCents: number;
   }
   ```
   Pair with documentation noting that the consumer must keep parent `kind` and payload type in sync.
2. **Alternative:** explicitly document in the SDK that EXTERNAL_PROPERTY is NOT supported and emit a KSP warning when it's encountered, recommending PROPERTY mode instead.

**Workaround in this testbed:** Test for Case 5 constructs payloads with the discriminator inlined inside the payload object (matching SPIA's emission and the Jackson wire format). A header comment in `tests/external-property.test.ts` documents the quirk so future readers don't trip on it.

**Implications:**
- Real-world consumers reading the SDK without runtime testing might assume `payload: PaymentPayload` (discriminated union) means they only need to set `payload`'s discriminator — not parent's. They'll get 400s in production.
- A future SDK fix that properly differentiates EXTERNAL_PROPERTY would be a BREAKING CHANGE for any consumer who adapted to the current behavior.

## F4 — `WRAPPER_OBJECT` mode is also emitted identically to `PROPERTY`

**Discovered in:** Task 8 (Case 6 — `Message` with WRAPPER_OBJECT discriminator)
**Component:** SPIA generator
**SDK version:** 0.4.1
**Related:** F3 (same root cause)

**Symptom:** Given a Kotlin sealed class annotated with `@JsonTypeInfo(use = NAME, include = WRAPPER_OBJECT)`, SPIA emits:

```ts
export type Message = ({ type: 'image' } & ImageMessage) | ({ type: 'text' } & TextMessage);
```

But the actual Jackson wire format for WRAPPER_OBJECT is `{"text": {"body": "hello"}}` — the subtype name is the OUTER object key. The emitted TS type is structurally wrong; consumers must cast (`as unknown as Message`) to bypass strict typing.

**Likely root cause:** Combined with F3, SPIA appears to be ignoring the `include` parameter of `@JsonTypeInfo` entirely — it always emits the PROPERTY-mode shape regardless of the mode. F4 may also indicate the `property` name isn't being read (the default `@type` would be expected; SPIA emits `type`, suggesting either a hardcoded default or a different path through KSP).

**Suggested SDK fix:** In the KSP processor, read `@JsonTypeInfo` properly:
- `include = PROPERTY` (default) → emit `({ <prop>: 'name' } & Subtype) | ...` (current behavior)
- `include = EXTERNAL_PROPERTY` → emit subtype-only union; require parent class to declare the discriminator separately (see F3)
- `include = WRAPPER_OBJECT` → emit `{ <subtypeName1>: Subtype1 } | { <subtypeName2>: Subtype2 } | ...`
- `include = WRAPPER_ARRAY` → similarly distinct
- Read the explicit `property = "..."` value rather than always using `type`

This is a bigger generator change than F3 alone, but F3 + F4 are the same root cause and should be fixed together.

**Workaround in this testbed:** Cast at the test call site — `as unknown as Message`. Documented in `tests/wrapper-object.test.ts`. Round-trip tests still validate runtime behavior; only typing is wrong.

**Implications:**
- Same severity as F3: real consumers will get HTTP 400 if they trust the SDK type.
- Fixing F3 + F4 together would be a meaningful breaking change for anyone who adapted to current behavior. Recommend bundling with a major version bump.
