# Releasing SPIA

This document walks through everything needed to cut a release of
`io.github.lyutvs:gradle-plugin` and `io.github.lyutvs:processor` to Maven Central.

The Gradle build config (tasks 22 & 23) is version-independent — the release
procedure below can be re-used for every subsequent version without
re-configuring the build.

## Prerequisites

| Item | Who | Notes |
|------|-----|-------|
| Sonatype Central Portal account | You | https://central.sonatype.com/account |
| `io.github.lyutvs` namespace verified | You | Standard GitHub verification — see below |
| GPG key pair | You | See GPG section below |
| Node.js 18+, JDK 21, git | You | Already needed for day-to-day builds |

---

## 1. Sonatype Central Portal — namespace verification

Maven Central (via Sonatype Central Portal) requires you to prove ownership of
the group-ID you want to publish under. We publish under
**`io.github.lyutvs`** — the `io.github.<GitHub-user>` form is the least-
friction verification route because it's tied to an existing GitHub account
without needing a separate domain.

### Procedure (GitHub username verification)

1. Log into https://central.sonatype.com and navigate to
   **Namespaces → Add Namespace**.
2. Enter `io.github.lyutvs`. Sonatype recognises the `io.github.` prefix and
   matches it against the GitHub account you signed up with.
3. If the portal asks for a challenge token, it will instruct you to create a
   public repo on `github.com/lyutvs` whose name is the token. Do that (the
   repo can be empty), then click **Verify** — usually near-instant.

### Why not `io.spia`?

Sonatype requires `io.<name>` groups to be backed by a domain you own
(`spia.io` in this case). Without the domain, DNS TXT verification is
impossible. The `io.github.<user>` fallback doesn't require a domain and is
fully supported by Central Portal, the Vanniktech publish plugin, and
Maven Central itself. If `spia.io` or a `spia` GitHub organisation is
acquired later, a future v0.2+ release can switch the coordinate (keeping
v0.1.0 published under the current group for continuity).

### After verification

Record the verification date so the pre-release gate in task 27 can confirm
it:

| Date (UTC) | Verified group | Verifier |
|------------|----------------|----------|
| 2026-04-22 | io.github.lyutvs Verified | lyutvs (GitHub username challenge repo `wjfckl2wil`) |

**Current status:** `io.github.lyutvs` Verified on 2026-04-22 by GitHub username
challenge (`github.com/lyutvs/wjfckl2wil`, deletable once Sonatype records the
verification).

## 2. Credentials

Sonatype Central Portal uses a **publisher token** (different from the old
OSSRH credentials). After namespace verification:

1. In Central Portal, go to **View Account → Generate User Token**.
2. Store the returned `username` + `password` in
   `~/.gradle/gradle.properties` — never commit:
   ```
   mavenCentralUsername=<portal-user-token>
   mavenCentralPassword=<portal-user-password>
   ```

## 3. GPG signing

See the **GPG** section below (task 21).

## 4. mavenLocal dry-run

Before shipping, validate the release bundle against a real, separate Gradle
build that resolves the plugin through `mavenLocal()` rather than a project
reference. The sample at `docs/samples/mavenlocal-consumer/` is intentionally
**not** included in the root `settings.gradle.kts` so that it exercises the
exact plugin resolution path an external consumer takes.

```bash
# Publish both artifacts locally:
./gradlew :gradle-plugin:publishToMavenLocal :processor:publishToMavenLocal

# Build the sample consumer against mavenLocal:
./gradlew -p docs/samples/mavenlocal-consumer clean build
```

A green build + a populated `docs/samples/mavenlocal-consumer/frontend/api.ts`
means the release bundle is self-contained and ready to upload to Central.

## 5. Cutting a release (tag-push, automated)

Releases are produced by `.github/workflows/release.yml` on tag push. The
maintainer's local workflow is:

```bash
# 1. Move `## [Unreleased]` items in CHANGELOG.md to a new
#    `## [X.Y.Z] - YYYY-MM-DD` section. Edit `gradle.properties`:
#    version=X.Y.Z. Commit both.
git add CHANGELOG.md gradle.properties
git commit -m "chore(release): prepare vX.Y.Z"
git push origin main

