# mavenLocal dry-run consumer

A tiny standalone Gradle project that consumes the SPIA plugin **from your
local Maven repository** (`~/.m2/repository`) rather than from source. This
catches problems that only surface after a real publish — POM completeness,
plugin marker coordinates, sources/javadoc jar packaging, signing order, etc.

Intentionally NOT included in the root `settings.gradle.kts`. It exists as
its own Gradle project so it exercises the same code path as an external
user who adds `id("io.github.lyutvs.spia") version "x.y.z"` to their own build.

## Procedure

From the spia repo root:

```bash
# 1. Publish both artifacts to your local Maven cache.
JAVA_HOME=... ./gradlew \
  :gradle-plugin:publishToMavenLocal \
  :processor:publishToMavenLocal

# 2. Build the consumer sample against those local artifacts.
cd docs/samples/mavenlocal-consumer
JAVA_HOME=... ./gradlew build
```

If step 2 succeeds and `frontend/api.ts` is generated with a `createApi`
export, the release bundle is ready to upload to Maven Central (per
`docs/RELEASING.md`).

## Why it's separated from the main build

- The main build uses `ksp(project(":processor"))` — direct project
  dependency, no Maven resolution involved.
- This sample intentionally resolves `io.github.lyutvs:processor` via the plugin's
  auto-add path, so a broken POM or missing artifact surfaces here rather
  than at the Central Portal staging check (which is much slower to iterate
  against).
