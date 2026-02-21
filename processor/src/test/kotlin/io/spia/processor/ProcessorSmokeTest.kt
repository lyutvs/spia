@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.spia.processor

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspIncremental
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
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
            symbolProcessorProviders = mutableListOf<SymbolProcessorProvider>(SpiaProcessorProvider())
            kspIncremental = false
            inheritClassPath = true
            messageOutputStream = System.out
            kspProcessorOptions["spia.outputPath"] = outputPath
        }

        val result = compilation.compile()
        assertEquals(
            KotlinCompilation.ExitCode.OK,
            result.exitCode,
            "compilation should succeed with the processor on the KSP classpath"
        )
        assertTrue(
            compilation.symbolProcessorProviders.any { it is SpiaProcessorProvider },
            "SpiaProcessorProvider should be registered"
        )
    }

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
        @Target(AnnotationTarget.VALUE_PARAMETER) annotation class PathVariable(val value: String = "")
        @Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestParam(
            val value: String = "",
            val required: Boolean = true,
            val defaultValue: String = ""
        )
        @Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestBody
        @Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestHeader(val value: String = "")
        """.trimIndent()
    )
}
