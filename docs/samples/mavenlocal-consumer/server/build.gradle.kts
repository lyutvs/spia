plugins {
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    id("com.google.devtools.ksp") version "2.1.20-1.0.31"
    id("io.spia") version "0.1.0"
}

group = "sample"
version = "0.0.1"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
}

kotlin { jvmToolchain(21) }

spia {
    outputPath = "$rootDir/frontend/api.ts"
    apiClient = "axios"
}
