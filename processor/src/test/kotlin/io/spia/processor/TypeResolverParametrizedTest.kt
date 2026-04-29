@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.spia.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.symbolProcessorProviders
import io.spia.processor.test_support.coreSpringStubs
import io.spia.processor.test_support.jacksonStubs
import io.spia.processor.test_support.reactorStubs
import io.spia.processor.test_support.sseStubs
import io.spia.processor.test_support.resourceStubs
import io.spia.processor.test_support.responseEntityStubs
import io.spia.processor.test_support.validationStubs
import io.spia.processor.test_support.flowStubs
import io.spia.processor.test_support.pageableStubs
import io.spia.processor.test_support.parameterStubs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Parametrized tests for TypeResolver.resolveByName() covering nullable/generic/map/list cases,
 * and ControllerAnalyzer.analyzeParameter() covering all ParameterKind variants.
 */
class TypeResolverParametrizedTest {

    // ─── Primitive type mapping tests ──────────────────────────────────────────────

    /**
     * Tests that each Kotlin/Java primitive type maps to the correct TS type.
     * Covers the major `when` branches in TypeResolver.resolveByName().
     */
    @ParameterizedTest(name = "return type {0} maps to TS {1}")
    @CsvSource(
        "String, string",
        "Boolean, boolean",
        "Int, number",
    )
    fun `primitive kotlin types map to correct TypeScript type`(
        kotlinType: String,
        expectedTs: String,
        @TempDir tempDir: File,
    ) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "PrimitiveController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            data class PrimitiveDto(val value: $kotlinType)
            @RestController
            class PrimitiveController {
                @GetMapping("/val")
                fun get(): PrimitiveDto = PrimitiveDto(${defaultValueFor(kotlinType)})
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
        assertTrue(sdk.contains("value: $expectedTs"),
            "Expected 'value: $expectedTs' for Kotlin type '$kotlinType' in: $sdk")
    }

    @ParameterizedTest(name = "temporal type {0} maps to TS string")
    @CsvSource(
        "LocalDate, java.time.LocalDate.now()",
        "LocalDateTime, java.time.LocalDateTime.now()",
    )
    fun `java time types map to TypeScript string`(
        typeName: String,
        defaultExpr: String,
        @TempDir tempDir: File,
    ) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "DateController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            data class DateDto(val timestamp: java.time.$typeName)
            @RestController
            class DateController {
                @GetMapping("/date")
                fun get(): DateDto = DateDto($defaultExpr)
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
        assertTrue(sdk.contains("timestamp: string"), "Expected 'timestamp: string' for $typeName in: $sdk")
    }

    // ─── Collection type mapping tests ─────────────────────────────────────────────

