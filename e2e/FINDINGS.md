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
