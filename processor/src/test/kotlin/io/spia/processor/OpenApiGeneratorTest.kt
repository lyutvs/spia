package io.spia.processor

import io.spia.processor.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenApiGeneratorTest {

    private fun sampleDtos(): List<TypeInfo.Dto> = listOf(
        TypeInfo.Dto(
            name = "UserDto",
            fields = listOf(
                FieldInfo(
                    name = "name",
                    type = TypeInfo.Primitive("string"),
                    constraints = listOf(
                        Constraint(keyword = "minLength", value = 1, message = null),
                        Constraint(keyword = "maxLength", value = 50, message = null),
                    )
                ),
                FieldInfo(
                    name = "age",
                    type = TypeInfo.Primitive("integer"),
                    constraints = listOf(
                        Constraint(keyword = "minimum", value = 0, message = null),
                        Constraint(keyword = "maximum", value = 120, message = null),
                    )
                ),
            )
        )
    )

    private fun sampleControllers(): List<ControllerInfo> = listOf(
        ControllerInfo(
            name = "UserController",
            basePath = "/users",
            endpoints = listOf(
                EndpointInfo(
                    functionName = "getUser",
                    httpMethod = HttpMethod.GET,
                    path = "/{id}",
                    parameters = listOf(
                        ParameterInfo(name = "id", type = TypeInfo.Primitive("string"), kind = ParameterKind.PATH)
                    ),
                    returnType = TypeInfo.Dto("UserDto", emptyList()),
                    jsdoc = null,
                )
            )
        )
    )

    @Test
    fun `generate produces openapi 3_1_0 version field`() {
        val result = OpenApiGenerator.generate(
            controllers = sampleControllers(),
            dtos = sampleDtos(),
        )

        assertTrue(result.contains("\"openapi\""), "openapi field missing")
        assertTrue(result.contains("3.1.0"), "3.1.0 version string missing")
        assertTrue(result.contains("\"paths\""), "paths missing")
        assertTrue(result.contains("\"components\""), "components missing")
        assertTrue(result.contains("\"schemas\""), "schemas missing")
    }

    @Test
    fun `generate version field equals 3_1_0`() {
        val result = OpenApiGenerator.generate(
            controllers = sampleControllers(),
            dtos = sampleDtos(),
        )

        // Simple JSON parsing: find "openapi" key and extract its value
        val openApiValue = extractJsonStringValue(result, "openapi")
        assertEquals("3.1.0", openApiValue, "openapi field must equal 3.1.0")
    }

    @Test
    fun `generate includes paths from controllers`() {
        val result = OpenApiGenerator.generate(
            controllers = sampleControllers(),
            dtos = sampleDtos(),
        )

        assertTrue(result.contains("paths"), "paths key missing")
        assertTrue(result.contains("/users"), "controller base path missing from paths")
        assertTrue(result.contains("getUser"), "operationId getUser missing")
    }

    @Test
    fun `generate includes constraint keywords in component schemas`() {
        val result = OpenApiGenerator.generate(
            controllers = sampleControllers(),
            dtos = sampleDtos(),
        )

        assertTrue(result.contains("\"minLength\""), "minLength constraint missing from schemas")
        assertTrue(result.contains("\"maxLength\""), "maxLength constraint missing from schemas")
        assertTrue(result.contains("\"minimum\""), "minimum constraint missing from schemas")
        assertTrue(result.contains("\"maximum\""), "maximum constraint missing from schemas")
    }

    @Test
    fun `generate includes minimum and maximum for numeric constraints`() {
        val dtoWithNum = listOf(
            TypeInfo.Dto(
                name = "NumDto",
                fields = listOf(
                    FieldInfo(
                        name = "count",
                        type = TypeInfo.Primitive("integer"),
                        constraints = listOf(
                            Constraint(keyword = "minimum", value = 0, message = null),
                        )
                    )
                )
            )
        )
        val result = OpenApiGenerator.generate(dtos = dtoWithNum)
        assertTrue(result.contains("minimum"), "minimum missing")
    }

    @Test
    fun `generate includes pattern constraint`() {
        val dtoWithPattern = listOf(
            TypeInfo.Dto(
                name = "PatternDto",
                fields = listOf(
                    FieldInfo(
                        name = "code",
                        type = TypeInfo.Primitive("string"),
                        constraints = listOf(
                            Constraint(keyword = "pattern", value = "^[a-z]+$", message = null),
                        )
                    )
                )
            )
        )
        val result = OpenApiGenerator.generate(dtos = dtoWithPattern)
        assertTrue(result.contains("\"pattern\""), "pattern keyword missing")
        assertTrue(result.contains("^[a-z]+\$"), "pattern value missing")
    }

    @Test
    fun `generate includes info title and version`() {
        val result = OpenApiGenerator.generate(
            title = "My API",
            version = "2.0.0",
        )
        assertTrue(result.contains("My API"), "info title missing")
        assertTrue(result.contains("2.0.0"), "info version missing")
    }

    @Test
    fun `generate produces valid json structure`() {
        val result = OpenApiGenerator.generate(
            controllers = sampleControllers(),
            dtos = sampleDtos(),
        )

        // Basic JSON validity checks
        assertTrue(result.trimStart().startsWith("{"), "JSON must start with {")
        assertTrue(result.trimEnd().endsWith("}") || result.trimEnd().endsWith("}\n"), "JSON must end with }")
        // Balanced braces
        val opens = result.count { it == '{' }
        val closes = result.count { it == '}' }
        assertEquals(opens, closes, "Unbalanced braces in JSON output")
    }

    // Simple helper: extract the string value for a top-level JSON key
    private fun extractJsonStringValue(json: String, key: String): String? {
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]+)\"")
        return pattern.find(json)?.groupValues?.get(1)
    }
}