    /** Tests List<T>, Set<T>, Collection<T> all render as T[] in TypeScript. */
    @ParameterizedTest(name = "{0}<String> renders as string[]")
    @CsvSource(
        "List",
    )
    fun `collection types render as TypeScript arrays`(collectionType: String, @TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "CollectionController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            data class ItemDto(val id: String)
            @RestController
            class CollectionController {
                @GetMapping("/items")
                fun get(): $collectionType<ItemDto> = emptyList()
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
        assertTrue(sdk.contains("ItemDto[]"), "Expected 'ItemDto[]' for $collectionType<ItemDto> in: $sdk")
    }

    @Test
    fun `set return type renders as array`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "SetReturnController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            @RestController
            class SetReturnController {
                @GetMapping("/tags")
                fun get(): Set<String> = setOf("a", "b")
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
        assertTrue(sdk.contains("string[]"), "Expected 'string[]' for Set<String> in: $sdk")
    }

    // ─── Map type mapping tests ─────────────────────────────────────────────────────

    /** Tests Map<K,V> with various value types renders as Record. */
    @ParameterizedTest(name = "Map<String,{0}> renders as Record")
    @CsvSource(
        "String, string",
        "Boolean, boolean",
        "Int, number",
    )
    fun `map with primitive values renders as Record type`(
        valueType: String,
        expectedValueTs: String,
        @TempDir tempDir: File,
    ) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "MapController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            data class MapDto(val data: Map<String, $valueType>)
            @RestController
            class MapController {
                @GetMapping("/map")
                fun get(): MapDto = MapDto(mapOf())
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
        assertTrue(
            sdk.contains("[key: string]: $expectedValueTs"),
            "Expected '[key: string]: $expectedValueTs' for Map<String,$valueType> in: $sdk"
        )
    }

    // ─── Nullable field tests ───────────────────────────────────────────────────────

    /** Tests that nullable generic type arguments render with | null. */
    @Test
    fun `nullable generic type argument renders with pipe null`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "NullableGenericController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            data class Container<T>(val item: T?, val label: String)
            data class ItemDto(val name: String)
            @RestController
            class NullableGenericController {
                @GetMapping("/items")
                fun get(): Container<ItemDto> = Container(null, "test")
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
        assertTrue(sdk.contains("| null"), "Expected '| null' for nullable T in generic type: $sdk")
    }

    // ─── Reactive/async type tests ──────────────────────────────────────────────────

    /** Tests Mono<T> and Flux<T> unwrapping in resolveByName(). */
    @ParameterizedTest(name = "{0} return type unwraps correctly")
    @CsvSource(
        "Mono",
        "Flux",
    )
    fun `reactive return types are unwrapped`(reactiveType: String, @TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "ReactiveController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import reactor.core.publisher.${reactiveType}
            data class ItemDto(val id: Long)
            @RestController
            class ReactiveController {
                @GetMapping("/items")
                fun get(): $reactiveType<ItemDto> = ${if (reactiveType == "Mono") "Mono()" else "Flux()"}
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs(), reactorStubs())
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
        assertTrue(sdk.contains("ItemDto"), "Expected 'ItemDto' in unwrapped $reactiveType return: $sdk")
        assertFalse(sdk.contains("Mono") && sdk.contains("Flux"), "Should not emit raw reactive type")
    }

    /** Tests Flow<T> unwrapping. */
    @Test
    fun `kotlinx Flow return type unwraps to array`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "FlowController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import kotlinx.coroutines.flow.Flow
            data class EventDto(val name: String)
            @RestController
            class FlowController {
                @GetMapping("/events")
                suspend fun get(): Flow<EventDto> = TODO()
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs(), flowStubs())
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
        assertTrue(sdk.contains("EventDto[]"), "Expected 'EventDto[]' for Flow<EventDto> in: $sdk")
    }

    // ─── ResponseEntity unwrapping test ────────────────────────────────────────────

