package io.spia.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
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
        extension.schemaOutput.convention("none")
        extension.openApiOutput.convention("none")

        // Wire the version convention lazily so it picks up root project version after evaluation.
        extension.npmPackage.version.convention(
            project.provider { project.rootProject.version.toString() }
        )

        // Attach the matching processor artifact to the consumer's `ksp` configuration.
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

        // Register the spiaPackNpm task — assembles an npm-publishable package from the generated TS.
        project.tasks.register("spiaPackNpm") {
            group = "spia"
            description = "Assemble generated TypeScript SDK as an npm package in build/npm."

            doLast {
                val npmOpts = extension.npmPackage

                // Validate required 'name' property.
                if (!npmOpts.name.isPresent) {
                    throw org.gradle.api.GradleException(
                        "spiaPackNpm requires 'npmPackage.name' to be set in the spia { } block. " +
                        "Example: npmPackage { name.set(\"@org/api-sdk\") }"
                    )
                }

                val pkgName = npmOpts.name.get()
                val pkgVersion = npmOpts.version.get()
                val outputDirPath = npmOpts.outputDir.get()

                val outputDir = if (File(outputDirPath).isAbsolute) {
                    File(outputDirPath)
                } else {
                    project.file(outputDirPath)
                }
                val srcDir = File(outputDir, "src")
                srcDir.mkdirs()

                // Copy generated TS files into src/
                val tsFiles = collectGeneratedTsFiles(project, extension)
                if (tsFiles.isEmpty()) {
                    project.logger.warn("spiaPackNpm: no generated TS files found. " +
                        "Run kspKotlin (or build) first so SPIA can emit the SDK files.")
                }
                for (tsFile in tsFiles) {
                    val dest = File(srcDir, tsFile.name)
                    tsFile.copyTo(dest, overwrite = true)
                    project.logger.info("spiaPackNpm: copied ${tsFile.name} -> ${dest.absolutePath}")
                }

                // Write package.json from template.
                val pkgJsonTemplate = SpiaPlugin::class.java.classLoader
                    .getResourceAsStream("npm-template/package.json.template")
                    ?.bufferedReader()?.readText()
                    ?: throw org.gradle.api.GradleException(
                        "spiaPackNpm: could not load npm-template/package.json.template from plugin resources"
                    )
                val pkgJson = pkgJsonTemplate
                    .replace("{{NAME}}", pkgName)
                    .replace("{{VERSION}}", pkgVersion)
                File(outputDir, "package.json").writeText(pkgJson)

                // Write tsconfig.json from template.
                val tsconfigTemplate = SpiaPlugin::class.java.classLoader
                    .getResourceAsStream("npm-template/tsconfig.json.template")
                    ?.bufferedReader()?.readText()
                    ?: throw org.gradle.api.GradleException(
                        "spiaPackNpm: could not load npm-template/tsconfig.json.template from plugin resources"
                    )
                File(outputDir, "tsconfig.json").writeText(tsconfigTemplate)

                project.logger.lifecycle(
                    "spiaPackNpm: npm package assembled at ${outputDir.absolutePath} " +
                    "(name=$pkgName, version=$pkgVersion)"
                )
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
            ksp.arg("spia.schemaOutput", extension.schemaOutput.get())

            if (extension.clientOptions.baseUrl.isPresent) {
                ksp.arg("spia.clientOptions.baseUrl", extension.clientOptions.baseUrl.get())
            }

            ksp.arg("spia.openApiOutput", extension.openApiOutput.get())
            ksp.arg("spia.splitByController", extension.splitByController.toString())
        }
    }

    /**
     * Collects the generated TypeScript files based on the extension's outputPath.
     * Falls back to common locations if outputPath is not configured.
     */
    private fun collectGeneratedTsFiles(project: Project, extension: SpiaExtension): List<File> {
        val files = mutableListOf<File>()

        if (extension.outputPath.isPresent) {
            val mainTsFile = project.file(extension.outputPath.get())
            if (mainTsFile.exists()) {
                files.add(mainTsFile)
            }
            // Also look for sibling files (.zod.ts, .api.ts, index.ts, _shared.ts)
            val parentDir = mainTsFile.parentFile
            if (parentDir != null && parentDir.exists()) {
                parentDir.listFiles { f ->
                    f.extension == "ts" && f != mainTsFile
                }?.forEach { files.add(it) }
            }
        } else {
            // Fallback: search common locations
            val candidates = listOf(
                project.file("build/generated/spia/api-sdk.ts"),
                project.file("frontend/src/generated/api-sdk.ts"),
                project.file("src/generated/api-sdk.ts"),
            )
            candidates.filter { it.exists() }.forEach { f ->
                files.add(f)
                f.parentFile?.listFiles { sib -> sib.extension == "ts" && sib != f }
                    ?.forEach { files.add(it) }
            }
        }

        return files.distinctBy { it.canonicalPath }
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
