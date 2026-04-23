# Security Policy

## Supported Versions

| Version  | Supported          |
|----------|--------------------|
| 0.2.x    | :white_check_mark: |
| < 0.2    | :x:                |

## Reporting a Vulnerability

Please do **not** open a public issue for security vulnerabilities.

Use GitHub's private vulnerability reporting:
<https://github.com/lyutvs/spia/security/advisories/new>

Include:

- A description of the vulnerability
- Minimal reproduction (Kotlin controller + `spia { ... }` config that triggers it)
- Affected SPIA versions
- Your assessment of the severity and blast radius

## What to expect

- **Acknowledgement**: within 72 hours
- **Initial assessment**: within 7 days
- **Fix timeline**: depends on severity; critical issues patched within 30 days when feasible
- **Credit**: with your permission, reporters are credited in the release notes

## Threat models in scope

SPIA is a compile-time KSP processor — there is **no runtime component** deployed on the consumer's server. The primary threat models this policy covers:

1. **Supply chain** — malicious code injected into published Maven Central artifacts. Mitigated by GPG-signed releases (`io.github.lyutvs:gradle-plugin`, `io.github.lyutvs:processor`) and a deterministic build pipeline with staged review on Central Portal before release.
2. **Generated code correctness** — the generated TypeScript SDK incorrectly handles untrusted input, enabling injection or data leakage in the consumer's frontend (e.g., improperly escaped path variables, header-injection, prototype-pollution through generated types).
3. **Build-time processor behavior** — a malicious `@RestController` or DTO on the consumer's codebase triggering KSP processor behavior that reads/leaks environment secrets or writes outside the configured `outputPath`.

Reports on any of these threat models are in scope. Issues unrelated to SPIA's behavior (e.g., a Spring Boot CVE, a Kotlin compiler bug) should be reported to their respective projects.