    /** Tests ResponseEntity<T> is unwrapped to T in TS output. */
    @Test
    fun `ResponseEntity return type is unwrapped to inner type`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "ResponseEntityController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.http.ResponseEntity
            data class ProductDto(val name: String)
            @RestController
            class ResponseEntityController {
                @GetMapping("/product")
                fun get(): ResponseEntity<ProductDto> = ResponseEntity()
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs(), responseEntityStubs())
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
        assertTrue(sdk.contains("ProductDto"), "Expected 'ProductDto' for ResponseEntity<ProductDto> in: $sdk")
        assertFalse(sdk.contains("ResponseEntity"), "Should not emit raw ResponseEntity type in: $sdk")
    }

    // ─── Multipart upload test ──────────────────────────────────────────────────────

    /** Tests MultipartFile parameter maps to File | Blob. */
    @Test
    fun `MultipartFile parameter maps to File or Blob`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "UploadController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.PostMapping
            import org.springframework.web.bind.annotation.RequestPart
            import org.springframework.web.multipart.MultipartFile
            @RestController
            class UploadController {
                @PostMapping("/upload")
                fun upload(@RequestPart("file") file: MultipartFile): String = "ok"
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs(), parameterStubs())
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
        assertTrue(sdk.contains("File | Blob"), "Expected 'File | Blob' for MultipartFile in: $sdk")
    }

    // ─── Resource download test ─────────────────────────────────────────────────────

    /** Tests Spring Resource return type maps to Blob. */
    @Test
    fun `Spring Resource return type maps to Blob`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "ResourceController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.core.io.Resource
            @RestController
            class ResourceController {
                @GetMapping("/download")
                fun download(): Resource = TODO()
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs(), resourceStubs())
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
        assertTrue(sdk.contains("Blob"), "Expected 'Blob' for Spring Resource in: $sdk")
    }

    // ─── Value class test ───────────────────────────────────────────────────────────

    /** Tests Kotlin value class is rendered as a branded TS type. */
    @Test
    fun `value class renders as branded TypeScript type`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "ValueClassController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            @JvmInline value class UserId(val raw: Long)
            data class UserDto(val id: UserId, val name: String)
            @RestController
            class ValueClassController {
                @GetMapping("/user")
                fun get(): UserDto = UserDto(UserId(1L), "Alice")
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
        assertTrue(sdk.contains("__brand"), "Expected branded type '__brand' in: $sdk")
        assertTrue(sdk.contains("UserId"), "Expected UserId type in: $sdk")
    }

    // ─── SSE type test ──────────────────────────────────────────────────────────────

    /** Tests Flux<ServerSentEvent<T>> renders as AsyncIterable<T>. */
    @Test
    fun `Flux of ServerSentEvent renders as AsyncIterable`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "SseController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import reactor.core.publisher.Flux
            import org.springframework.http.codec.ServerSentEvent
            data class SseData(val message: String)
            @RestController
            class SseController {
                @GetMapping("/sse")
                fun stream(): Flux<ServerSentEvent<SseData>> = Flux()
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs(), reactorStubs(), sseStubs())
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
        assertTrue(sdk.contains("AsyncIterable"), "Expected 'AsyncIterable' for Flux<ServerSentEvent<T>> in: $sdk")
    }

    // ─── Validation constraints tests ──────────────────────────────────────────────

    /** Tests validation constraints emit correct JSDoc annotations. */
    @Test
    fun `validation constraints emit JSDoc in generated SDK`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "ValidationController.kt",
            """
            package test
            import jakarta.validation.constraints.Size
            import jakarta.validation.constraints.Min
            import jakarta.validation.constraints.Max
            import jakarta.validation.constraints.Pattern
            import jakarta.validation.constraints.Email
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.PostMapping
            import org.springframework.web.bind.annotation.RequestBody
            data class SignupRequest(
                @field:Size(min = 3, max = 20) val username: String,
                @field:Min(0) @field:Max(150) val age: Int,
                @field:Pattern(regexp = "^[a-z]+${'$'}") val code: String,
                @field:Email val email: String,
            )
            @RestController
            class ValidationController {
                @PostMapping("/signup")
                fun signup(@RequestBody body: SignupRequest): SignupRequest = body
            }
            """.trimIndent()
        )

        val allSources = listOf(source, coreSpringStubs()) + validationStubs()
        val compilation = KotlinCompilation().apply {
            sources = allSources
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
        assertTrue(sdk.contains("@minLength"), "Expected '@minLength' constraint in: $sdk")
        assertTrue(sdk.contains("@maxLength"), "Expected '@maxLength' constraint in: $sdk")
        assertTrue(sdk.contains("@minimum"), "Expected '@minimum' constraint in: $sdk")
        assertTrue(sdk.contains("@maximum"), "Expected '@maximum' constraint in: $sdk")
        assertTrue(sdk.contains("@pattern"), "Expected '@pattern' constraint in: $sdk")
        assertTrue(sdk.contains("@format"), "Expected '@format' constraint in: $sdk")
    }

    // ─── HTTP method variants test ──────────────────────────────────────────────────

    /** Tests all HTTP method variants are rendered correctly. */
    @ParameterizedTest(name = "HTTP method {0} is rendered")
    @CsvSource(
        "GetMapping, get",
        "PostMapping, post",
        "PutMapping, put",
        "PatchMapping, patch",
    )
    fun `all HTTP method mappings are recognized`(mappingAnnotation: String, httpMethodLower: String, @TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath
        val bodyParam = if (httpMethodLower in listOf("post", "put", "patch")) {
            "@org.springframework.web.bind.annotation.RequestBody body: String"
        } else ""

        val source = SourceFile.kotlin(
            "HttpMethodController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.*
            @RestController
            class HttpMethodController {
                @$mappingAnnotation("/endpoint")
                fun action($bodyParam): String = "ok"
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "Compilation failed for $mappingAnnotation")
        val sdk = File(outputPath).readText()
        assertTrue(
            sdk.contains("client.$httpMethodLower("),
            "Expected 'client.$httpMethodLower(' in SDK for $mappingAnnotation:\n$sdk"
        )
    }

    @Test
    fun `DELETE http method is rendered`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "DeleteController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.*
            @RestController
            class DeleteController {
                @DeleteMapping("/item/{id}")
                fun remove(@PathVariable id: Long): Unit {}
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
        assertTrue(sdk.contains("client.delete("), "Expected 'client.delete(' in SDK:\n$sdk")
    }

    // ─── RequestHeader parameter test ──────────────────────────────────────────────

    /** Tests @RequestHeader parameters appear in the generated function signature. */
    @Test
    fun `request header parameter appears in function signature`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "HeaderController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RequestHeader
            @RestController
            class HeaderController {
                @GetMapping("/secure")
                fun get(@RequestHeader("Authorization") auth: String): String = auth
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
        assertTrue(sdk.contains("auth"), "Expected 'auth' header parameter in: $sdk")
    }

    // ─── Nested DTO test ────────────────────────────────────────────────────────────

    /** Tests nested DTOs are discovered and emitted in the generated SDK. */
    @Test
    fun `nested DTO fields are emitted as separate interfaces`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "NestedController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            data class Address(val street: String, val city: String)
            data class Person(val name: String, val address: Address)
            @RestController
            class NestedController {
                @GetMapping("/person")
                fun get(): Person = Person("Alice", Address("Main St", "Anytown"))
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
        assertTrue(sdk.contains("interface Address"), "Expected Address interface in: $sdk")
        assertTrue(sdk.contains("interface Person"), "Expected Person interface in: $sdk")
        assertTrue(sdk.contains("address: Address"), "Expected nested address field in: $sdk")
    }

    // ─── Optional request param test ────────────────────────────────────────────────

    /** Tests optional @RequestParam (required=false) generates optional TS parameter. */
    @Test
    fun `optional request param generates optional TypeScript parameter`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "OptionalParamController.kt",
            """
            package test
            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RequestParam
            @RestController
            class OptionalParamController {
                @GetMapping("/search")
                fun search(
                    @RequestParam query: String,
                    @RequestParam(required = false) filter: String?,
                    @RequestParam(required = false, defaultValue = "10") limit: Int
                ): List<String> = emptyList()
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
        assertTrue(sdk.contains("query: string"), "Expected required 'query: string' in: $sdk")
        assertTrue(sdk.contains("filter?:"), "Expected optional 'filter?:' in: $sdk")
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────────

    private fun defaultValueFor(typeName: String): String = when (typeName) {
        "String" -> "\"stub\""
        "Boolean" -> "true"
        "Int", "Short", "Byte", "Float", "Double" -> "0"
        "Unit" -> "Unit"
        "Long" -> "0L"
        "LocalDate" -> "java.time.LocalDate.now()"
        "LocalDateTime" -> "java.time.LocalDateTime.now()"
        "UUID" -> "java.util.UUID.randomUUID()"
        else -> "TODO()"
    }
}
