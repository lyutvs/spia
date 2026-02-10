package io.spia.processor

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Nullability
import io.spia.processor.model.FieldInfo
import io.spia.processor.model.SdkConfig
import io.spia.processor.model.TypeInfo

class TypeResolver(private val config: SdkConfig) {

    private val resolvedDtos = mutableMapOf<String, TypeInfo.Dto>()
    private val resolvedEnums = mutableMapOf<String, TypeInfo.Enum>()

    fun allDtos(): Collection<TypeInfo.Dto> = resolvedDtos.values
    fun allEnums(): Collection<TypeInfo.Enum> = resolvedEnums.values

    fun resolve(ksType: KSType): TypeInfo {
        val nullable = ksType.nullability == Nullability.NULLABLE
        val qualifiedName = ksType.declaration.qualifiedName?.asString() ?: return TypeInfo.Unknown("unknown", nullable)

        val base = resolveByName(qualifiedName, ksType)
        return if (nullable) base.withNullable(true) else base
    }

    private fun resolveByName(qualifiedName: String, ksType: KSType): TypeInfo {
        return when (qualifiedName) {
            "kotlin.String", "java.lang.String" -> TypeInfo.Primitive("string")
            "kotlin.Boolean", "java.lang.Boolean" -> TypeInfo.Primitive("boolean")
            "kotlin.Int", "java.lang.Integer",
            "kotlin.Short", "java.lang.Short",
            "kotlin.Byte", "java.lang.Byte",
            "kotlin.Float", "java.lang.Float",
            "kotlin.Double", "java.lang.Double" -> TypeInfo.Primitive("number")

            "kotlin.Long", "java.lang.Long" -> when (config.longType) {
                io.spia.processor.model.LongType.NUMBER -> TypeInfo.Primitive("number")
                io.spia.processor.model.LongType.STRING -> TypeInfo.Primitive("string")
                io.spia.processor.model.LongType.BIGINT -> TypeInfo.Primitive("bigint")
            }

            "kotlin.Unit", "java.lang.Void" -> TypeInfo.Primitive("void")

            "java.time.LocalDate", "java.time.LocalDateTime", "java.time.Instant",
            "java.time.ZonedDateTime", "java.time.OffsetDateTime",
            "java.util.Date", "java.util.UUID" -> TypeInfo.Primitive("string")

            "kotlin.collections.List", "kotlin.collections.MutableList",
            "kotlin.collections.Set", "kotlin.collections.MutableSet",
            "kotlin.collections.Collection", "kotlin.collections.MutableCollection",
            "java.util.List", "java.util.ArrayList",
            "java.util.Set", "java.util.HashSet",
            "java.util.Collection" -> {
                val elementType = ksType.arguments.firstOrNull()?.type?.resolve()
                if (elementType != null) TypeInfo.Array(resolve(elementType))
                else TypeInfo.Array(TypeInfo.Primitive("any"))
            }

            "kotlin.collections.Map", "kotlin.collections.MutableMap",
            "java.util.Map", "java.util.HashMap" -> {
                val args = ksType.arguments
                val keyType = args.getOrNull(0)?.type?.resolve()?.let { resolve(it) } ?: TypeInfo.Primitive("string")
                val valueType = args.getOrNull(1)?.type?.resolve()?.let { resolve(it) } ?: TypeInfo.Primitive("any")
                TypeInfo.Record(keyType, valueType)
            }

            "org.springframework.http.ResponseEntity" -> {
                val inner = ksType.arguments.firstOrNull()?.type?.resolve()
                if (inner != null) resolve(inner) else TypeInfo.Primitive("any")
            }

            else -> resolveCustomType(ksType)
        }
    }

    private fun resolveCustomType(ksType: KSType): TypeInfo {
        val declaration = ksType.declaration
        if (declaration !is KSClassDeclaration) {
            return TypeInfo.Unknown(declaration.simpleName.asString())
        }

        val name = declaration.simpleName.asString()

        if (declaration.classKind == ClassKind.ENUM_CLASS) {
            resolvedEnums.getOrPut(name) {
                val constants = declaration.declarations
                    .filterIsInstance<KSClassDeclaration>()
                    .filter { it.classKind == ClassKind.ENUM_ENTRY }
                    .map { it.simpleName.asString() }
                    .toList()
                TypeInfo.Enum(name, constants)
            }
            return TypeInfo.Enum(name, resolvedEnums[name]!!.constants)
        }

        // DTO: data class or regular class with properties
        resolvedDtos.getOrPut(name) {
            // Create a placeholder first to handle circular references
            val placeholder = TypeInfo.Dto(name, emptyList())
            resolvedDtos[name] = placeholder

            val fields = declaration.getAllProperties().map { prop ->
                FieldInfo(
                    name = prop.simpleName.asString(),
                    type = resolve(prop.type.resolve()),
                )
            }.toList()

            TypeInfo.Dto(name, fields)
        }

        return TypeInfo.Dto(name, resolvedDtos[name]!!.fields)
    }
}
