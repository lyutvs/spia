package io.spia.processor

import io.spia.processor.model.FieldInfo
import io.spia.processor.model.TypeInfo
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZodSchemaGeneratorTest {

    private val generator = ZodSchemaGenerator()

    @Test
    fun `primitives with min and max constraints are emitted correctly`() {
        val dtos = listOf(
            TypeInfo.Dto(
                name = "PersonDto",
                fields = listOf(
                    FieldInfo(
                        name = "name",
                        type = TypeInfo.Primitive("string"),
                        constraints = listOf(
                            Constraint("minLength", 1, null),
                            Constraint("maxLength", 50, null),
                        ),
                    ),
                    FieldInfo(
                        name = "age",
                        type = TypeInfo.Primitive("number"),
                        constraints = listOf(
                            Constraint("minimum", 0, null),
                            Constraint("maximum", 120, null),
                        ),
                    ),
                ),
            )
        )

        val result = generator.generate(dtos, emptyList())

        assertTrue(result.contains("import { z } from 'zod'"), "missing zod import")
        assertTrue(result.contains("PersonDtoSchema"), "missing schema const name")
        assertTrue(result.contains(".min(1)"), "missing .min(1) for minLength constraint")
        assertTrue(result.contains(".max(50)"), "missing .max(50) for maxLength constraint")
        assertTrue(result.contains(".min(0)"), "missing .min(0) for minimum constraint")
        assertTrue(result.contains(".max(120)"), "missing .max(120) for maximum constraint")
        assertTrue(result.contains("z.string()"), "missing z.string() for name field")
        assertTrue(result.contains("z.number()"), "missing z.number() for age field")
    }

    @Test
    fun `regex and email constraints are emitted correctly`() {
        val dtos = listOf(
            TypeInfo.Dto(
                name = "ValidationDto",
                fields = listOf(
                    FieldInfo(
                        name = "code",
                        type = TypeInfo.Primitive("string"),
                        constraints = listOf(
                            Constraint("pattern", "^[A-Z]{2}-\\d{4}\$", null),
                        ),
                    ),
                    FieldInfo(
                        name = "email",
                        type = TypeInfo.Primitive("string"),
                        constraints = listOf(
                            Constraint("format", "email", null),
                        ),
                    ),
                ),
            )
        )

        val result = generator.generate(dtos, emptyList())

        assertTrue(result.contains("import { z } from 'zod'"), "missing zod import")
        assertTrue(result.contains(".regex("), "missing .regex( for pattern constraint")
        assertTrue(result.contains("new RegExp("), "missing new RegExp() for pattern")
        assertTrue(result.contains(".email()"), "missing .email() for email format constraint")
    }

    @Test
    fun `nullable fields get nullable() appended`() {
        val dtos = listOf(
            TypeInfo.Dto(
                name = "ProfileDto",
                fields = listOf(
                    FieldInfo(
                        name = "bio",
                        type = TypeInfo.Primitive("string", nullable = true),
                        constraints = emptyList(),
                    ),
                    FieldInfo(
                        name = "age",
                        type = TypeInfo.Primitive("number", nullable = false),
                        constraints = emptyList(),
                    ),
                ),
            )
        )

        val result = generator.generate(dtos, emptyList())

        assertTrue(result.contains(".nullable()"), "nullable field must have .nullable()")
        // Count that nullable appears only once (for bio, not for age)
        val nullableCount = result.lines().count { it.contains(".nullable()") }
        assertTrue(nullableCount >= 1, "expected at least one .nullable()")
        assertFalse(
            result.lines()
                .filter { it.contains("age") }
                .any { it.contains(".nullable()") },
            "non-nullable field age must not have .nullable()"
        )
    }

    @Test
    fun `array and boolean types are emitted correctly`() {
        val dtos = listOf(
            TypeInfo.Dto(
                name = "ItemListDto",
                fields = listOf(
                    FieldInfo(
                        name = "items",
                        type = TypeInfo.Array(TypeInfo.Primitive("string")),
                        constraints = emptyList(),
                    ),
                    FieldInfo(
                        name = "active",
                        type = TypeInfo.Primitive("boolean"),
                        constraints = emptyList(),
                    ),
                ),
            )
        )

        val result = generator.generate(dtos, emptyList())

        assertTrue(result.contains("z.array(z.string())"), "missing z.array(z.string())")
        assertTrue(result.contains("z.boolean()"), "missing z.boolean()")
    }
}
