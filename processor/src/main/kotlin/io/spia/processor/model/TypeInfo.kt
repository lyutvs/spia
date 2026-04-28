package io.spia.processor.model

sealed class TypeInfo {
    abstract val nullable: Boolean

    data class Primitive(val tsType: String, override val nullable: Boolean = false) : TypeInfo()
    data class Array(val elementType: TypeInfo, override val nullable: Boolean = false) : TypeInfo()
    data class Record(val keyType: TypeInfo, val valueType: TypeInfo, override val nullable: Boolean = false) : TypeInfo()
    data class Enum(val name: String, val constants: List<String>, override val nullable: Boolean = false) : TypeInfo()
    data class Dto(val name: String, val fields: List<FieldInfo>, override val nullable: Boolean = false) : TypeInfo()
    data class Unknown(val rawName: String, override val nullable: Boolean = false) : TypeInfo()

    /** Server-Sent Events stream — rendered as `AsyncIterable<T>` in TS. */
    data class StreamType(val item: TypeInfo, override val nullable: Boolean = false) : TypeInfo()

    /**
     * A parameterized DTO (e.g., `class Page<T>(val content: List<T>, ...)`). The
     * interface definition uses [typeParameters] as placeholder names; [fields] may
     * reference those placeholders via [TypeParameter]. At a usage site the generator
     * substitutes [typeArguments] into the interface name (`Page<UserDto>`).
     */
    data class Generic(
        val name: String,
        val typeParameters: List<String>,
        val fields: List<FieldInfo>,
        val typeArguments: List<TypeInfo> = emptyList(),
        override val nullable: Boolean = false,
    ) : TypeInfo()

    /** Reference to a declared generic parameter (e.g., `T` inside `Page<T>`). */
    data class TypeParameter(val name: String, override val nullable: Boolean = false) : TypeInfo()

    /**
     * A Kotlin `sealed class` rendered as a TypeScript discriminated union.
     *
     * @param name          The TypeScript type alias name (simple class name).
     * @param subtypes      Ordered list of subtype entries, each carrying the resolved [Dto]
     *                      and the discriminator tag value (from `@JsonTypeName` or simple name fallback).
     * @param discriminator The JSON property name used as the discriminator (from `@JsonTypeInfo.property`),
     *                      or null if `@JsonTypeInfo` was absent (renders as a plain union without literals).
     */
    data class SealedUnion(
        val name: String,
        val subtypes: List<SealedSubtype>,
        val discriminator: String?,
        override val nullable: Boolean = false,
    ) : TypeInfo()

    fun withNullable(nullable: Boolean): TypeInfo = when (this) {
        is Primitive -> copy(nullable = nullable)
        is Array -> copy(nullable = nullable)
        is Record -> copy(nullable = nullable)
        is Enum -> copy(nullable = nullable)
        is Dto -> copy(nullable = nullable)
        is Unknown -> copy(nullable = nullable)
        is Generic -> copy(nullable = nullable)
        is TypeParameter -> copy(nullable = nullable)
        is SealedUnion -> copy(nullable = nullable)
        is StreamType -> copy(nullable = nullable)
    }
}

/**
 * A single entry in a [TypeInfo.SealedUnion] subtype list.
 *
 * @param dto The resolved DTO representing the subtype's fields.
 * @param tag The discriminator literal value (e.g., `"circle"`) or null if no discriminator.
 */
data class SealedSubtype(val dto: TypeInfo.Dto, val tag: String?)

data class FieldInfo(
    val name: String,
    val type: TypeInfo,
    val serializedName: String = name,
    val aliases: List<String> = emptyList(),
    val excludeWhenNull: Boolean = false,
    val constraints: List<io.spia.processor.Constraint> = emptyList(),
)
