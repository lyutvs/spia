package io.spia.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import io.spia.processor.model.*
import java.io.File

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
            file.writeText(content)
            logger.info("SPIA: Generated SDK at $outputPath")
        } else {
            // Default: write to build directory
            val projectDir = environment.options["spia.projectDir"]
            val defaultDir = if (projectDir != null) {
                File(projectDir, "build/generated/spia")
            } else {
                File("build/generated/spia")
            }
            defaultDir.mkdirs()
            val file = File(defaultDir, "api-sdk.ts")
            file.writeText(content)
            logger.info("SPIA: Generated SDK at ${file.absolutePath}")
        }
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
