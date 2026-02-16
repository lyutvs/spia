plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    compileOnly("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.1.20-1.0.31")
}

gradlePlugin {
    plugins {
        create("spia") {
            id = "io.spia"
            implementationClass = "io.spia.gradle.SpiaPlugin"
        }
    }
}
