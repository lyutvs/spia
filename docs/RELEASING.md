# Releasing SPIA

This document walks through everything needed to cut a release of
`io.spia:gradle-plugin` and `io.spia:processor` to Maven Central.

The Gradle build config (tasks 22 & 23) is version-independent — the release
procedure below can be re-used for every subsequent version without
re-configuring the build.

## Prerequisites

| Item | Who | Notes |
|------|-----|-------|
| Sonatype Central Portal account | You | https://central.sonatype.com/account |
| `io.spia` namespace verified | You | Via GitHub or DNS TXT — see below |
| GPG key pair | You | See GPG section below |
| Node.js 18+, JDK 21, git | You | Already needed for day-to-day builds |

---

## 1. Sonatype Central Portal — namespace verification

Maven Central (via Sonatype Central Portal) requires you to prove ownership of
the group-ID you want to publish under. We've chosen `io.spia` as the group.

### Option A — GitHub verification (recommended)

This works if you have a GitHub organisation or user named `spia`, or you can
satisfy the challenge with a verification repository.

1. Log into https://central.sonatype.com and navigate to
   **Namespaces → Add Namespace**.
2. Enter `io.github.<username>` to use GitHub namespace, OR enter `io.spia`
   and choose GitHub verification.
3. Portal returns a verification token and instructs you to create a public
   repo on GitHub whose name matches that token.
4. Create the repo (can be empty), wait ~1 minute, click **Verify**.

### Option B — DNS TXT record

Use this if you own the `spia.io` domain:

1. Same **Add Namespace** flow, enter `io.spia`, choose DNS verification.
2. Portal returns a token. Add a TXT record at `_sonatype.spia.io` with the
   token as its value.
3. Wait for DNS propagation, click **Verify**.

### Fallback — `io.github.<username>`

If neither verification route is accessible right now, fall back to
`io.github.<user>` and update:

- `gradle.properties` → `group=io.github.<user>`
- `gradle-plugin/gradle.properties` → `group=io.github.<user>`
- `gradle-plugin/build.gradle.kts` → the `gradlePlugin` block's plugin id
  **may stay `io.spia`** — the plugin id is not tied to the group coordinate.
  Only the Maven coordinate changes.
- Regenerate the SDK to ensure no hard-coded group reference elsewhere.

### After verification

Record verification date + chosen group in the table below so the pre-release
check in task 27 can grep for it:

| Date (UTC) | Verified group | Verifier |
|------------|----------------|----------|
| _TBD_ | io.spia Verified | _TBD_ |

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

See `docs/samples/mavenlocal-consumer/` (task 24). Every release MUST be
validated by building this consumer against the local artifact before
pushing to Central.

## 5. Actual release

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
completeness) before publishing to Maven Central proper — that can take
10–30 minutes. Check the portal UI for progress.

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
