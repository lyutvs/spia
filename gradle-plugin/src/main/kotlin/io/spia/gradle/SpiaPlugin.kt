package io.spia.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class SpiaPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "spia",
            SpiaExtension::class.java,
        )

        extension.enumStyle.convention("union")
        extension.longType.convention("number")
        extension.apiClient.convention("axios")

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

            ksp.arg("spia.enumStyle", extension.enumStyle.get())
            ksp.arg("spia.longType", extension.longType.get())
            ksp.arg("spia.apiClient", extension.apiClient.get())
        }
    }
}
