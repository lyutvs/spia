plugins {
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("com.google.devtools.ksp") version "2.3.7"
    id("io.github.lyutvs.spia") version "0.1.0"
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
