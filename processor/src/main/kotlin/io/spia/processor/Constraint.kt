package io.spia.processor

/**
 * Represents a JSON Schema / OpenAPI 3.1 validation constraint extracted from a Spring DTO field.
 *
 * Fixed data contract — consumed by Zod schema generation (task 07) and OpenAPI emission (task 12).
 * Do NOT change the shape after this task is merged.
 */
data class Constraint(
    val keyword: String,   // OpenAPI/JSON Schema keyword (e.g. "minLength", "maximum", "pattern")
    val value: Any?,       // numeric/string/null
    val message: String?,  // optional human-readable message
)
