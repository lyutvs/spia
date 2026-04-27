package io.spia.processor

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Nullability
import io.spia.processor.model.FieldInfo
import io.spia.processor.model.SdkConfig
import io.spia.processor.model.TypeInfo

class TypeResolver(private val config: SdkConfig) {

    // Pass 1: FQN → TS name. Populated via preRegister(), consulted in resolveCustomType().
    private val dtoNameMap = mutableMapOf<String, String>()
    private val enumNameMap = mutableMapOf<String, String>()

    // Pass 2: FQN → resolved TypeInfo.
    private val resolvedDtos = mutableMapOf<String, TypeInfo.Dto>()
    private val resolvedEnums = mutableMapOf<String, TypeInfo.Enum>()
    private val resolvedGenerics = mutableMapOf<String, TypeInfo.Generic>()

    fun allDtos(): Collection<TypeInfo.Dto> = resolvedDtos.values
    fun allEnums(): Collection<TypeInfo.Enum> = resolvedEnums.values
    fun allGenerics(): Collection<TypeInfo.Generic> = resolvedGenerics.values

    /**
     * Pass 1: pre-register all custom types reachable from [ksType] so that TS names
     * are assigned before any [resolve] call materializes field references. This is
     * required for FQN-based deduplication: if two classes share a simple name across
     * packages, both get package-prefixed disambiguated names, and field references
     * in other DTOs must see the final name.
     */
    fun preRegister(ksType: KSType) {
        val visited = mutableSetOf<String>()
        preRegisterRec(ksType, visited)
    }

    private fun preRegisterRec(ksType: KSType, visited: MutableSet<String>) {
        val declaration = ksType.declaration as? KSClassDeclaration ?: return
        val fqn = declaration.qualifiedName?.asString() ?: return

        if (fqn in visited) return
        visited += fqn

        // Recurse into type arguments regardless (e.g. List<UserDto>).
        ksType.arguments.forEach { arg ->
            arg.type?.resolve()?.let { preRegisterRec(it, visited) }
        }

        if (isStandardType(fqn)) return
        if (dtoNameMap.containsKey(fqn) || enumNameMap.containsKey(fqn)) return

        val simpleName = declaration.simpleName.asString()
        val isEnum = declaration.classKind == ClassKind.ENUM_CLASS

        // Detect simple-name collision across both name maps.
        val collidingFqns = (dtoNameMap.entries + enumNameMap.entries)
            .filter { (otherFqn, otherName) ->
                otherFqn != fqn && simpleOf(otherName) == simpleName
            }
            .map { it.key }

        val assignedName = if (collidingFqns.isEmpty()) simpleName else disambiguate(fqn, simpleName)

        // If a collision occurred with entries that are still using the plain simpleName,
        // retroactively rename those so every colliding FQN is package-prefixed.
        collidingFqns.forEach { otherFqn ->
            val currentName = dtoNameMap[otherFqn] ?: enumNameMap[otherFqn]
            if (currentName == simpleName) {
                val renamed = disambiguate(otherFqn, simpleName)
                if (dtoNameMap.containsKey(otherFqn)) dtoNameMap[otherFqn] = renamed
                if (enumNameMap.containsKey(otherFqn)) enumNameMap[otherFqn] = renamed
            }
        }

        if (isEnum) {
            enumNameMap[fqn] = assignedName
        } else {
            dtoNameMap[fqn] = assignedName
            // Recurse into field types for transitive DTO discovery.
            declaration.getAllProperties().forEach { prop ->
                preRegisterRec(prop.type.resolve(), visited)
            }
        }
    }

    private fun simpleOf(tsName: String): String =
        tsName.substringAfterLast('_')

    private fun disambiguate(fqn: String, simpleName: String): String {
        val pkgPath = fqn.removeSuffix(".$simpleName")
        val lastSegment = pkgPath.substringAfterLast('.', missingDelimiterValue = pkgPath)
        val safePrefix = lastSegment.ifBlank { "pkg" }
        return "${safePrefix}_$simpleName"
    }

