package io.spia.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.Properties

class SpiaPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "spia",
            SpiaExtension::class.java,
        )

        extension.enumStyle.convention("union")
        extension.longType.convention("number")
        extension.apiClient.convention("fetch")

        // Attach the matching processor artifact to the consumer's `ksp` configuration.
        // Consumers apply `id("io.spia")` and the processor is wired automatically —
        // unless they've already declared a processor dependency themselves (e.g.,
        // the in-repo demo which uses `ksp(project(":processor"))`).
        //
        // Hooked via pluginManager.withPlugin so the injection runs as soon as the
        // KSP plugin creates its `ksp` configuration, regardless of `plugins {}`
        // declaration order.
        val coords = readPluginCoordinates()
        if (coords != null) {
            val (group, version) = coords
            project.pluginManager.withPlugin("com.google.devtools.ksp") {
                val kspConf = project.configurations.findByName("ksp")
                    ?: project.configurations.create("ksp")
                val alreadyDeclared = kspConf.allDependencies.any {
                    it.name == "processor" && (it.group == group || it.group == null)
                }
                if (!alreadyDeclared) {
                    project.dependencies.add("ksp", "$group:processor:$version")
                }
            }
        }

        project.afterEvaluate {
            val kspExtension = project.extensions.findByName("ksp")
            if (kspExtension == null) {
                project.logger.warn("spia: KSP plugin not found. Apply com.google.devtools.ksp first.")
                return@afterEvaluate
            }

            val ksp = kspExtension as com.google.devtools.ksp.gradle.KspExtension

            if (extension.outputPath.isPresent) {
                val resolved = project.file(extension.outputPath.get()).absolutePath
                ksp.arg("spia.outputPath", resolved)
            }

            ksp.arg("spia.projectDir", project.projectDir.absolutePath)
            ksp.arg("spia.moduleName", project.name)
            ksp.arg("spia.enumStyle", extension.enumStyle.get())
            ksp.arg("spia.longType", extension.longType.get())
            ksp.arg("spia.apiClient", extension.apiClient.get())
        }
    }

    private fun readPluginCoordinates(): Pair<String, String>? {
        val stream = SpiaPlugin::class.java.classLoader
            .getResourceAsStream("spia-version.properties") ?: return null
        return stream.use { input ->
            val props = Properties().apply { load(input) }
            val group = props.getProperty("group") ?: return@use null
            val version = props.getProperty("version") ?: return@use null
            group to version
        }
    }
}
