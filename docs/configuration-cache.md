# Gradle Configuration Cache Compatibility

SPIA supports the Gradle Configuration Cache (CC) fully when using
**Gradle 9.0 or newer** with the KSP version pinned in `gradle.properties`.

## Supported versions

| Tool | Minimum version | Notes |
|---|---|---|
| Gradle | 9.0 | Configuration Cache became stable in Gradle 9. The project uses 9.4.1 (see `gradle-wrapper.properties`). |
| KSP | 2.x (e.g. `2.3.7`) | The `kspVersion` in `gradle.properties` must match the Kotlin version. CC is not supported with KSP 1.x. |
| Kotlin | 2.x | KSP 2.x requires a matching Kotlin 2.x plugin. |

## Verified behaviour

Running `:app:kspKotlin --configuration-cache` was verified against Gradle 9.4.1 + KSP 2.3.7:

- **First run** — "Calculating task graph as no cached configuration is available" appears, then "Configuration cache entry stored."
- **Second run** — "Reusing configuration cache" appears and all KSP tasks are `UP-TO-DATE`, completing in ~2 s instead of ~12 s.

No warnings or incompatibility errors were produced.

## How the processor handles multiple KSP rounds

`SpiaProcessor` contains a `processed = false` guard at line 21:

```kotlin
private var processed = false

override fun process(resolver: Resolver): List<KSAnnotated> {
    if (processed) return emptyList()
    processed = true
    // ...
}
```

This flag makes the processor idempotent across KSP's multi-round execution — it exits early on every round after the first, preventing duplicate output. **Do not remove this guard.**

## Known limitations

### outputPath external-change tracking

When `spia.outputPath` is set to a path outside the project's `build/` directory, Gradle's incremental build does not automatically detect that the output file has been modified externally (e.g. deleted by the developer or overwritten by another tool). In that case, run `./gradlew :app:kspKotlin --rerun-tasks` to force re-generation.

Because the generated file is written with `java.io.File` rather than KSP's `CodeGenerator`, Gradle does not register it as a tracked output — this is a deliberate design choice that allows writing to arbitrary paths outside the build directory.

### KSPLogger is not a task input

`KSPLogger` is injected by the KSP runtime and is not wired as a Gradle task input. Changing log verbosity does not invalidate the configuration cache.

### Incremental KSP mode not used

SPIA does not opt in to KSP's own incremental processing (`@IncrementalHelper` / `KSPLogger.warn`-based dirty-set tracking). All source files are rescanned on every KSP invocation. Incremental KSP support is not on the current roadmap.

### Gradle versions below 9.0

Gradle 8.x has Configuration Cache in incubating/preview status. SPIA is not tested against Gradle 8.x CC mode and may emit warnings about unsupported API usage. Upgrade to Gradle 9.0+ for stable CC support.

## Troubleshooting

### Locating the CC report

When a configuration cache problem is detected, Gradle writes a human-readable HTML report to:

```
build/reports/configuration-cache/<hash>/configuration-cache-report.html
```

Open that file in a browser to see which task or plugin triggered the incompatibility and the full stack trace.

### Demoting errors to warnings

During migration, you can make CC problems non-fatal:

```bash
./gradlew build --configuration-cache --configuration-cache-problems=warn
```

This lets the build proceed and prints a summary of all issues at the end.

### Forcing cache invalidation

If a stale cache entry causes unexpected behaviour, delete it with:

```bash
rm -rf .gradle/configuration-cache
```

Then re-run the build to create a fresh entry.

### Re-running tasks without cache eviction

To force all tasks to re-execute while keeping the configuration cached (useful for testing that the cached configuration is correct):

```bash
./gradlew :app:kspKotlin --configuration-cache --rerun-tasks
```