    fun resolve(ksType: KSType): TypeInfo {
        val nullable = ksType.nullability == Nullability.NULLABLE
        val declaration = ksType.declaration

        // Generic type parameters (e.g., `T` inside `Page<T>`) are surfaced verbatim
        // so the generator can render them as TS type parameter names.
        if (declaration is KSTypeParameter) {
            val name = declaration.name.asString()
            return TypeInfo.TypeParameter(name, nullable)
        }

        val qualifiedName = declaration.qualifiedName?.asString() ?: return TypeInfo.Unknown("unknown", nullable)
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

            SpringAnnotations.MULTIPART_FILE -> TypeInfo.Primitive("File | Blob")

            "kotlin.Any", "java.lang.Object" -> TypeInfo.Unknown("unknown")

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

        val fqn = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()

        if (declaration.classKind == ClassKind.ENUM_CLASS) {
            resolvedEnums[fqn]?.let { return it }
            val tsName = enumNameMap[fqn] ?: declaration.simpleName.asString()
            val constants = declaration.declarations
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.classKind == ClassKind.ENUM_ENTRY }
                .map { it.simpleName.asString() }
                .toList()
            val enum = TypeInfo.Enum(tsName, constants)
            resolvedEnums[fqn] = enum
            return enum
        }

        val tsName = dtoNameMap[fqn] ?: declaration.simpleName.asString()
        val isGeneric = declaration.typeParameters.isNotEmpty()

        if (isGeneric) {
            // Parameterized class: emit (or reuse) one generic interface definition
            // per FQN regardless of type arguments. At usage sites we substitute
            // arguments in the TS rendering.
            val resolvedArgs = ksType.arguments.map { arg ->
                arg.type?.resolve()?.let { resolve(it) } ?: TypeInfo.Unknown("unknown")
            }
            resolvedGenerics[fqn]?.let { cached ->
                return cached.copy(typeArguments = resolvedArgs)
            }
            val placeholder = TypeInfo.Generic(
                name = tsName,
                typeParameters = declaration.typeParameters.map { it.name.asString() },
                fields = emptyList(),
                typeArguments = emptyList(),
            )
            resolvedGenerics[fqn] = placeholder

            val fields = declaration.getAllProperties().map { prop ->
                val propName = prop.simpleName.asString()
                FieldInfo(
                    name = propName,
                    type = resolve(prop.type.resolve()),
                    serializedName = JacksonAnnotationReader.renamedTo(prop) ?: propName,
                    aliases = JacksonAnnotationReader.aliases(prop),
                    excludeWhenNull = JacksonAnnotationReader.excludeWhenNull(prop),
                )
            }.toList()

            val realDefinition = placeholder.copy(fields = fields)
            resolvedGenerics[fqn] = realDefinition
            return realDefinition.copy(typeArguments = resolvedArgs)
        }

        resolvedDtos[fqn]?.let { return it }

        // Install placeholder before recursing so a circular back-reference returns
        // this entry instead of re-entering and looping.
        val placeholder = TypeInfo.Dto(tsName, emptyList())
        resolvedDtos[fqn] = placeholder

        val fields = declaration.getAllProperties().map { prop ->
            val propName = prop.simpleName.asString()
            FieldInfo(
                name = propName,
                type = resolve(prop.type.resolve()),
                serializedName = JacksonAnnotationReader.renamedTo(prop) ?: propName,
                aliases = JacksonAnnotationReader.aliases(prop),
                excludeWhenNull = JacksonAnnotationReader.excludeWhenNull(prop),
            )
        }.toList()

        // Overwrite the placeholder with the real DTO carrying the fields. This is the
        // fix: getOrPut-based predecessors discarded the lambda's return value and
        // stored the empty placeholder permanently.
        val real = TypeInfo.Dto(tsName, fields)
        resolvedDtos[fqn] = real
        return real
    }

    private fun isStandardType(fqn: String): Boolean = when (fqn) {
        "kotlin.String", "java.lang.String",
        "kotlin.Boolean", "java.lang.Boolean",
        "kotlin.Int", "java.lang.Integer",
        "kotlin.Short", "java.lang.Short",
        "kotlin.Byte", "java.lang.Byte",
        "kotlin.Float", "java.lang.Float",
        "kotlin.Double", "java.lang.Double",
        "kotlin.Long", "java.lang.Long",
        "kotlin.Unit", "java.lang.Void",
        "kotlin.Any", "java.lang.Object",
        "java.time.LocalDate", "java.time.LocalDateTime", "java.time.Instant",
        "java.time.ZonedDateTime", "java.time.OffsetDateTime",
        "java.util.Date", "java.util.UUID",
        "kotlin.collections.List", "kotlin.collections.MutableList",
        "kotlin.collections.Set", "kotlin.collections.MutableSet",
        "kotlin.collections.Collection", "kotlin.collections.MutableCollection",
        "java.util.List", "java.util.ArrayList",
        "java.util.Set", "java.util.HashSet",
        "java.util.Collection",
        "kotlin.collections.Map", "kotlin.collections.MutableMap",
        "java.util.Map", "java.util.HashMap",
        "org.springframework.http.ResponseEntity",
        SpringAnnotations.MULTIPART_FILE -> true
        else -> false
    }
}
