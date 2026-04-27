package io.spia.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import io.spia.processor.model.*
import java.io.File
import java.security.MessageDigest
import java.time.Instant

class SpiaProcessor(
    private val environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    private val logger: KSPLogger = environment.logger
    private var processed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()
        processed = true

        val config = parseConfig(environment.options)
        logger.info("SPIA: config=$config")

        // Find all @RestController classes
        val controllers = resolver.getSymbolsWithAnnotation(SpringAnnotations.REST_CONTROLLER)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        if (controllers.isEmpty()) {
            logger.info("SPIA: No @RestController classes found.")
            return emptyList()
        }

        logger.info("SPIA: Found ${controllers.size} controller(s)")

        val typeResolver = TypeResolver(config)
        val analyzer = ControllerAnalyzer(typeResolver)
        val generator = TypeScriptGenerator(config)

        // Pass 1: pre-register every DTO/enum reachable from controller signatures so
        // TS names (including same-simple-name disambiguation) are finalized before any
        // field reference is resolved.
        controllers.forEach { controller ->
            controller.declarations.filterIsInstance<KSFunctionDeclaration>().forEach { fn ->
                fn.parameters.forEach { p -> typeResolver.preRegister(p.type.resolve()) }
                fn.returnType?.resolve()?.let { typeResolver.preRegister(it) }
            }
        }

        // Pass 2: resolve and analyze controllers with stable names in place.
        val controllerInfos = controllers.map { analyzer.analyze(it) }
        val tsContent = generator.generate(controllerInfos, typeResolver)

        writeOutput(config, tsContent)

        return emptyList()
    }

    private fun writeOutput(config: SdkConfig, content: String) {
        val outputPath = config.outputPath
        if (outputPath != null) {
            val file = File(outputPath)
            file.parentFile?.mkdirs()
            updateLockfile(file, content)
            file.writeText(content)
            logger.info("SPIA: Generated SDK at $outputPath")
        } else {
            // Default: write to build directory
            val projectDir = environment.options["spia.projectDir"] ?: error("SPIA plugin did not forward projectDir")
            val defaultDir = File(projectDir, "build/generated/spia")
            defaultDir.mkdirs()
            val file = File(defaultDir, "api-sdk.ts")
            file.writeText(content)
            logger.info("SPIA: Generated SDK at ${file.absolutePath}")
        }
    }

    /**
     * Maintains a sidecar lockfile at `<outputFile>.spia-lock`.
     * Each line: `moduleName:sha256Hex:iso8601Timestamp`
     *
     * If another module name already exists in the lockfile with a different SHA-256 digest,
     * emits a KSPLogger.warn so the build output surfaces the conflict (EC-10).
     */
    private fun updateLockfile(outputFile: File, content: String) {
        val moduleName = environment.options["spia.moduleName"]
            ?: environment.options["spia.projectDir"]?.let { File(it).name }
            ?: "unknown"

        val digest = sha256Hex(content)
        val timestamp = Instant.now().toString()
        val lockFile = File("${outputFile.absolutePath}.spia-lock")

        // Parse existing entries
        data class LockEntry(val module: String, val sha256: String, val timestamp: String)

        val existingEntries: MutableMap<String, LockEntry> = mutableMapOf()
        if (lockFile.exists()) {
            lockFile.readLines().forEach { line ->
                val parts = line.split(":", limit = 3)
                if (parts.size == 3) {
                    existingEntries[parts[0]] = LockEntry(parts[0], parts[1], parts[2])
                }
            }
        }

        // Detect conflict: any other module with a different digest
        val conflictingModules = existingEntries.values
            .filter { it.module != moduleName && it.sha256 != digest }
            .map { it.module }

        if (conflictingModules.isNotEmpty()) {
            logger.warn(
                "EC-10 SPIA outputPath conflict: module '$moduleName' is writing to '${outputFile.absolutePath}' " +
                "but the following module(s) have already written a different digest: $conflictingModules. " +
                "Consider using a separate outputPath per module."
            )
        }

        // Update or insert current module entry
        existingEntries[moduleName] = LockEntry(moduleName, digest, timestamp)

        // Write lockfile with stable (sorted) ordering
        lockFile.parentFile?.mkdirs()
        lockFile.writeText(
            existingEntries.values
                .sortedBy { it.module }
                .joinToString("\n") { "${it.module}:${it.sha256}:${it.timestamp}" }
        )
    }

    private fun sha256Hex(content: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(content.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun parseConfig(options: Map<String, String>): SdkConfig {
        return SdkConfig(
            outputPath = options["spia.outputPath"],
            enumStyle = when (options["spia.enumStyle"]) {
                "enum" -> EnumStyle.ENUM
                else -> EnumStyle.UNION
            },
            longType = when (options["spia.longType"]) {
                "string" -> LongType.STRING
                "bigint" -> LongType.BIGINT
                else -> LongType.NUMBER
            },
            apiClient = when (options["spia.apiClient"]) {
                "fetch" -> ApiClient.FETCH
                else -> ApiClient.AXIOS
            },
        )
    }
}
