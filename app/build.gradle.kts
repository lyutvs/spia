plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("com.google.devtools.ksp")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("io.spia")
}

group = "io.spia"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    ksp(project(":processor"))
}

kotlin {
    jvmToolchain(21)
}

spia {
    outputPath = "frontend/src/generated/api-sdk.ts"
    enumStyle = "union"
    longType = "number"
    apiClient = "axios"
}
