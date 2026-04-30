# E2E Jackson Polymorphism Testbed

End-to-end testbed that consumes the published SPIA SDK as an external user would and validates polymorphism round-trips against a running Spring Boot backend.

## Why this exists

The monorepo's `app/` demo uses source-level dependencies on `processor/` and `gradle-plugin/`, which means it cannot catch bugs in the **published artifacts** — packaging, plugin resource files, `mavenLocal` resolution, etc. This testbed is an **independent Gradle build** that resolves SPIA from `mavenLocal()` only. If something works here, it works for an external consumer.

See `FINDINGS.md` for SDK/runtime issues this testbed has surfaced.

## Prerequisites

- JDK 21 (Zulu). Memory: JDK 25 breaks the embedded Kotlin compiler.
- Node 20+ and pnpm.
- One-time SDK publish: `./gradlew publishToMavenLocal` from the **repo root** (not `e2e/`).

## Run everything

```bash
e2e/run-tests.sh
```

This does: `publishToMavenLocal` → backend build (KSP emits TS) → backend bootRun → pnpm install + typecheck + vitest. All four steps must pass for the run to be green.

## Dev loop

Terminal 1 — backend stays running, regenerates TS on each restart:

```bash
e2e/dev-backend.sh
```

Terminal 2 — Vitest watch mode:

```bash
cd e2e/e2e-client && pnpm test:watch
```

After changing anything in `processor/` or `gradle-plugin/`: stop terminal 1, re-run `dev-backend.sh` (it re-publishes and rebuilds).

## Layout

```
e2e/
├── settings.gradle.kts        # independent Gradle build; pluginManagement → mavenLocal
├── gradle.properties          # version pins (must match root)
├── e2e-backend/               # Spring Boot module, applies io.github.lyutvs.spia
├── e2e-client/                # pnpm + Vitest, imports from src/generated/api-sdk.ts
├── scripts/wait-for-http.sh   # HTTP readiness poller used by run-tests.sh
├── run-tests.sh               # full CI pipeline
├── dev-backend.sh             # local dev: publish + boot, no vitest
├── FINDINGS.md                # SDK/runtime issues this testbed has surfaced
└── README.md                  # this file
```

The backend's `spia { outputPath = "../e2e-client/src/generated/api-sdk.ts" }` writes the generated TS straight into the test project. `src/generated/` is gitignored.

## Adding a new polymorphism case

1. Add the DTO under `e2e/e2e-backend/src/main/kotlin/io/spia/e2e/dto/<topic>/`.
2. Add an echo endpoint to `PolymorphicController.kt` (or `PolymorphicNestedController.kt` if the body type is a collection).
3. Add a fixtures endpoint to `FixturesController.kt`.
4. Run `cd e2e && ./gradlew :e2e-backend:build` and confirm `e2e/e2e-client/src/generated/api-sdk.ts` regenerated.
5. Add `e2e/e2e-client/tests/<topic>.test.ts` following the round-trip pattern from `tests/animals.test.ts`.
6. Run `cd e2e/e2e-client && pnpm test tests/<topic>.test.ts`.

## Known limitations (V1)

- Single Spring Boot version (4.0.5) and JDK (21).
- Single port (18080) — no parallel runs on the same host.
- WRAPPER_OBJECT and EXTERNAL_PROPERTY emission requires casts at the call site (see FINDINGS.md F3, F4).
- `Page<T>` polymorphic items are runtime-broken and tested via `it.fails(...)` (see FINDINGS.md F2).
- Not yet wired into CI; run locally before each release tag.

## Findings summary (current as of 2026-04-30)

| ID | Layer | Severity | Workaround |
|----|-------|----------|------------|
| F1 | SDK codegen | Strict-typecheck blocker | `lib: ["DOM"]` in tsconfig |
| F2 | Spring/Jackson runtime | Generic-wrapper bug | `it.fails()` on Page<T> tests |
| F3 | SDK codegen | EXTERNAL_PROPERTY emitted as PROPERTY | duplicate discriminator at call site |
| F4 | SDK codegen | WRAPPER_OBJECT emitted as PROPERTY | `as unknown as Message` cast |

See `FINDINGS.md` for details.
