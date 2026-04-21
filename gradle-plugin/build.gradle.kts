import com.vanniktech.maven.publish.SonatypeHost

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.vanniktech.maven.publish") version "0.30.0"
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
            id = "io.github.lyutvs.spia"
            implementationClass = "io.spia.gradle.SpiaPlugin"
        }
    }
}

// Expose the plugin's own version to runtime code so SpiaPlugin can add the matching
// processor artifact to the `ksp` configuration without the consumer declaring it.
val generatedResources = layout.buildDirectory.dir("generated/spia-resources")
tasks.register("generateVersionProperties") {
    inputs.property("version", project.version)
    inputs.property("group", project.group)
    outputs.dir(generatedResources)
    doLast {
        val out = generatedResources.get().file("spia-version.properties").asFile
        out.parentFile.mkdirs()
        out.writeText("group=${project.group}\nversion=${project.version}\n")
    }
}
sourceSets.named("main") {
    resources.srcDir(generatedResources)
}
tasks.named("processResources") { dependsOn("generateVersionProperties") }
// sourcesJar is registered lazily by Vanniktech; it picks up resources.srcDirs, so
// it also needs to wait for the generator.
tasks.matching { it.name == "sourcesJar" }.configureEach {
    dependsOn("generateVersionProperties")
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    // Sign only when signing credentials are present, so publishToMavenLocal
    // works for unsigned dry-runs (task 24).
    if (project.hasProperty("signing.keyId") ||
        System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) {
        signAllPublications()
    }

    coordinates(project.group.toString(), "gradle-plugin", project.version.toString())

    pom {
        name.set("SPIA Gradle Plugin")
        description.set("Kotlin Symbol Processing plugin that generates a type-safe TypeScript SDK from Spring Boot controllers.")
        inceptionYear.set("2026")
        url.set("https://github.com/lyutvs/spia")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("spia")
                name.set("SPIA Contributors")
                url.set("https://github.com/lyutvs/spia")
            }
        }
        scm {
            connection.set("scm:git:https://github.com/lyutvs/spia.git")
            developerConnection.set("scm:git:ssh://git@github.com/lyutvs/spia.git")
            url.set("https://github.com/lyutvs/spia")
        }
    }
}
