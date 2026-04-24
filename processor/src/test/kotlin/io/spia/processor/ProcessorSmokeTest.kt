@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.spia.processor

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspIncremental
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.symbolProcessorProviders
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
            sources = listOf(source, springStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
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
            sources = listOf(source, springStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
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
            sources = listOf(source, springStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
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
            sources = listOf(source, springStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
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
            sources = listOf(source, springStubs(), multipartFileStub())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
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
            sources = listOf(source, springStubs())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
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
            sources = listOf(source, springStubs(), multipartFileStub())
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp {
                symbolProcessorProviders.add(SpiaProcessorProvider())
                processorOptions["spia.outputPath"] = outputPath
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

    private fun multipartFileStub(): SourceFile = SourceFile.kotlin(
        "MultipartFileStub.kt",
        """
        package org.springframework.web.multipart

        interface MultipartFile {
            fun getOriginalFilename(): String?
            fun getBytes(): ByteArray
        }
        """.trimIndent()
    )

    private fun springStubs(): SourceFile = SourceFile.kotlin(
        "SpringStubs.kt",
        """
        package org.springframework.web.bind.annotation

        @Target(AnnotationTarget.CLASS) annotation class RestController
        @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
        annotation class RequestMapping(val value: String = "", val method: Array<String> = [])
        @Target(AnnotationTarget.FUNCTION) annotation class GetMapping(val value: String = "")
        @Target(AnnotationTarget.FUNCTION) annotation class PostMapping(val value: String = "")
        @Target(AnnotationTarget.FUNCTION) annotation class PutMapping(val value: String = "")
        @Target(AnnotationTarget.FUNCTION) annotation class DeleteMapping(val value: String = "")
        @Target(AnnotationTarget.FUNCTION) annotation class PatchMapping(val value: String = "")
        @Target(AnnotationTarget.VALUE_PARAMETER) annotation class PathVariable(val value: String = "", val name: String = "")
        @Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestParam(
            val value: String = "",
            val required: Boolean = true,
            val defaultValue: String = ""
        )
        @Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestBody
        @Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestHeader(val value: String = "", val name: String = "")
        @Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestPart(val value: String = "", val name: String = "")
        """.trimIndent()
    )
}
