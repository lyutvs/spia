plugins {
    java
    kotlin("jvm")
    kotlin("plugin.spring")
    id("com.google.devtools.ksp")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("io.github.lyutvs.spia")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    // Local multi-project build uses the project reference directly — the io.spia
    // plugin's auto-add detects this and skips injecting the Maven coord.
    // External consumers don't need this line; the plugin adds the processor for them.
    ksp(project(":processor"))

    // Dependency stubs reserved for upcoming tasks — uncomment when the task lands:
    implementation("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("org.springframework.boot:spring-boot-starter-webflux")       // task 17 (Streaming/SSE)
    // implementation("com.fasterxml.jackson.module:jackson-module-kotlin")      // task 13 (Jackson customization)
}

kotlin {
    jvmToolchain(21)
}

spia {
    outputPath = "frontend/src/generated/api-sdk.ts"
    enumStyle = "union"
    longType = "number"
    schemaOutput = "zod"
    openApiOutput = "3.1"
    clientOptions {
        baseUrl = "/api"
    }
    // Bundle splitting (task 18): when enabled (set splitByController to true),
    // the processor emits one `<slug>.api.ts` file per controller plus an
    // `index.ts` barrel and a `_shared.ts` module. Bundlers can then
    // tree-shake unused controllers.
}

/* ───────────── Frontend typecheck gate ─────────────
 * The generated SDK must compile under `tsc --strict`. We wire `npm install`
 * and `npm run typecheck:strict` into the Gradle build so a fresh clone of
 * the repo gets the full release gate just by running `./gradlew build`.
 */
tasks.register<Exec>("npmInstall") {
    workingDir = file("frontend")
    commandLine("npm", "install", "--no-audit", "--no-fund")
    inputs.file("frontend/package.json")
    inputs.file("frontend/package-lock.json").optional()
    outputs.dir("frontend/node_modules")
    onlyIf {
        val nodeModules = file("frontend/node_modules")
        if (!nodeModules.exists()) return@onlyIf true
        val pkg = file("frontend/package.json")
        pkg.lastModified() > nodeModules.lastModified()
    }
    doFirst {
        val probe = providers.exec {
            commandLine("sh", "-c", "command -v npm || true")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim()
        if (probe.isEmpty()) {
            throw GradleException("npm not found in PATH. Install Node.js 18+ before building, or skip this task.")
        }
    }
}

tasks.register<Exec>("frontendTypecheck") {
    workingDir = file("frontend")
    commandLine("npm", "run", "typecheck:strict")
    dependsOn("npmInstall", "kspKotlin")
    inputs.dir("frontend/src")
    inputs.file("frontend/tsconfig.json")
    inputs.file("frontend/package.json")
    outputs.file("frontend/.typecheck-ok")
    doLast { file("frontend/.typecheck-ok").writeText(System.currentTimeMillis().toString()) }
}

tasks.named("build") { dependsOn("frontendTypecheck") }
