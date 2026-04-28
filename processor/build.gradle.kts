import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    kotlin("jvm")
    id("com.vanniktech.maven.publish") version "0.36.0"
    id("jacoco")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.3.7")

    testImplementation(kotlin("test"))
    testImplementation("dev.zacsweers.kctfork:core:0.12.1")
    testImplementation("dev.zacsweers.kctfork:ksp:0.12.1")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

jacoco { toolVersion = "0.8.11" }
tasks.named<Test>("test") { finalizedBy(tasks.named("jacocoTestReport")) }
tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.50".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.30".toBigDecimal()
            }
        }
    }
}
tasks.named("check") { dependsOn(tasks.named("jacocoTestCoverageVerification")) }

mavenPublishing {
    publishToMavenCentral()

    if (project.hasProperty("signing.keyId") ||
        System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) {
        signAllPublications()
    }

    coordinates(project.group.toString(), "processor", project.version.toString())

    pom {
        name.set("SPIA KSP Processor")
        description.set("KSP SymbolProcessor that extracts Spring Boot controller metadata and generates TypeScript interfaces and API client.")
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
