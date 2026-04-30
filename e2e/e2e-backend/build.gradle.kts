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
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    // The SPIA Gradle plugin auto-injects the processor artifact into `ksp`.
    // External-consumer mode: do NOT add `ksp(project(":processor"))` here.
}

kotlin {
    jvmToolchain(21)
}

spia {
    // Single-file output: emits api-sdk.ts directly into the TS project's src/generated.
    outputPath = "../e2e-client/src/generated/api-sdk.ts"
    enumStyle = "union"
    longType = "number"
    schemaOutput = "none"     // Zod not needed for round-trip tests
    openApiOutput = "none"
    clientOptions {
        baseUrl = "http://localhost:18080"
    }
}
