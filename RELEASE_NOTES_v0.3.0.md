# Release Notes — v0.3.0

## Toolchain Breaking Changes

- **Kotlin** raised to **2.3.x** (minimum). Consumers must upgrade their Kotlin plugin.
- **KSP** raised to **2.3.x** (minimum). The `com.google.devtools.ksp` plugin version must match the Kotlin version (e.g., KSP `2.3.7` for Kotlin `2.3.21`).
- **Gradle** raised to **9.x** (minimum). The plugin is compiled against Gradle 9 APIs.

## Breaking Change — `apiClient` Default Flip

The `spia` plugin default `apiClient` changed from `"axios"` to `"fetch"`. Consumers who do not explicitly pin `apiClient` in their `spia { }` block will receive a fetch-based SDK.

To preserve the previous behavior, add `apiClient = "axios"` to your `spia { }` configuration.

The fetch template now emits `if (!res.ok) throw new Error(…)` before every response parse, aligning HTTP error semantics with axios behavior.

## Added

- README "Quick Start" section for the 3-line fetch-based setup.
- `ProcessorSmokeTest` coverage for the fetch branch.

## Internal / Infrastructure

- Publishing: `com.vanniktech.maven.publish` 0.30 → 0.36 (Central Portal by default).
- Test infra: `kctfork` 0.5 → 0.12, `junit-jupiter` 5 → 6, `typescript` 5.9 → 6.0.
- Demo module: Spring Boot 3.4 → 4.0.
- CI: tag-push release automation, Dependabot (6 ecosystems), CodeQL, `dependency-review-action`.
- Community files: issue/PR templates, `SECURITY.md`, branch protection on `main`.

## Migration Guide

1. Run a build. If the generated `api-sdk.ts` shows `createApi(baseUrl: string)` where you previously had `createApi(client: AxiosInstance)`, the default flip affected you.
2. Either migrate call sites to the fetch form, or pin `apiClient = "axios"` to restore the previous shape.
