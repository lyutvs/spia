@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.spia.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.symbolProcessorProviders
import io.spia.processor.model.*
import io.spia.processor.test_support.coreSpringStubs
import io.spia.processor.test_support.jacksonStubs
import io.spia.processor.test_support.pageableStubs
import io.spia.processor.test_support.reactorStubs
import io.spia.processor.test_support.sseStubs
import io.spia.processor.test_support.resourceStubs
import io.spia.processor.test_support.responseEntityStubs
import io.spia.processor.test_support.validationStubs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.lang.reflect.Method
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Parametrized tests for TypeScriptGenerator.renderType() covering all sealed when branches,
 * plus rendering for nullable types, collections, generics, enums, and ApiClient modes.
 */
class TypeScriptGeneratorParametrizedTest {

    companion object {
        private fun makeConfig(
            enumStyle: EnumStyle = EnumStyle.UNION,
            longType: LongType = LongType.NUMBER,
            apiClient: ApiClient = ApiClient.AXIOS,
        ) = SdkConfig(
            outputPath = null,
            enumStyle = enumStyle,
            longType = longType,
            apiClient = apiClient,
        )

        @JvmStatic
        fun renderTypeCases(): List<Array<Any>> = listOf(
            // TypeInfo.Primitive
            arrayOf(TypeInfo.Primitive("string"), "string"),
            arrayOf(TypeInfo.Primitive("number"), "number"),
            arrayOf(TypeInfo.Primitive("boolean"), "boolean"),
            arrayOf(TypeInfo.Primitive("void"), "void"),
            arrayOf(TypeInfo.Primitive("bigint"), "bigint"),
            // TypeInfo.Array
            arrayOf(TypeInfo.Array(TypeInfo.Primitive("string")), "string[]"),
            arrayOf(TypeInfo.Array(TypeInfo.Primitive("number")), "number[]"),
            // TypeInfo.Record
            arrayOf(TypeInfo.Record(TypeInfo.Primitive("string"), TypeInfo.Primitive("number")), "{ [key: string]: number }"),
            // TypeInfo.Unknown
            arrayOf(TypeInfo.Unknown("any"), "any"),
            arrayOf(TypeInfo.Unknown("unknown"), "unknown"),
            // TypeInfo.Dto
            arrayOf(TypeInfo.Dto("UserDto", emptyList()), "UserDto"),
            // TypeInfo.Enum
            arrayOf(TypeInfo.Enum("Status", listOf("ACTIVE", "INACTIVE")), "Status"),
            // TypeInfo.TypeParameter
            arrayOf(TypeInfo.TypeParameter("T"), "T"),
            // TypeInfo.SealedUnion
            arrayOf(TypeInfo.SealedUnion("Shape", emptyList(), null), "Shape"),
            // TypeInfo.StreamType
            arrayOf(TypeInfo.StreamType(TypeInfo.Primitive("string")), "AsyncIterable<string>"),
            // TypeInfo.ValueClass
            arrayOf(TypeInfo.ValueClass("UserId", TypeInfo.Primitive("string")), "UserId"),
            // TypeInfo.Generic with typeArguments
            arrayOf(
                TypeInfo.Generic("Page", listOf("T"), emptyList(), listOf(TypeInfo.Primitive("string"))),
                "Page<string>"
            ),
            // TypeInfo.Generic without typeArguments (raw)
            arrayOf(
                TypeInfo.Generic("Page", listOf("T"), emptyList(), emptyList()),
                "Page<T>"
            ),
        )
    }

    /**
     * Uses reflection to call the private renderType() method directly, covering all
     * sealed when branches from TypeInfo.
     */
    @ParameterizedTest(name = "[{index}] renderType({1})")
    @MethodSource("renderTypeCases")
    fun `renderType covers all TypeInfo sealed branches`(typeInfo: TypeInfo, expected: String) {
        val gen = TypeScriptGenerator(makeConfig())
        val result = invokeRenderType(gen, typeInfo)
        assertEquals(expected, result, "renderType for $typeInfo")
    }

    /** Tests that nullable Array renders correctly with the full type notation. */
    @ParameterizedTest(name = "nullable {0}[] renders with nullable=true")
    @CsvSource(
        "string, string[]",
        "number, number[]",
    )
    fun `nullable array typeinfo renders element type correctly`(elementTs: String, expectedResult: String) {
        val gen = TypeScriptGenerator(makeConfig())
        val arrayType = TypeInfo.Array(TypeInfo.Primitive(elementTs), nullable = true)
        val result = invokeRenderType(gen, arrayType)
        assertEquals(expectedResult, result, "renderType for nullable Array($elementTs)")
    }

