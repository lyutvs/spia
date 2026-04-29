package io.spia.processor

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration

/**
 * Reads Jackson serialization annotations from KSP symbol declarations.
 *
 * Supported annotations:
 * - [JacksonAnnotations.JSON_PROPERTY] — overrides the serialized field name
 * - [JacksonAnnotations.JSON_ALIAS] — provides additional deserialization aliases
 * - [JacksonAnnotations.JSON_INCLUDE] — controls null-value inclusion (NON_NULL → optional field)
 * - [JacksonAnnotations.JSON_TYPE_INFO] — discriminator property name for sealed hierarchies
 * - [JacksonAnnotations.JSON_TYPE_NAME] — per-subclass discriminator value
 *
 * ## Kotlin use-site targets and bare ctor-param annotations (G-01)
 *
 * Real Jackson annotation targets are `{ANNOTATION_TYPE, FIELD, METHOD, PARAMETER}` — there is no
 * `PROPERTY` target.  When a developer writes:
 *
 * ```kotlin
 * data class Foo(@JsonProperty("bar") val bar: String)
 * ```
 *
 * without an explicit `@field:` use-site target, Kotlin places the annotation on the
 * `VALUE_PARAMETER` site (visible via `KSValueParameter.annotations`), **not** on the
 * `PROPERTY` site (visible via `KSPropertyDeclaration.annotations`).
 *
 * [findAnnotation] therefore applies a two-step fallback when called on a
 * [KSPropertyDeclaration]:
 *
 * 1. Check the PROPERTY-site annotations (covers `@field:JsonProperty(…)` style).
 * 2. If not found, resolve the owning class's primary constructor and check the
 *    matching [KSValueParameter] by name (covers bare ctor-param annotations).
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

    /**
     * Returns the discriminator property name from `@JsonTypeInfo(property = "type")` on [cls],
     * or null if the annotation is absent (callers may fall back to a nominal union).
     * Defaults to `"type"` if the annotation is present but `property` is not set.
     */
    fun sealedDiscriminator(cls: KSClassDeclaration): String? {
        val annotation = cls.findAnnotation(JacksonAnnotations.JSON_TYPE_INFO) ?: return null
        val property = annotation.arguments.firstOrNull { it.name?.asString() == "property" }?.value as? String
        return if (property.isNullOrBlank()) "type" else property
    }

    /**
     * Returns the discriminator value for [subclass] from `@JsonTypeName("value")`,
     * or falls back to the simple class name if the annotation is absent.
     */
    fun sealedTypeName(subclass: KSClassDeclaration): String {
        val annotation = subclass.findAnnotation(JacksonAnnotations.JSON_TYPE_NAME) ?: return subclass.simpleName.asString()
        val value = annotation.arguments.firstOrNull { it.name?.asString() == "value" }?.value as? String
        return if (value.isNullOrBlank()) subclass.simpleName.asString() else value
    }

    /**
     * Finds [fqn] annotation on [this].
     *
     * For [KSPropertyDeclaration], applies a two-step fallback chain:
     * 1. PROPERTY-site annotations (covers `@field:JsonProperty(…)`).
     * 2. Matching primary-constructor VALUE_PARAMETER annotations (covers bare
     *    `@JsonProperty(…)` on ctor params — the most common real-world pattern).
     *
     * This mirrors real Jackson behaviour: Jackson's Java `@Target` includes `PARAMETER`
     * but not Kotlin `PROPERTY`, so bare ctor-param annotations never reach the
     * PROPERTY site in KSP.
     */
    private fun KSAnnotated.findAnnotation(fqn: String): KSAnnotation? {
        // Step 1: PROPERTY-site (handles @field:JsonProperty and class-level annotations).
        val propertySite = annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == fqn
        }
        if (propertySite != null) return propertySite

        // Step 2: For KSPropertyDeclaration, fall back to the matching primary-constructor
        // VALUE_PARAMETER (handles bare @JsonProperty on ctor params, G-01).
        if (this !is KSPropertyDeclaration) return null
        val ownerClass = parentDeclaration as? KSClassDeclaration ?: return null
        val propName = simpleName.asString()
        val ctorParam = ownerClass.primaryConstructor?.parameters
            ?.firstOrNull { it.name?.asString() == propName }
            ?: return null
        return ctorParam.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == fqn
        }
    }
}
