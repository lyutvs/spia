package io.spia.processor.model

sealed class TypeInfo {
    abstract val nullable: Boolean

    data class Primitive(val tsType: String, override val nullable: Boolean = false) : TypeInfo()
    data class Array(val elementType: TypeInfo, override val nullable: Boolean = false) : TypeInfo()
    data class Record(val keyType: TypeInfo, val valueType: TypeInfo, override val nullable: Boolean = false) : TypeInfo()
    data class Enum(val name: String, val constants: List<String>, override val nullable: Boolean = false) : TypeInfo()
    data class Dto(val name: String, val fields: List<FieldInfo>, override val nullable: Boolean = false) : TypeInfo()
    data class Unknown(val rawName: String, override val nullable: Boolean = false) : TypeInfo()

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

    fun withNullable(nullable: Boolean): TypeInfo = when (this) {
        is Primitive -> copy(nullable = nullable)
        is Array -> copy(nullable = nullable)
        is Record -> copy(nullable = nullable)
        is Enum -> copy(nullable = nullable)
        is Dto -> copy(nullable = nullable)
        is Unknown -> copy(nullable = nullable)
        is Generic -> copy(nullable = nullable)
        is TypeParameter -> copy(nullable = nullable)
    }
}

data class FieldInfo(
    val name: String,
    val type: TypeInfo,
)