# 2. Tag the commit and push the tag.
git tag -a vX.Y.Z -m "Release vX.Y.Z"
git push origin vX.Y.Z
```

The workflow then runs three jobs:

1. **verify** — checks that the tag's `X.Y.Z` matches `gradle.properties`
   `version=` and that the tagged commit is on `main`. Fails fast on
   mismatch.
2. **build-and-stage** — runs `./gradlew build`, the mavenLocal dry-run
   preflight against `docs/samples/mavenlocal-consumer`, then
   `publishToMavenCentral` for both artifacts (staged on Central Portal,
   **not** auto-released).
3. **draft-release** — creates a GitHub Release marked as **draft** with
   the `## [X.Y.Z]` section of `CHANGELOG.md` as the body.

### Required secrets

| Secret | Value | Source |
|---|---|---|
| `MAVEN_CENTRAL_USERNAME` | Portal User Token username | central.sonatype.com → View Account → Generate User Token |
| `MAVEN_CENTRAL_PASSWORD` | Portal User Token password | same screen |
| `SIGNING_IN_MEMORY_KEY` | Armored GPG private key (entire `-----BEGIN PGP PRIVATE KEY BLOCK-----` through `-----END PGP PRIVATE KEY BLOCK-----`) | `gpg --export-secret-keys --armor <KEY_ID>` |
| `SIGNING_IN_MEMORY_KEY_PASSWORD` | GPG key passphrase | — |

For the armored GPG key, pipe the exported file directly to avoid
clipboard line-ending corruption:

```bash
gpg --export-secret-keys --armor <KEY_ID> | gh secret set SIGNING_IN_MEMORY_KEY --repo lyutvs/spia
```

## 6. Post-release review (two manual gates)

After the workflow completes:

1. **Central Portal** — open https://central.sonatype.com →
   *Deployments* → locate the staged deployment for your version →
   review signatures, javadoc, POM → click **Release**. Maven Central
   proper reflects the release within 10–30 minutes.
2. **GitHub Release** — open
   https://github.com/lyutvs/spia/releases → locate the draft for
   `vX.Y.Z` → review the auto-extracted notes → check *Set as a
   pre-release* if the tag is an alpha/rc → click **Publish release**.

If something looks wrong during post-release review, drop the staged
deployment on Central Portal and delete the tag locally + remotely,
then re-tag after fixing the issue.

## 7. Fallback: local-only release (when CI is unavailable)

```bash
# 1. Clean build + tsc --strict gate
JAVA_HOME=... ./gradlew clean build

# 2. Publish both artifacts to Central Portal staging
JAVA_HOME=... ./gradlew \
  :gradle-plugin:publishAndReleaseToMavenCentral \
  :processor:publishAndReleaseToMavenCentral

# 3. Tag and push
git tag -a v0.1.0 -m "Release v0.1.0"
git push origin v0.1.0
git push origin main
```

Central Portal performs its own validation (signatures, javadoc, pom
completeness) before publishing to Maven Central proper — that can
take 10–30 minutes. Check the portal UI for progress.

This path requires the maintainer to have the GPG key and Portal
credentials configured locally (see GPG section below). Prefer the
tag-push flow in section 5 whenever possible.

---

## GPG signing

Each artifact uploaded to Maven Central must be signed with your GPG key.

### Generate a key pair (once)

```bash
gpg --full-generate-key   # choose RSA 4096, 2-year expiry is fine
gpg --list-secret-keys --keyid-format=long
# Note the long key id (the hex string after `sec rsa4096/`).
```

### Publish the public key

```bash
gpg --keyserver keys.openpgp.org --send-keys <KEY_ID>
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```

### Point Gradle at your key

Add to `~/.gradle/gradle.properties`:

```
signing.keyId=<last 8 hex chars of your key id>
signing.password=<your key passphrase>
signing.secretKeyRingFile=<absolute path to secring.gpg>
```

OR export and use in-memory for CI:

```bash
gpg --export-secret-keys --armor <KEY_ID> > ~/.gnupg/private.key
# Then set env vars:
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(cat ~/.gnupg/private.key)"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="<passphrase>"
```

The `com.vanniktech.maven.publish` plugin in our `gradle-plugin` and
`processor` modules picks either flavour automatically (see task 22/23).

### Verification

Once credentials + signing are in place, local verification:

```bash
./gradlew :gradle-plugin:publishToMavenLocal :processor:publishToMavenLocal
ls ~/.m2/repository/io/spia/**/*.asc   # should list .asc files alongside .jar
```

If you don't have signing configured yet, `publishToMavenLocal` still works —
the Vanniktech config gates `signAllPublications()` behind a property check
so you can dry-run without keys.
