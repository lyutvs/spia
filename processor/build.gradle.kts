plugins {
    kotlin("jvm")
}

group = "io.spia"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.1.20-1.0.31")
}

kotlin {
    jvmToolchain(21)
}
