@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.spia.processor

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.symbolProcessorProviders
import io.spia.processor.test_support.coreSpringStubs
import io.spia.processor.test_support.jacksonStubs
import io.spia.processor.test_support.parameterStubs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessorSmokeTest {

    @Test
    fun `compilation with SpiaProcessorProvider completes successfully`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "UserController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.RequestMapping
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.PathVariable

            data class User(val id: Long, val name: String)

            @RestController
            @RequestMapping("/users")
            class UserController {
                @GetMapping("/{id}")
                fun getUser(@PathVariable id: Long): User = User(id, "stub")
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
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "compilation should succeed with the processor on the KSP classpath"
        )
    }

    @Test
    fun `kotlin Any return type maps to unknown and generic nullable uses pipe null`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "AnyAndGenericController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping

            data class Wrapper<T>(val data: T?, val name: String)

            @RestController
            class AnyAndGenericController {
                @GetMapping("/any")
                fun anyReturn(): Any = "stub"

                @GetMapping("/wrapped")
                fun wrapped(): Wrapper<String> = Wrapper("hello", "test")
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
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "compilation should succeed"
        )

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        assertFalse(sdk.contains("interface Any {"), "empty interface Any should not exist")
        assertTrue(sdk.contains("unknown"), "Any should map to TS unknown")
        assertFalse(Regex("data\\?:").containsMatchIn(sdk), "generic nullable should not use ?:")
        assertTrue(sdk.contains("data: ") && sdk.contains("| null"), "generic nullable should use | null")
    }

    @Test
    fun `EC-08 regex PathVariable substitution`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "EcPathVariablePatternsController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.RequestMapping
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.PathVariable

            @RestController
            @RequestMapping("/sandbox/ec08")
            class EcPathVariablePatternsController {
                @GetMapping("/regex/{id:[0-9]+}")
                fun getById(@PathVariable id: Long): Long = id

                @GetMapping("/multi/{userId}/items/{itemId}")
                fun getItem(@PathVariable userId: Long, @PathVariable itemId: Long): Long = userId + itemId

                @GetMapping("/mixed-regex/{userId:[0-9]+}/items/{itemId:[a-z]+}")
                fun getMixed(@PathVariable userId: Long, @PathVariable itemId: String): String = "${'$'}{userId}-${'$'}{itemId}"
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
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "compilation should succeed"
        )

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        assertFalse(sdk.contains("[0-9]+"), "regex pattern leaked into URL")
        assertFalse(sdk.contains("[a-z]+"), "regex pattern leaked into URL")
        assertFalse(sdk.contains("{id:"), "Spring path constraint syntax leaked")
        assertFalse(sdk.contains("{userId:"), "Spring path constraint syntax leaked")
        assertFalse(sdk.contains("{itemId:"), "Spring path constraint syntax leaked")
        assertTrue(sdk.contains("\${encodeURIComponent(String(id))}"), "id should be substituted")
        assertTrue(sdk.contains("\${encodeURIComponent(String(userId))}"))
    }

    @Test
    fun `EC-08 PathVariable custom binding name`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "CustomBindingController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.RequestMapping
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.PathVariable

            @RestController
            @RequestMapping("/sandbox/ec08-custom")
            class CustomBindingController {
                @GetMapping("/custom/{userId}")
                fun getUser(@PathVariable("userId") id: Long): Long = id
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
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "compilation should succeed"
        )

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        assertTrue(sdk.contains("userId"), "binding name should be reflected in TS output")
    }

    @Test
    fun `EC-03 multipart file upload`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "MultipartController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.PostMapping
            import org.springframework.web.bind.annotation.RequestParam
            import org.springframework.web.bind.annotation.RequestPart
            import org.springframework.web.multipart.MultipartFile

            @RestController
            class MultipartController {
                @PostMapping("/upload")
                fun upload(@RequestPart("file") file: MultipartFile, @RequestParam description: String): String = ""

                @PostMapping("/multi")
                fun multi(@RequestPart("files") files: List<MultipartFile>): String = ""
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
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "compilation should succeed"
        )

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        assertTrue(sdk.contains("file: File | Blob"), "single file param signature missing")
        assertTrue(sdk.contains("files:") && sdk.contains("(File | Blob)[]"), "multi-file param signature missing")
        assertTrue(sdk.contains("FormData"), "FormData not generated")
        assertFalse(sdk.contains("interface MultipartFile"), "empty MultipartFile interface should not exist")
    }

    @Test
    fun `EC-07 request header transmission`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "HeaderController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RequestHeader

            @RestController
            class HeaderController {
                @GetMapping("/x")
                fun x(@RequestHeader("X-Trace-Id") traceId: String, @RequestHeader simpleHeader: String): String = ""
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
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "compilation should succeed"
        )

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        assertTrue(sdk.contains("traceId: string"), "traceId param signature missing")
        assertTrue(sdk.contains("'X-Trace-Id': traceId"), "X-Trace-Id header key missing")
        assertTrue(sdk.contains("'simpleHeader': simpleHeader"), "fallback param name key missing")
        assertTrue(sdk.contains("headers:"), "axios headers config missing")
    }

    @Test
    fun `EC-03 + EC-07 mixed`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "MixedController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.PostMapping
            import org.springframework.web.bind.annotation.RequestPart
            import org.springframework.web.bind.annotation.RequestHeader
            import org.springframework.web.multipart.MultipartFile

            @RestController
            class MixedController {
                @PostMapping("/mixed")
                fun mixed(@RequestPart("file") file: MultipartFile, @RequestHeader("X-Auth") token: String): String = ""
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
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "compilation should succeed"
        )

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        assertTrue(sdk.contains("FormData"), "FormData not generated for multipart")
        assertTrue(sdk.contains("'X-Auth': token"), "X-Auth header key missing")
        assertTrue(sdk.contains("file: File | Blob"), "multipart file param signature missing")
    }

    @Test
    fun `EC-10 single module writes lockfile but no warning`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "SingleModuleController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping

            @RestController
            class SingleModuleController {
                @GetMapping("/single")
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
                processorOptions["spia.moduleName"] = "single-module"
                processorOptions["spia.apiClient"] = "axios"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        val lockFile = File("$outputPath.spia-lock")
        assertTrue(lockFile.exists(), "lockfile should be created for single module")
        assertFalse(
            result.messages.contains("EC-10"),
            "single-module build should not emit an EC-10 conflict warning"
        )
    }

    @Test
    fun `EC-10 multi-module outputPath conflict emits warning and writes spia-lock`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        // Module A: UserController
        val sourceA = SourceFile.kotlin(
            "ModuleAController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping

            data class UserA(val id: Long, val name: String)

            @RestController
            class ModuleAController {
                @GetMapping("/module-a/users")
                fun listUsers(): List<UserA> = emptyList()
            }
            """.trimIndent()
        )

        val compilationA = KotlinCompilation().apply {
            sources = listOf(sourceA, coreSpringStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
                processorOptions["spia.moduleName"] = "module-a"
                processorOptions["spia.apiClient"] = "axios"
                incremental = false
            }
        }

        val resultA = compilationA.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, resultA.exitCode, "module-a compilation should succeed")

        val lockFile = File("$outputPath.spia-lock")
        assertTrue(lockFile.exists(), "lockfile should exist after module-a writes")

        // Module B: different controller / different generated content → different digest
        val sourceB = SourceFile.kotlin(
            "ModuleBController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping

            data class ProductB(val sku: String, val price: Double)

            @RestController
            class ModuleBController {
                @GetMapping("/module-b/products")
                fun listProducts(): List<ProductB> = emptyList()
            }
            """.trimIndent()
        )

        val compilationB = KotlinCompilation().apply {
            sources = listOf(sourceB, coreSpringStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
                processorOptions["spia.moduleName"] = "module-b"
                processorOptions["spia.apiClient"] = "axios"
                incremental = false
            }
        }

        val resultB = compilationB.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, resultB.exitCode, "module-b compilation should succeed")

        // The lockfile must still exist (and now contain two entries)
        assertTrue(lockFile.exists(), "spia-lock sidecar must exist after multi-module writes")

        // The second compilation must have emitted a conflict warning (EC-10)
        assertTrue(
            resultB.messages.contains("EC-10"),
            "Expected EC-10 conflict warning in module-b compilation messages. Got:\n${resultB.messages}"
        )
        assertTrue(
            resultB.messages.contains("module-a"),
            "Warning should mention the conflicting module name 'module-a'. Got:\n${resultB.messages}"
        )

        // Lockfile should have two entries (one per module)
        val lockLines = lockFile.readLines().filter { it.isNotBlank() }
        assertEquals(2, lockLines.size, "lockfile should have exactly 2 entries after two different modules write")
    }

    @Test
    fun `fetch emits createApi baseUrl string signature and fetch call`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "UserController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.RequestMapping
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.PathVariable
            import org.springframework.web.bind.annotation.PostMapping
            import org.springframework.web.bind.annotation.RequestBody
            import org.springframework.web.bind.annotation.RequestParam

            data class User(val id: Long, val name: String)

            @RestController
            @RequestMapping("/users")
            class UserController {
                @GetMapping("/{id}")
                fun getUser(@PathVariable id: Long): User = User(id, "stub")

                @PostMapping
                fun createUser(@RequestBody user: User): User = user

                // has-optional buildFetchQuery branch — must emit URLSearchParams prelude + conditional qs fragment.
                @GetMapping("/search")
                fun search(
                    @RequestParam(required = false) page: Int?,
                    @RequestParam(required = false) size: Int?,
                    @RequestParam(required = false) keyword: String?,
                ): List<User> = emptyList()

                // all-required buildFetchQuery branch — must emit inline encodeURIComponent form.
                @GetMapping("/count")
                fun count(@RequestParam active: Boolean): Int = 0
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
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "compilation should succeed with fetch client"
        )

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        assertTrue(sdk.contains("export function createApi(baseUrl: string)"), "createApi signature missing")
        assertTrue(sdk.contains("fetch("), "fetch() call missing")
        assertTrue(sdk.contains("JSON.stringify"), "JSON.stringify for POST body missing")
        assertTrue(sdk.contains("res.json()"), "res.json() call missing")
        assertTrue(sdk.contains("if (!res.ok) throw"), "res.ok guard missing")
        assertTrue(sdk.contains("\${res.url}"), "error message must use resolved \${res.url}, not the route template")

        // has-optional @RequestParam branch: URLSearchParams prelude + conditional ?qs fragment.
        assertTrue(sdk.contains("const params = new URLSearchParams();"), "URLSearchParams prelude missing for optional query params")
        assertTrue(sdk.contains("if (page !== undefined) params.append('page', String(page));"), "optional page append missing")
        assertTrue(sdk.contains("if (keyword !== undefined) params.append('keyword', String(keyword));"), "optional keyword append missing")
        assertTrue(sdk.contains("const qs = params.toString();"), "qs extraction missing")
        assertTrue(sdk.contains("\${qs ? `?\${qs}` : ''}"), "conditional ?qs URL fragment missing")

        // all-required @RequestParam branch: inline `?k=\${encodeURIComponent(...)}` form, no prelude.
        assertTrue(
            sdk.contains("?active=\${encodeURIComponent(String(active))}"),
            "all-required query params must use inline encodeURIComponent form",
        )

        // Kotlin-side interpolation leak guard: method and path must be baked in at generation time
        assertFalse(sdk.contains("\${method}"), "un-interpolated Kotlin \${method} template leaked into TS output")
        assertFalse(sdk.contains("\${path}"), "un-interpolated Kotlin \${path} template leaked into TS output")
        // Old IIFE form must not leak back in.
        assertFalse(sdk.contains("(() => { const _p = new URLSearchParams"), "legacy IIFE query form leaked into TS output")
    }

    @Test
    fun `EC-08 sealed class rendered as discriminated union via @JsonTypeInfo`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "SealedController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RequestMapping
            import com.fasterxml.jackson.annotation.JsonTypeInfo
            import com.fasterxml.jackson.annotation.JsonTypeName
            import com.fasterxml.jackson.annotation.JsonSubTypes

            @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
            @JsonSubTypes(
                JsonSubTypes.Type(value = Circle::class, name = "circle"),
                JsonSubTypes.Type(value = Square::class, name = "square"),
                JsonSubTypes.Type(value = Triangle::class, name = "triangle"),
            )
            sealed class Shape

            @JsonTypeName("circle")
            data class Circle(val radius: Double) : Shape()

            @JsonTypeName("square")
            data class Square(val side: Double) : Shape()

            @JsonTypeName("triangle")
            data class Triangle(val base: Double, val height: Double) : Shape()

            @RestController
            @RequestMapping("/api/shapes")
            class SealedController {
                @GetMapping("/circle")
                fun getCircle(): Shape = Circle(radius = 5.0)
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
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "compilation should succeed with sealed hierarchy"
        )

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // The sealed union type alias must be emitted
        assertTrue(sdk.contains("type Shape ="), "Shape discriminated union type alias missing")

        // Each subtype interface must be emitted
        assertTrue(sdk.contains("interface Circle"), "Circle interface missing")
        assertTrue(sdk.contains("interface Square"), "Square interface missing")
        assertTrue(sdk.contains("interface Triangle"), "Triangle interface missing")

        // Discriminator literals must appear
        assertTrue(sdk.contains("'circle'"), "discriminator literal 'circle' missing")
        assertTrue(sdk.contains("'square'"), "discriminator literal 'square' missing")
        assertTrue(sdk.contains("'triangle'"), "discriminator literal 'triangle' missing")

        // The intersection form with the discriminator property must be present
        assertTrue(sdk.contains("kind"), "discriminator property 'kind' missing from union")

        // Subtype interfaces must not also appear as stand-alone DTO interfaces outside the union block
        // (they should be declared exactly once, inline inside renderSealedUnion)
        assertFalse(sdk.contains("interface Shape {"), "Shape should be a type alias, not an interface")
    }

    @Test
    fun `EC-10 Jackson annotations - JsonProperty renames field, JsonAlias emits jsdoc, JsonInclude marks optional`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "JacksonController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.PostMapping
            import org.springframework.web.bind.annotation.RequestBody
            import com.fasterxml.jackson.annotation.JsonProperty
            import com.fasterxml.jackson.annotation.JsonAlias
            import com.fasterxml.jackson.annotation.JsonInclude

            data class JacksonDto(
                @JsonProperty("user_name")
                @JsonAlias(value = ["name", "userName"])
                val userName: String,

                @JsonInclude(JsonInclude.Include.NON_NULL)
                val bio: String? = null,
            )

            @RestController
            class JacksonController {
                @PostMapping("/jackson/users")
                fun create(@RequestBody dto: JacksonDto): JacksonDto = dto
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
                processorOptions["spia.apiClient"] = "fetch"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "compilation should succeed"
        )

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // @JsonProperty("user_name") must rename Kotlin property userName to user_name in TS
        assertTrue(sdk.contains("user_name: string"), "@JsonProperty rename not applied")
        assertFalse(sdk.contains("userName: string"), "Kotlin property name should not appear in output")

        // @JsonAlias must emit a JSDoc comment with the alias names
        assertTrue(sdk.contains("@alias"), "@JsonAlias JSDoc comment missing")
        assertTrue(sdk.contains("name") && sdk.contains("userName"), "alias values missing from JSDoc")

        // @JsonInclude(NON_NULL) on a nullable field must use optional marker (?:)
        assertTrue(sdk.contains("bio?:"), "@JsonInclude(NON_NULL) nullable field should be optional with ?:")
    }

}
