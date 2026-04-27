package io.spia.processor

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation

/**
 * Reads Jackson serialization annotations from KSP symbol declarations.
 *
 * Supported annotations:
 * - [JacksonAnnotations.JSON_PROPERTY] — overrides the serialized field name
 * - [JacksonAnnotations.JSON_ALIAS] — provides additional deserialization aliases
 * - [JacksonAnnotations.JSON_INCLUDE] — controls null-value inclusion (NON_NULL → optional field)
 *
 * // Task 14 will add: typeProperty, typeNames, subTypes
 */
object JacksonAnnotationReader {

    /**
     * Returns the value of `@JsonProperty("foo")` on [symbol], or null if absent.
     */
    fun renamedTo(symbol: KSAnnotated): String? {
        val annotation = symbol.findAnnotation(JacksonAnnotations.JSON_PROPERTY) ?: return null
        val value = annotation.arguments.firstOrNull { it.name?.asString() == "value" }?.value as? String
        return value?.takeIf { it.isNotBlank() }
    }

    /**
     * Returns the list of alias strings from `@JsonAlias("a", "b")` on [symbol].
     * Returns an empty list if the annotation is absent or has no values.
     */
    fun aliases(symbol: KSAnnotated): List<String> {
        val annotation = symbol.findAnnotation(JacksonAnnotations.JSON_ALIAS) ?: return emptyList()
        val valueArg = annotation.arguments.firstOrNull { it.name?.asString() == "value" }?.value
        return when (valueArg) {
            is List<*> -> valueArg.filterIsInstance<String>()
            is String -> if (valueArg.isNotBlank()) listOf(valueArg) else emptyList()
            else -> emptyList()
        }
    }

    /**
     * Returns true if `@JsonInclude(JsonInclude.Include.NON_NULL)` is present on [symbol].
     */
    fun excludeWhenNull(symbol: KSAnnotated): Boolean {
        val annotation = symbol.findAnnotation(JacksonAnnotations.JSON_INCLUDE) ?: return false
        val valueArg = annotation.arguments.firstOrNull { it.name?.asString() == "value" }?.value
        return valueArg?.toString()?.contains("NON_NULL") == true
    }

    private fun KSAnnotated.findAnnotation(fqn: String): KSAnnotation? =
        annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == fqn }
}
