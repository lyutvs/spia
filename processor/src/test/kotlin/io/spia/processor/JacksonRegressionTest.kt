@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.spia.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.symbolProcessorProviders
import io.spia.processor.test_support.coreSpringStubs
import io.spia.processor.test_support.jacksonStubs
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for G-01 — Jackson annotations on bare Kotlin constructor parameters.
 *
 * Real Jackson Java @Target is {ANNOTATION_TYPE, FIELD, METHOD, PARAMETER}.
 * There is no Kotlin PROPERTY target. When a developer writes:
 *
 *     data class Foo(@JsonProperty("bar") val bar: String)
 *
 * without an explicit @field: use-site target, Kotlin places the annotation on the
 * VALUE_PARAMETER site, NOT the PROPERTY site.  These tests verify that
 * JacksonAnnotationReader resolves both sites via the fallback chain added for G-01.
 */
class JacksonRegressionTest {

    /**
     * G-01 bare ctor-param: @JsonProperty without @field: use-site target must rename the
     * TypeScript property key to match the JSON wire name.
     */
    @Test
    fun `G-01 bare ctor-param @JsonProperty renames TS field without field use-site target`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "BareCtorParamController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.PostMapping
            import org.springframework.web.bind.annotation.RequestBody
            import com.fasterxml.jackson.annotation.JsonProperty
            import com.fasterxml.jackson.annotation.JsonAlias
            import com.fasterxml.jackson.annotation.JsonInclude

            // No @field: use-site target — annotation sits on VALUE_PARAMETER only (G-01).
            data class BareCtorDto(
                @JsonProperty("user_name")
                val userName: String,

                @JsonAlias(value = ["alias_one", "alias_two"])
                val displayName: String,

                @JsonInclude(JsonInclude.Include.NON_NULL)
                val bio: String? = null,
            )

            @RestController
            class BareCtorParamController {
                @PostMapping("/bare-ctor/users")
                fun create(@RequestBody dto: BareCtorDto): BareCtorDto = dto
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // @JsonProperty("user_name") on a bare VALUE_PARAMETER must rename the TS field.
        assertTrue(sdk.contains("user_name: string"), "G-01: @JsonProperty on bare ctor param must rename TS field to user_name")
        assertFalse(sdk.contains("userName: string"), "G-01: Kotlin property name must not appear when @JsonProperty is present")

        // @JsonAlias on a bare VALUE_PARAMETER must emit JSDoc @alias comment.
        assertTrue(sdk.contains("@alias"), "G-01: @JsonAlias on bare ctor param must emit @alias JSDoc")
        assertTrue(sdk.contains("alias_one"), "G-01: alias_one must appear in @alias JSDoc")
        assertTrue(sdk.contains("alias_two"), "G-01: alias_two must appear in @alias JSDoc")

        // @JsonInclude(NON_NULL) on a bare VALUE_PARAMETER must use optional marker.
        assertTrue(sdk.contains("bio?:"), "G-01: @JsonInclude(NON_NULL) on bare ctor param must mark field optional with ?:")
    }

    /**
     * G-01 regression: @field:JsonProperty (explicit use-site target) must still work after
     * the VALUE_PARAMETER fallback was introduced — ensures backward compatibility.
     */
    @Test
    fun `G-01 field use-site @JsonProperty still works after VALUE_PARAMETER fallback added`(@TempDir tempDir: File) {
        val outputPath = File(tempDir, "generated/api-sdk.ts").absolutePath

        val source = SourceFile.kotlin(
            "FieldUseSiteController.kt",
            """
            package test

            import org.springframework.web.bind.annotation.RestController
            import org.springframework.web.bind.annotation.PostMapping
            import org.springframework.web.bind.annotation.RequestBody
            import com.fasterxml.jackson.annotation.JsonProperty
            import com.fasterxml.jackson.annotation.JsonInclude

            // Explicit @field: use-site target — annotation sits on FIELD/PROPERTY site.
            data class FieldUseSiteDto(
                @field:JsonProperty("screen_name")
                val screenName: String,

                @field:JsonInclude(JsonInclude.Include.NON_NULL)
                val avatar: String? = null,
            )

            @RestController
            class FieldUseSiteController {
                @PostMapping("/field-use-site/users")
                fun create(@RequestBody dto: FieldUseSiteDto): FieldUseSiteDto = dto
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
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "compilation should succeed")

        assertTrue(File(outputPath).exists(), "SDK file not generated")
        val sdk = File(outputPath).readText()

        // @field:JsonProperty("screen_name") must rename the TS field (backward compat).
        assertTrue(sdk.contains("screen_name: string"), "G-01 compat: @field:JsonProperty must still rename TS field")
        assertFalse(sdk.contains("screenName: string"), "G-01 compat: Kotlin property name must not appear in output")

        // @field:JsonInclude(NON_NULL) must still mark the field optional (backward compat).
        assertTrue(sdk.contains("avatar?:"), "G-01 compat: @field:JsonInclude(NON_NULL) must still mark field optional")
    }
}