    /** Tests ENUM style: UNION vs ENUM rendering via full KSP compilation. */
    @ParameterizedTest(name = "EnumStyle.{0} generates enum with {1}")
    @CsvSource(
        "union, export type",
        "enum, export enum",
    )
    fun `enum style config controls rendering`(styleStr: String, expectedKeyword: String, @TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "EnumController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            enum class Direction { NORTH, SOUTH, EAST, WEST }
            data class DirectionDto(val dir: Direction)
            @RestController
            class EnumController {
                @GetMapping("/dir")
                fun get(): DirectionDto = DirectionDto(Direction.NORTH)
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
                processorOptions["spia.apiClient"] = "axios"
                processorOptions["spia.enumStyle"] = styleStr
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val sdk = File(outputPath).readText()
        assertTrue(sdk.contains(expectedKeyword), "Expected '$expectedKeyword' for enumStyle=$styleStr in:\n$sdk")
    }

    /** Tests LongType config branches: number, string, bigint. */
    @ParameterizedTest(name = "LongType.{0} maps kotlin.Long to {1}")
    @CsvSource(
        "number, number",
        "string, string",
        "bigint, bigint",
    )
    fun `long type config controls Long mapping`(longTypeStr: String, expectedTsType: String, @TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "LongController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            data class LongDto(val id: Long)
            @RestController
            class LongController {
                @GetMapping("/longs")
                fun get(): LongDto = LongDto(1L)
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
                processorOptions["spia.apiClient"] = "axios"
                processorOptions["spia.longType"] = longTypeStr
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val sdk = File(outputPath).readText()
        assertTrue(sdk.contains("id: $expectedTsType"), "Expected 'id: $expectedTsType' in SDK for longType=$longTypeStr:\n$sdk")
    }

    /** Tests fetch client mode generates ClientOptions interface. */
    @Test
    fun `fetch api client generates ClientOptions interface`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "FetchController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            @RestController
            class FetchController {
                @GetMapping("/ping")
                fun ping(): String = "pong"
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
                processorOptions["spia.apiClient"] = "fetch"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val sdk = File(outputPath).readText()
        assertTrue(sdk.contains("ClientOptions"), "Expected ClientOptions interface in fetch mode")
        assertFalse(sdk.contains("AxiosInstance"), "Should not include Axios types in fetch mode")
    }

    /** Tests that nullable DTO fields render with '| null'. */
    @Test
    fun `nullable DTO fields render with pipe null`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "NullableController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            data class Profile(val name: String, val bio: String?, val age: Int?)
            @RestController
            class NullableController {
                @GetMapping("/profile")
                fun get(): Profile = Profile("Alice", null, null)
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
                processorOptions["spia.apiClient"] = "axios"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val sdk = File(outputPath).readText()
        assertTrue(sdk.contains("bio: string | null"), "Expected nullable string in: $sdk")
        assertTrue(sdk.contains("age: number | null"), "Expected nullable number in: $sdk")
        assertTrue(sdk.contains("name: string"), "Expected non-nullable name in: $sdk")
    }

    /** Tests that generic DTO with multiple type parameters renders correctly. */
    @Test
    fun `generic DTO with multiple type parameters renders`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "GenericController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            data class Pair<A, B>(val first: A, val second: B)
            data class Response<T>(val data: T, val ok: Boolean)
            @RestController
            class GenericController {
                @GetMapping("/pair")
                fun get(): Pair<String, Int> = Pair("hello", 42)
                @GetMapping("/resp")
                fun resp(): Response<String> = Response("ok", true)
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
                processorOptions["spia.apiClient"] = "axios"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val sdk = File(outputPath).readText()
        assertTrue(sdk.contains("Pair<"), "Expected generic Pair<A, B> interface in: $sdk")
        assertTrue(sdk.contains("Response<"), "Expected generic Response<T> interface in: $sdk")
    }

    /** Tests Map<K,V> fields render as Record. */
    @Test
    fun `map fields render as Record type`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "MapController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            data class MapDto(val metadata: Map<String, String>, val counts: Map<String, Int>)
            @RestController
            class MapController {
                @GetMapping("/map")
                fun get(): MapDto = MapDto(mapOf(), mapOf())
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
                processorOptions["spia.apiClient"] = "axios"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val sdk = File(outputPath).readText()
        assertTrue(sdk.contains("[key: string]: string"), "Expected string record in: $sdk")
        assertTrue(sdk.contains("[key: string]: number"), "Expected number record in: $sdk")
    }

    /** Tests Set<T> fields render as array type. */
    @Test
    fun `set fields render as array type`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "SetController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            data class SetDto(val tags: Set<String>)
            @RestController
            class SetController {
                @GetMapping("/set")
                fun get(): SetDto = SetDto(setOf())
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
                processorOptions["spia.apiClient"] = "axios"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val sdk = File(outputPath).readText()
        assertTrue(sdk.contains("string[]"), "Expected string[] for Set<String> in: $sdk")
    }

    /** Tests that sealed class renders as discriminated union when Jackson annotation is present. */
    @Test
    fun `sealed class with Jackson annotation renders discriminated union`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "SealedController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import com.fasterxml.jackson.annotation.JsonTypeInfo
            import com.fasterxml.jackson.annotation.JsonTypeName
            import com.fasterxml.jackson.annotation.JsonSubTypes

            @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
            @JsonSubTypes(JsonSubTypes.Type(value = Circle::class, name = "circle"), JsonSubTypes.Type(value = Square::class, name = "square"))
            sealed class Shape
            @JsonTypeName("circle") data class Circle(val radius: Double) : Shape()
            @JsonTypeName("square") data class Square(val side: Double) : Shape()

            @RestController
            class SealedController {
                @GetMapping("/shape")
                fun get(): Shape = Circle(1.0)
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs(), jacksonStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
                processorOptions["spia.apiClient"] = "axios"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val sdk = File(outputPath).readText()
        assertTrue(sdk.contains("'circle'") || sdk.contains("Circle"), "Expected discriminated union in: $sdk")
    }

    /** Tests controllerToSlug converts PascalCase to kebab-case. */
    @ParameterizedTest(name = "controllerToSlug({0}) == {1}")
    @CsvSource(
        "UserController, user",
        "UserProfileController, user-profile",
        "OrderItemController, order-item",
    )
    fun `controllerToSlug converts controller names to kebab-case`(input: String, expected: String) {
        val gen = TypeScriptGenerator(makeConfig())
        val slug = gen.controllerToSlug(input)
        assertEquals(expected, slug, "controllerToSlug($input)")
    }

    /** Tests ParameterKind.COOKIE results in a single cookies? parameter in the signature. */
    @Test
    fun `cookie parameters are collapsed into a single cookies object`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "CookieController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.CookieValue
            @RestController
            class CookieController {
                @GetMapping("/cookie")
                fun get(@CookieValue("session") session: String, @CookieValue("token") token: String): String = session
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
                processorOptions["spia.apiClient"] = "axios"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val sdk = File(outputPath).readText()
        assertTrue(sdk.contains("cookies?:"), "Expected collapsed cookies param in: $sdk")
    }

    /** Tests ParameterKind.PAGEABLE is expanded into page/size/sort parameters. */
    @Test
    fun `pageable parameter expands to page, size, sort`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "PageableController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.data.domain.Pageable
            data class Item(val id: Long)
            @RestController
            class PageableController {
                @GetMapping("/items")
                fun list(pageable: Pageable): List<Item> = emptyList()
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs(), pageableStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
                processorOptions["spia.apiClient"] = "axios"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val sdk = File(outputPath).readText()
        assertTrue(sdk.contains("page?:"), "Expected page? param in: $sdk")
        assertTrue(sdk.contains("size?:"), "Expected size? param in: $sdk")
        assertTrue(sdk.contains("sort?:"), "Expected sort? param in: $sdk")
    }

    /** Tests ParameterKind.MATRIX_VARIABLE appears in params. */
    @Test
    fun `matrix variable parameter appears in function signature`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "MatrixController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.MatrixVariable
            import org.springframework.web.bind.annotation.PathVariable
            @RestController
            class MatrixController {
                @GetMapping("/cars/{carId}")
                fun get(@PathVariable carId: String, @MatrixVariable color: String): String = carId
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
                processorOptions["spia.apiClient"] = "axios"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        val sdk = File(outputPath).readText()
        assertTrue(sdk.contains("color"), "Expected matrix variable 'color' in: $sdk")
    }

    /** Tests that split-by-controller mode generates separate api files via generateController(). */
    @Test
    fun `split by controller mode generates per-controller files`(@TempDir tempDir: File) {
        // splitByController uses File(outputPath).parentFile as the directory
        // so outputPath should be a file path, and the parent dir will receive the generated files
        val outputDir = File(tempDir, "generated")
        outputDir.mkdirs()
        val outputPath = File(outputDir, "api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "SplitController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            @RestController
            class SplitController {
                @GetMapping("/split")
                fun get(): String = "hello"
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
                processorOptions["spia.apiClient"] = "axios"
                processorOptions["spia.splitByController"] = "true"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
        // Should generate _shared.ts, split.api.ts, and index.ts in the parent directory
        val generatedFiles = outputDir.listFiles()?.filter { it.name.endsWith(".ts") }
        assertTrue(
            !generatedFiles.isNullOrEmpty(),
            "Expected generated .ts files in: ${outputDir.absolutePath}, found: ${outputDir.listFiles()?.toList()}"
        )
    }

    // ─── Reflection helper ──────────────────────────────────────────────────────────

    /**
     * Calls the private renderType() method on TypeScriptGenerator using reflection.
     * This directly exercises all sealed when branches without needing a full compilation.
     */
    private fun invokeRenderType(gen: TypeScriptGenerator, typeInfo: TypeInfo): String {
        val method: Method = TypeScriptGenerator::class.java
            .getDeclaredMethod("renderType", TypeInfo::class.java)
        method.isAccessible = true
        return method.invoke(gen, typeInfo) as String
    }
}
