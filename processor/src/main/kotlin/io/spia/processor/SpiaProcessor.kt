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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
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
        val analyzer = ControllerAnalyzer(typeResolver, logger)
        val generator = TypeScriptGenerator(config, logger)

        // Find @ControllerAdvice / @RestControllerAdvice classes for global error mapping.
        val adviceClasses = (
            resolver.getSymbolsWithAnnotation(SpringAnnotations.CONTROLLER_ADVICE) +
            resolver.getSymbolsWithAnnotation(SpringAnnotations.REST_CONTROLLER_ADVICE)
        ).filterIsInstance<KSClassDeclaration>().toList()

        // Pass 1: pre-register every DTO/enum reachable from controller signatures so
        // TS names (including same-simple-name disambiguation) are finalized before any
        // field reference is resolved.
        controllers.forEach { controller ->
            controller.declarations.filterIsInstance<KSFunctionDeclaration>().forEach { fn ->
                fn.parameters.forEach { p -> typeResolver.preRegister(p.type.resolve()) }
                fn.returnType?.resolve()?.let { typeResolver.preRegister(it) }
            }
        }
        // Pre-register return types from @ExceptionHandler methods (in advice and controllers).
        (controllers + adviceClasses).forEach { cls ->
            cls.declarations.filterIsInstance<KSFunctionDeclaration>().forEach { fn ->
                val hasHandler = fn.annotations.any {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == SpringAnnotations.EXCEPTION_HANDLER
                }
                if (hasHandler) fn.returnType?.resolve()?.let { typeResolver.preRegister(it) }
            }
        }

        // Collect global error responses from @ControllerAdvice classes.
        val globalErrors = analyzer.collectAdviceErrors(adviceClasses)

        // Pass 2: resolve and analyze controllers with stable names in place.
        val controllerInfos = controllers.map { analyzer.analyze(it, globalErrors) }
        val tsContent = generator.generate(controllerInfos, typeResolver)

        writeOutput(config, tsContent, controllerInfos, typeResolver, generator)

        return emptyList()
    }

    private fun writeOutput(
        config: SdkConfig,
        content: String,
        controllerInfos: List<io.spia.processor.model.ControllerInfo> = emptyList(),
        typeResolver: TypeResolver? = null,
        generator: TypeScriptGenerator? = null,
    ) {
        val outputPath = config.outputPath
        // Resolve the directory used for split-mode emission. If outputPath is set, we
        // place per-controller / index / _shared files alongside it; otherwise default to
        // <projectDir>/build/generated/spia. The default-branch single-file `api-sdk.ts`
        // path below is preserved for back-compat (split == false).
        val splitOutputDir: File = if (outputPath != null) {
            File(outputPath).parentFile ?: File(".")
        } else {
            val projectDir = environment.options["spia.projectDir"] ?: error("SPIA plugin did not forward projectDir")
            File(projectDir, "build/generated/spia")
        }

        if (config.splitByController && generator != null && typeResolver != null) {
            splitOutputDir.mkdirs()
            // _shared.ts — DTOs, ApiError, ClientOptions
            val sharedContent = generator.generateShared(controllerInfos, typeResolver)
            val sharedFile = File(splitOutputDir, "_shared.ts")
            sharedFile.writeText(sharedContent)
            logger.info("SPIA: Generated shared SDK module at ${sharedFile.absolutePath}")

            // <slug>.api.ts per controller
            for (controller in controllerInfos) {
                val slug = generator.controllerToSlug(controller.name)
                val perFile = File(splitOutputDir, "$slug.api.ts")
                perFile.writeText(generator.generateController(controller, typeResolver))
                logger.info("SPIA: Generated per-controller SDK at ${perFile.absolutePath}")
            }

            // index.ts barrel
            val indexFile = File(splitOutputDir, "index.ts")
            indexFile.writeText(generator.generateIndex(controllerInfos))
            logger.info("SPIA: Generated SDK index at ${indexFile.absolutePath}")

            if (config.schemaOutput == SchemaOutput.ZOD) {
                val zodFile = File(splitOutputDir, "api-sdk.zod.ts")
                val zodContent = ZodSchemaGenerator().generate(
                    typeResolver.allDtos(),
                    typeResolver.allGenerics(),
                )
                zodFile.writeText(zodContent)
                logger.info("SPIA: Generated Zod schemas at ${zodFile.absolutePath}")
            }
        } else if (outputPath != null) {
            val file = File(outputPath)
            file.parentFile?.mkdirs()
            updateLockfile(file, content)
            file.writeText(content)
            logger.info("SPIA: Generated SDK at $outputPath")

            if (config.schemaOutput == SchemaOutput.ZOD && typeResolver != null) {
                val zodFile = File(outputPath.removeSuffix(".ts") + ".zod.ts")
                val zodContent = ZodSchemaGenerator().generate(
                    typeResolver.allDtos(),
                    typeResolver.allGenerics(),
                )
                zodFile.writeText(zodContent)
                logger.info("SPIA: Generated Zod schemas at ${zodFile.absolutePath}")
            }
        } else {
            // Default: write to build directory
            val projectDir = environment.options["spia.projectDir"] ?: error("SPIA plugin did not forward projectDir")
            val defaultDir = File(projectDir, "build/generated/spia")
            defaultDir.mkdirs()
            val file = File(defaultDir, "api-sdk.ts")
            file.writeText(content)
            logger.info("SPIA: Generated SDK at ${file.absolutePath}")

            if (config.schemaOutput == SchemaOutput.ZOD && typeResolver != null) {
                val zodFile = File(defaultDir, "api-sdk.zod.ts")
                val zodContent = ZodSchemaGenerator().generate(
                    typeResolver.allDtos(),
                    typeResolver.allGenerics(),
                )
                zodFile.writeText(zodContent)
                logger.info("SPIA: Generated Zod schemas at ${zodFile.absolutePath}")
            }
        }

        if (config.openApiOutput == OpenApiVersion.V3_1) {
            val projectDir = environment.options["spia.projectDir"] ?: error("SPIA plugin did not forward projectDir")
            val openApiDir = File(projectDir, "build/generated/spia")
            openApiDir.mkdirs()
            val allDtos = typeResolver?.allDtos()?.toList() ?: emptyList()
            val allEnums = typeResolver?.allEnums()?.toList() ?: emptyList()
            val allGenerics = typeResolver?.allGenerics()?.toList() ?: emptyList()
            val json = OpenApiGenerator.generate(
                controllers = controllerInfos,
                dtos = allDtos,
                enums = allEnums,
                generics = allGenerics,
                title = environment.options["spia.moduleName"] ?: "Spia API",
                version = "1.0.0",
            )
            val openApiFile = File(openApiDir, "openapi.json")
            openApiFile.writeText(json)
            logger.info("SPIA: Generated OpenAPI spec at ${openApiFile.absolutePath}")
        }
    }

    /**
     * Maintains a sidecar lockfile at `<outputFile>.spia-lock`.
     * Each line is **tab-separated**: `moduleName\tsha256Hex\tiso8601Timestamp`
     * (breaking change from v0.4.0, which used a colon-delimited format).
     * Lines that do not parse into exactly three tab-separated parts are silently dropped on read.
     *
     * The lockfile is written via an atomic temp-file + move sequence to avoid TOCTOU races
     * between parallel Gradle workers sharing the same output path.
     *
     * If another module name already exists in the lockfile with a different SHA-256 digest,
     * emits a KSPLogger.warn so the build output surfaces the conflict (EC-10).
     *
     * **Finding H-1 (deferred):** the digest is recorded here *before* the caller writes the
     * main output file (see `process()` → `writeOutput()` at the `updateLockfile` / `file.writeText`
     * sequence). A crash between the two leaves the lockfile ahead of the actual output. Fix deferred.
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
                val parts = line.split("\t", limit = 3)
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

        // Write lockfile with stable (sorted) ordering via atomic temp-file + move
        val newContent = existingEntries.values
            .sortedBy { it.module }
            .joinToString("\n") { "${it.module}\t${it.sha256}\t${it.timestamp}" }

        lockFile.parentFile?.mkdirs()
        val parentPath = lockFile.parentFile?.toPath()
            ?: throw IllegalStateException("EC-13 SPIA: cannot resolve lockfile parent directory for ${lockFile.absolutePath}")
        val tmp = Files.createTempFile(parentPath, ".spia-lock-", ".tmp")
        try {
            Files.writeString(tmp, newContent)
            try {
                Files.move(tmp, lockFile.toPath(), ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp, lockFile.toPath(), REPLACE_EXISTING)
            }
        } catch (t: Throwable) {
            try { Files.deleteIfExists(tmp) } catch (_: Throwable) { /* best-effort cleanup */ }
            throw t
        }
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
            baseUrl = options["spia.clientOptions.baseUrl"],
            schemaOutput = when (options["spia.schemaOutput"]?.lowercase()) {
                "zod" -> SchemaOutput.ZOD
                else -> SchemaOutput.NONE
            },
            openApiOutput = when (options["spia.openApiOutput"]) {
                "3.1" -> OpenApiVersion.V3_1
                else -> OpenApiVersion.NONE
            },
            splitByController = options["spia.splitByController"]?.toBoolean() ?: false,
        )
    }
}
