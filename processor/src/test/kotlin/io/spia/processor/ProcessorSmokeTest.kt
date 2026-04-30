@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.spia.processor

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.symbolProcessorProviders
import io.spia.processor.test_support.coreSpringStubs
import io.spia.processor.test_support.flowStubs
import io.spia.processor.test_support.httpStatusStubs
import io.spia.processor.test_support.jacksonStubs
import io.spia.processor.test_support.javaController
import io.spia.processor.test_support.javaDto
import io.spia.processor.test_support.pageableStubs
import io.spia.processor.test_support.parameterStubs
import io.spia.processor.test_support.reactorStubs
import io.spia.processor.test_support.resourceStubs
import io.spia.processor.test_support.responseEntityStubs
import io.spia.processor.test_support.responseStatusStubs
import io.spia.processor.test_support.sseStubs
import io.spia.processor.test_support.validationStubs
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
    fun `EC-13 multi-module lockfile uses tab delimiter and atomic write`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

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

        val lockFile = File("$outputPath.spia-lock")
        assertTrue(lockFile.exists(), "lockfile should exist after two modules write")

        val lockContent = lockFile.readText()
        val lockLines = lockContent.lines().filter { it.isNotBlank() }

        // Assert each non-blank line contains exactly two tab characters
        lockLines.forEach { line ->
            assertEquals(2, line.count { it == '\t' },
                "each lockfile line must contain exactly two tabs, got: ${line.replace("\t", "\\t")}")
        }

        // Assert each non-blank line splits into exactly three parts on tab
        lockLines.forEach { line ->
            assertEquals(3, line.split("\t", limit = 3).size,
                "each lockfile line must split into 3 parts on tab, got: ${line.replace("\t", "\\t")}")
        }

        // Assert no leftover .spia-lock-*.tmp siblings
        val parentDir = lockFile.parentFile
        val tmpFiles = parentDir.listFiles { f -> f.name.startsWith(".spia-lock-") && f.name.endsWith(".tmp") }
        assertTrue(tmpFiles.isNullOrEmpty(), "no leftover .spia-lock-*.tmp temp files should remain")
    }

    @Test
    fun `EC-13 stale colon-delimited lockfile lines are silently dropped on read`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val lockFile = File("$outputPath.spia-lock")
        lockFile.parentFile.mkdirs()
        assertTrue(lockFile.parentFile.exists(), "lockfile parent must exist before seeding")

        // Pre-write an old colon-delimited format line
        lockFile.writeText("module-old:abc123:2026-04-27T10:00:00Z")

        val source = SourceFile.kotlin(
            "UserController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping

            data class User(val id: Long, val name: String)

            @RestController
            class UserController {
                @GetMapping("/users")
                fun listUsers(): List<User> = emptyList()
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
                processorOptions["spia.moduleName"] = "module-new"
                processorOptions["spia.apiClient"] = "axios"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        val lockLines = lockFile.readLines().filter { it.isNotBlank() }

        // Assert lockfile is tab-delimited and contains exactly one entry
        assertEquals(1, lockLines.size, "stale colon-delimited lines should be dropped; only one entry should remain")

        // Assert the surviving entry is tab-delimited
        lockLines.forEach { line ->
            assertEquals(2, line.count { it == '\t' },
                "surviving lockfile line must use tab delimiter, got: ${line.replace("\t", "\\t")}")
        }

        // Assert module-old did not survive
        assertFalse(
            lockLines.any { it.startsWith("module-old") },
            "module-old (stale colon-format entry) must not survive in the new lockfile"
        )
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

        assertTrue(sdk.contains("export function createApi(options?: ClientOptions)"), "createApi ClientOptions signature missing")
        assertTrue(sdk.contains("export interface ClientOptions"), "ClientOptions interface missing")
        assertTrue(sdk.contains("baseUrl?: string"), "ClientOptions.baseUrl field missing")
        assertTrue(sdk.contains("fetch("), "fetch() call missing")
        assertTrue(sdk.contains("JSON.stringify"), "JSON.stringify for POST body missing")
        assertTrue(sdk.contains("res.json()"), "res.json() call missing")
        assertTrue(sdk.contains("if (!res.ok)"), "res.ok guard missing")
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
    fun `EC-12 sealed @JsonTypeName containing single-quote fails KSP compilation`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "SealedSingleQuoteController.kt",
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
                JsonSubTypes.Type(value = ItsCircle::class, name = "it's"),
            )
            sealed class BadShape

            @JsonTypeName("it's")
            data class ItsCircle(val radius: Double) : BadShape()

            @RestController
            @RequestMapping("/api/bad-shapes")
            class SealedSingleQuoteController {
                @GetMapping("/circle")
                fun getCircle(): BadShape = ItsCircle(radius = 5.0)
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
            KotlinCompilation.ExitCode.COMPILATION_ERROR,
            result.exitCode,
            "compilation must fail when @JsonTypeName tag contains a single-quote"
        )
        assertTrue(result.messages.contains("EC-12"), "error message must mention EC-12 marker")
        assertTrue(result.messages.contains("@JsonTypeName"), "error message must mention @JsonTypeName")
    }

    @Test
    fun `EC-12 sealed @JsonTypeName with backtick newline backslash also fails KSP compilation`(@TempDir tempDir: File) {
        // Each unsafe character (backtick, newline, carriage-return, backslash) must independently trigger EC-12.
        val cases = listOf(
            "backtick" to "bad`tag",
            "newline" to "bad\\ntag",
            "carriageReturn" to "bad\\rtag",
            "backslash" to "bad\\\\tag",
        )

        for ((label, badTag) in cases) {
            val outputPath = File(tempDir, "generated/api-sdk-$label.ts").absolutePath

            val source = SourceFile.kotlin(
                "SealedUnsafeController_$label.kt",
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
                    JsonSubTypes.Type(value = UnsafeSubtype::class, name = "$badTag"),
                )
                sealed class UnsafeShape

                @JsonTypeName("$badTag")
                data class UnsafeSubtype(val v: Int) : UnsafeShape()

                @RestController
                @RequestMapping("/api/unsafe-shapes")
                class SealedUnsafeController_$label {
                    @GetMapping("/x")
                    fun getX(): UnsafeShape = UnsafeSubtype(v = 1)
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
                KotlinCompilation.ExitCode.COMPILATION_ERROR,
                result.exitCode,
                "compilation must fail when @JsonTypeName tag contains $label"
            )
            assertTrue(result.messages.contains("EC-12"), "[$label] error message must mention EC-12 marker")
            assertTrue(result.messages.contains("@JsonTypeName"), "[$label] error message must mention @JsonTypeName")
        }
    }

    @Test
    fun `EC-12 sealed @JsonTypeName with safe characters compiles cleanly`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "SealedSafeTagController.kt",
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
                JsonSubTypes.Type(value = SafeVariant::class, name = "my-tag_v2.1"),
            )
            sealed class SafeShape

            @JsonTypeName("my-tag_v2.1")
            data class SafeVariant(val v: Int) : SafeShape()

            @RestController
            @RequestMapping("/api/safe-shapes")
            class SealedSafeTagController {
                @GetMapping("/x")
                fun getX(): SafeShape = SafeVariant(v = 1)
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
            "compilation must succeed when @JsonTypeName tag has only safe characters"
        )
        assertFalse(result.messages.contains("EC-12"), "no EC-12 error must fire for safe tag")

        assertTrue(File(outputPath).exists(), "SDK file not generated for safe-tag sealed union")
        val sdk = File(outputPath).readText()
        assertTrue(sdk.contains("type SafeShape ="), "SafeShape type alias missing")
        assertTrue(sdk.contains("'my-tag_v2.1'"), "discriminator literal 'my-tag_v2.1' missing")
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

    @Test
    fun `EC-11 generated SDK contains ApiError class definition`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "SimpleController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping

            @RestController
            class SimpleController {
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // AC-1: ApiError class must be present in the generated SDK
        assertTrue(sdk.contains("class ApiError"), "class ApiError missing from SDK")
        assertTrue(sdk.contains("extends Error"), "ApiError must extend Error")
        assertTrue(sdk.contains("public status: number"), "ApiError must have public status: number")
        assertTrue(sdk.contains("constructor"), "ApiError must have a constructor")
    }

    @Test
    fun `EC-11 fetch non-2xx throws ApiError not generic Error`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "ThrowController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.PathVariable

            data class Item(val id: Long, val name: String)

            @RestController
            class ThrowController {
                @GetMapping("/items/{id}")
                fun getItem(@PathVariable id: Long): Item = Item(id, "stub")
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        val sdk = File(outputPath).readText()

        // AC-3: non-2xx must throw ApiError, not plain Error
        assertTrue(sdk.contains("throw new ApiError"), "throw new ApiError missing in fetch template")
        assertFalse(sdk.contains("throw new Error("), "throw new Error() should have been replaced by throw new ApiError")
    }

    @Test
    @org.junit.jupiter.api.DisplayName("EC-11b fetch non-JSON error body preserves res.status as ApiError")
    fun `EC-11b fetch non-JSON error body preserves resStatus as ApiError`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "NonJsonErrorController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.PathVariable

            data class Item(val id: Long, val name: String)

            @RestController
            class NonJsonErrorController {
                @GetMapping("/items/{id}")
                fun getItem(@PathVariable id: Long): Item = Item(id, "stub")
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        val sdk = File(outputPath).readText()

        assertTrue(sdk.contains("let __errBody: unknown = null;"), "errBody declaration missing")
        assertTrue(sdk.contains("__errBody = await res.json();"), "res.json() assignment missing")
        assertTrue(sdk.contains("__errBody = await res.text();"), "res.text() fallback missing")
        assertTrue(sdk.contains("throw new ApiError(res.status, __errBody,"), "ApiError with __errBody missing")
        assertTrue(sdk.contains("clearTimeout(__timeoutId);"), "clearTimeout line must not be displaced")
        assertTrue(sdk.contains("throw new ApiError"), "throw new ApiError missing in fetch template")
        assertFalse(sdk.contains("throw new Error("), "throw new Error() should have been replaced by throw new ApiError")
    }

    @Test
    fun `EC-11 ControllerAdvice ExceptionHandler emits typed error aliases`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "ErrorAliasController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RestControllerAdvice
            import org.springframework.web.bind.annotation.ExceptionHandler
            import org.springframework.web.bind.annotation.ResponseStatus
            import org.springframework.http.HttpStatus

            data class NotFoundError(val message: String)
            data class BadRequestError(val field: String, val reason: String)

            @RestControllerAdvice
            class GlobalErrorAdvice {
                @ExceptionHandler(NoSuchElementException::class)
                @ResponseStatus(HttpStatus.NOT_FOUND)
                fun notFound(ex: NoSuchElementException): NotFoundError = NotFoundError(ex.message ?: "not found")

                @ExceptionHandler(IllegalArgumentException::class)
                @ResponseStatus(HttpStatus.BAD_REQUEST)
                fun badRequest(ex: IllegalArgumentException): BadRequestError = BadRequestError("field", ex.message ?: "bad")
            }

            @RestController
            class ErrorAliasController {
                @GetMapping("/items")
                fun getItems(): List<String> = emptyList()
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(source, coreSpringStubs(), httpStatusStubs(), responseStatusStubs())
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        val sdk = File(outputPath).readText()

        // AC-1: ApiError class must still be present
        assertTrue(sdk.contains("class ApiError"), "class ApiError missing from SDK")

        // AC-2: typed error aliases must be emitted for endpoints that have error responses
        assertTrue(sdk.contains("ApiError<"), "ApiError generic usage missing in error alias")
    }

    @Test
    fun `Java controller and POJO - getter derived fields and ecJava method emitted`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val compilation = KotlinCompilation().apply {
            sources = listOf(coreSpringStubs(), javaDto(), javaController())
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
            "compilation should succeed with a Java @RestController"
        )

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // Java controller methods must appear under the ecJava namespace
        assertTrue(sdk.contains("ecJava"), "ecJava namespace missing from generated SDK")

        // Java POJO getter getName() must be mapped to the lowercase field 'name'
        assertTrue(sdk.contains("name:") || sdk.contains("name?: ") || sdk.contains("name: "),
            "getter-derived field 'name' missing from EcJavaDto interface")

        // EcJavaDto interface must be emitted
        assertTrue(sdk.contains("EcJavaDto"), "EcJavaDto interface missing from generated SDK")
    }

    @Test
    fun `fetch createApi emits ClientOptions interface and options parameter`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "PingController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping

            @RestController
            class PingController {
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // ClientOptions interface must be emitted
        assertTrue(sdk.contains("export interface ClientOptions"), "ClientOptions interface missing")
        assertTrue(sdk.contains("baseUrl?: string"), "ClientOptions.baseUrl field missing")

        // createApi must use options?: ClientOptions signature
        assertTrue(sdk.contains("export function createApi(options?: ClientOptions)"), "createApi ClientOptions signature missing")

        // baseUrl fallback: no buildtime baseUrl set, so falls back to ""
        assertTrue(sdk.contains("options?.baseUrl ??"), "baseUrl fallback chain missing")
    }

    @Test
    fun `bean validation constraints surface as JSDoc in generated TS`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val compilation = KotlinCompilation().apply {
            sources = listOf(coreSpringStubs()) + validationStubs()
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        assertTrue(sdk.contains("@minLength 1"), "@minLength 1 JSDoc tag missing")
        assertTrue(sdk.contains("@maxLength 50"), "@maxLength 50 JSDoc tag missing")
        assertTrue(sdk.contains("Wrapper"), "Wrapper type missing from generated output")
        // Wrapper<T>'s name field also has @minLength 1 (from Size(min=1, max=30))
        assertTrue(
            sdk.lines().count { it.contains("@minLength 1") } >= 2,
            "Expected at least 2 @minLength 1 occurrences (ValRequest.name and ValWrapper.name)"
        )
    }

    @Test
    fun `EC-09 authInterceptor + retry are emitted in ClientOptions and fetch body`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "EcAuthRetryController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RequestMapping
            import org.springframework.web.bind.annotation.RequestHeader

            data class AuthRetryResponse(val message: String)

            @RestController
            @RequestMapping("/api/ec-auth-retry")
            class EcAuthRetryController {
                @GetMapping("/protected")
                fun protectedEndpoint(@RequestHeader("Authorization") authorization: String): AuthRetryResponse =
                    AuthRetryResponse("ok")
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "EC-09: compilation should succeed")

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        assertTrue(sdk.contains("authInterceptor?:"), "authInterceptor?: field missing from ClientOptions")
        assertTrue(sdk.contains("retry?:"), "retry?: field missing from ClientOptions")
        assertTrue(sdk.contains("RequestInit"), "RequestInit must appear in authInterceptor signature")

        assertTrue(sdk.contains("options?.retry?.maxAttempts ?? 0"), "options?.retry?.maxAttempts ?? 0 pattern missing")
        assertTrue(sdk.contains("options?.retry?.backoffMs ?? 200"), "backoffMs default 200 missing")
        assertTrue(sdk.contains("s >= 500") || sdk.contains("status >= 500"), "retryOn default s >= 500 missing")

        assertTrue(sdk.contains("instanceof ApiError"), "instanceof ApiError missing — must catch ApiError not base Error")
        assertTrue(sdk.contains("error.status") || sdk.contains("__retryOn(error.status)"),
            "error.status missing — must inspect error.status for retry decision")

        // 503 -> retry (status >= 500), 400 -> no retry path
        assertTrue(
            sdk.contains("__retryOn(error.status) && __attempt < __maxAttempts"),
            "retry condition must check __retryOn(error.status) for 503 retry / 400 no retry distinction"
        )
    }

    @Test
    fun `SSE Flux returns AsyncIterable and Resource returns Blob`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "StreamingController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.http.ResponseEntity
            import org.springframework.http.codec.ServerSentEvent
            import org.springframework.core.io.Resource
            import reactor.core.publisher.Flux

            data class Tick(val seq: Long)

            @RestController
            class StreamingController {
                @GetMapping("/ticks")
                fun ticks(): Flux<ServerSentEvent<Tick>> = throw NotImplementedError()

                @GetMapping("/file")
                fun file(): ResponseEntity<Resource> = throw NotImplementedError()
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(
                source,
                coreSpringStubs(),
                reactorStubs(),
                sseStubs(),
                resourceStubs(),
                responseEntityStubs(),
            )
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

        val output = File(outputPath).readText()
        assertTrue(output.contains("AsyncIterable<Tick>"), "expected AsyncIterable in:\n$output")
        assertTrue(output.contains("Blob"), "expected Blob in:\n$output")
    }

    @Test
    fun `EC-15 CookieValue parameter generates cookies optional Record param`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "CookieController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.CookieValue

            @RestController
            class CookieController {
                @GetMapping("/whoami")
                fun whoami(@CookieValue("session-id") sessionId: String): String = "stub"
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // @CookieValue must produce a cookies?: Record<string, string> opt parameter
        assertTrue(sdk.contains("cookies"), "cookies parameter missing from @CookieValue endpoint")
        assertTrue(sdk.contains("Record<string, string>"), "cookies Record type missing")
    }

    @Test
    fun `EC-15 ModelAttribute DTO fields flattened to query params`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "ModelAttributeController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.ModelAttribute

            data class SearchFilter(val keyword: String, val page: Int = 0, val size: Int = 20)

            @RestController
            class ModelAttributeController {
                @GetMapping("/search")
                fun search(@ModelAttribute filter: SearchFilter): List<String> = emptyList()
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // @ModelAttribute DTO fields must be flattened to individual params, not nested object
        assertTrue(sdk.contains("keyword"), "ModelAttribute DTO field 'keyword' missing from params")
        // The individual field names should appear as query-string params, not the DTO name
        assertFalse(sdk.contains("filter: SearchFilter"), "DTO param should be flattened, not passed as object")
    }

    @Test
    fun `EC-15 RequestAttribute server-side only — excluded from TS signature with warn`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "RequestAttributeController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RequestAttribute

            @RestController
            class RequestAttributeController {
                @GetMapping("/req-attr")
                fun getAttr(@RequestAttribute("requestId") requestId: String): String = "stub"
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // @RequestAttribute parameter must NOT appear in the generated TS signature
        assertFalse(sdk.contains("requestId: string"), "@RequestAttribute param should be excluded from TS signature")

        // A warn must be emitted in the build output (server-side only)
        assertTrue(
            result.messages.contains("RequestAttribute") || result.messages.contains("server-side"),
            "Expected warn about @RequestAttribute server-side-only. Got:\n${result.messages}"
        )
    }

    @Test
    fun `EC-15 MatrixVariable treated as query string parameter`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "MatrixVariableController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.PathVariable
            import org.springframework.web.bind.annotation.MatrixVariable

            @RestController
            class MatrixVariableController {
                @GetMapping("/items/{id}")
                fun getItem(
                    @PathVariable id: Long,
                    @MatrixVariable color: String,
                ): String = "stub"
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // @MatrixVariable param must appear in the generated TS signature
        assertTrue(sdk.contains("color"), "MatrixVariable param 'color' missing from TS signature")
    }

    @Test
    fun `fetch createApi baseUrl priority - buildtime config baseUrl is used when set`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "PingController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping

            @RestController
            class PingController {
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
                processorOptions["spia.clientOptions.baseUrl"] = "/api"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed with buildtime baseUrl")

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // buildtime baseUrl "/api" must be baked into the fallback chain
        assertTrue(sdk.contains("\"/api\""), "buildtime baseUrl not baked into generated SDK")
        // options?.baseUrl takes priority over buildtime config
        assertTrue(sdk.contains("options?.baseUrl ??"), "baseUrl fallback chain missing")
    }

    @Test
    fun `EC-10 timeout AbortSignal - generated fetch code uses AbortSignal any and signal parameter`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "TimeoutController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RequestMapping

            data class SlowResponse(val message: String)

            @RestController
            @RequestMapping("/api/timeout")
            class TimeoutController {
                @GetMapping("/slow")
                fun slow(): SlowResponse = SlowResponse("done")
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // AbortSignal.any must appear in the generated fetch code
        assertTrue(sdk.contains("AbortSignal.any"), "AbortSignal.any missing — timeout+signal combining required")

        // Every endpoint must have signal?: AbortSignal as last parameter
        assertTrue(
            sdk.contains("signal?: AbortSignal"),
            "signal?: AbortSignal parameter missing from endpoint signature"
        )

        // ApiTimeoutError must be defined
        assertTrue(sdk.contains("ApiTimeoutError"), "ApiTimeoutError class missing from generated SDK")

        // timeoutMs must appear in ClientOptions
        assertTrue(sdk.contains("timeoutMs"), "timeoutMs missing from ClientOptions")

        // AbortController must be used
        assertTrue(sdk.contains("AbortController"), "AbortController missing from fetch body")
    }

    @Test
    fun `splitByController emits per-controller files plus index and _shared`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath
        val outputDir = File(outputPath).parentFile

        val sourceA = SourceFile.kotlin(
            "UserController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.RequestMapping
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.PathVariable

            data class UserDto(val id: Long, val name: String)

            @RestController
            @RequestMapping("/users")
            class UserController {
                @GetMapping("/{id}")
                fun getUser(@PathVariable id: Long): UserDto = UserDto(id, "stub")
            }
            """.trimIndent()
        )

        val sourceB = SourceFile.kotlin(
            "OrderController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping

            data class OrderDto(val sku: String, val qty: Int)

            @RestController
            class OrderController {
                @GetMapping("/orders")
                fun listOrders(): List<OrderDto> = emptyList()
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(sourceA, sourceB, coreSpringStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
                processorOptions["spia.apiClient"] = "fetch"
                processorOptions["spia.splitByController"] = "true"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed with splitByController")

        val indexFile = File(outputDir, "index.ts")
        val sharedFile = File(outputDir, "_shared.ts")
        val userApiFile = File(outputDir, "user.api.ts")
        val orderApiFile = File(outputDir, "order.api.ts")

        assertTrue(indexFile.exists(), "index.ts must exist in split mode at ${indexFile.absolutePath}")
        assertTrue(sharedFile.exists(), "_shared.ts must exist in split mode at ${sharedFile.absolutePath}")
        assertTrue(userApiFile.exists(), "user.api.ts must exist (per-controller file)")
        assertTrue(orderApiFile.exists(), "order.api.ts must exist (per-controller file)")

        val indexContent = indexFile.readText()
        assertTrue(indexContent.contains("export *"), "index.ts must contain `export *` re-exports")
        assertTrue(
            indexContent.contains("from './user.api'") || indexContent.contains("from './order.api'"),
            "index.ts must re-export the per-controller .api modules"
        )

        val sharedContent = sharedFile.readText()
        assertTrue(sharedContent.contains("class ApiError"), "_shared.ts must contain the shared ApiError class")

        val userApiContent = userApiFile.readText()
        assertTrue(
            userApiContent.contains("export {") || userApiContent.contains("export *"),
            "per-controller files must re-export shared symbols"
        )
    }

    @Test
    fun `EC-11 Kotlin value class emitted as branded TS type with constructor helper`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "ValueClassController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RequestMapping
            import org.springframework.web.bind.annotation.PathVariable

            @JvmInline
            value class UserId(val raw: String)

            data class UserDto(val id: UserId, val name: String)

            @RestController
            @RequestMapping("/api/ec11")
            class ValueClassController {
                @GetMapping("/users/{id}")
                fun getUser(@PathVariable id: String): UserDto =
                    UserDto(id = UserId(id), name = "stub")
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
            "compilation should succeed with a value class in the controller"
        )

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // Branded type definition must be present
        assertTrue(sdk.contains("__brand"), "branded type __brand token missing from SDK")
        assertTrue(
            sdk.contains("type UserId = string & { readonly __brand: 'UserId' }"),
            "branded type alias for UserId missing: $sdk"
        )

        // Constructor helper arrow function must be present
        assertTrue(
            sdk.contains("const UserId = (raw: string): UserId => raw as UserId"),
            "constructor helper for UserId missing: $sdk"
        )

        // UserDto interface must reference UserId as branded type (not raw string)
        assertTrue(sdk.contains("interface UserDto"), "UserDto interface missing")
        assertTrue(sdk.contains("id: UserId"), "UserDto.id should reference UserId branded type")
    }

    @Test
    fun `EC-06 Pageable param expands to inline page size sort query fields`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "EcPageableController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RequestMapping
            import org.springframework.data.domain.Pageable
            import org.springframework.data.domain.Page

            data class ItemDto(val id: Long, val name: String)

            @RestController
            @RequestMapping("/api/ec/pageable")
            class EcPageableController {
                @GetMapping
                fun list(pageable: Pageable): Page<ItemDto> = TODO()
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
                processorOptions["spia.apiClient"] = "fetch"
                incremental = false
            }
        }

        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "EC-06: compilation should succeed")

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // Pageable must expand into the three optional query fields
        assertTrue(sdk.contains("page?: number"), "page?: number param missing from Pageable endpoint")
        assertTrue(sdk.contains("size?: number"), "size?: number param missing from Pageable endpoint")
        assertTrue(sdk.contains("sort?: string"), "sort?: string param missing from Pageable endpoint")

        // The serialization must include page, size, sort in the query string builder
        assertTrue(
            sdk.contains("page") && (sdk.contains("params.append") || sdk.contains("encodeURIComponent")),
            "page query serialization missing from Pageable endpoint"
        )
        assertTrue(
            sdk.contains("size") && (sdk.contains("params.append") || sdk.contains("encodeURIComponent")),
            "size query serialization missing from Pageable endpoint"
        )
        assertTrue(
            sdk.contains("sort") && (sdk.contains("params.append") || sdk.contains("encodeURIComponent")),
            "sort query serialization missing from Pageable endpoint"
        )
    }

    @Test
    fun `Mono and Flux and suspend fun unwrap reactive return types`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "EcReactiveController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.GetMapping
            import org.springframework.web.bind.annotation.RequestMapping
            import reactor.core.publisher.Mono
            import reactor.core.publisher.Flux

            data class UserDto(val id: String, val name: String)

            @RestController
            @RequestMapping("/api/ec-reactive")
            class EcReactiveController {
                @GetMapping("/mono")
                fun getMono(): Mono<UserDto> = throw NotImplementedError()

                @GetMapping("/flux")
                fun getFlux(): Flux<UserDto> = throw NotImplementedError()

                @GetMapping("/suspend")
                suspend fun getSuspend(): UserDto = throw NotImplementedError()
            }
            """.trimIndent()
        )

        val compilation = KotlinCompilation().apply {
            sources = listOf(
                source,
                coreSpringStubs(),
                reactorStubs(),
            )
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // Mono<UserDto> → Promise<UserDto> (single value unwrap)
        assertTrue(
            sdk.contains("Promise<UserDto>"),
            "Mono<UserDto> should unwrap to Promise<UserDto> in:\n$sdk"
        )
        // Flux<UserDto> → Promise<UserDto[]> (multi-value array unwrap)
        assertTrue(
            sdk.contains("Promise<UserDto[]>"),
            "Flux<UserDto> should unwrap to Promise<UserDto[]> in:\n$sdk"
        )
        // SSE Flux<ServerSentEvent<Tick>> must still produce AsyncIterable<Tick> — verified in separate test
        // Confirm AsyncIterable<Tick> is NOT here (this controller has no SSE)
        assertFalse(
            sdk.contains("AsyncIterable<Tick>"),
            "plain Flux should not produce AsyncIterable in:\n$sdk"
        )
    }

}
