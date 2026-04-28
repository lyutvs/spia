package io.spia.processor

import io.spia.processor.model.*

/**
 * Generates an OpenAPI 3.1.0 JSON document from controller/DTO metadata.
 * Uses only Kotlin stdlib — no third-party JSON library.
 */
object OpenApiGenerator {

    fun generate(
        controllers: List<ControllerInfo>,
        dtos: List<TypeInfo.Dto> = emptyList(),
        enums: List<TypeInfo.Enum> = emptyList(),
        generics: List<TypeInfo.Generic> = emptyList(),
        title: String = "Spia API",
        version: String = "1.0.0",
    ): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"openapi\": \"3.1.0\",\n")
        sb.append("  \"info\": {\n")
        sb.append("    \"title\": ${jsonString(title)},\n")
        sb.append("    \"version\": ${jsonString(version)}\n")
        sb.append("  },\n")

        // paths
        sb.append("  \"paths\": {\n")
        val pathEntries = buildPathEntries(controllers)
        pathEntries.entries.forEachIndexed { idx, (path, methodMap) ->
            sb.append("    ${jsonString(path)}: {\n")
            methodMap.entries.forEachIndexed { mIdx, (method, opObj) ->
                sb.append("      \"$method\": $opObj")
                if (mIdx < methodMap.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("    }")
            if (idx < pathEntries.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  },\n")

        // components
        sb.append("  \"components\": {\n")
        sb.append("    \"schemas\": {\n")

        val schemaEntries = buildSchemaEntries(dtos, enums, generics)
        schemaEntries.entries.forEachIndexed { idx, (name, schemaObj) ->
            sb.append("      ${jsonString(name)}: $schemaObj")
            if (idx < schemaEntries.size - 1) sb.append(",")
            sb.append("\n")
        }

        sb.append("    },\n")
        sb.append("    \"responses\": {\n")
        sb.append("      \"ApiError\": {\n")
        sb.append("        \"description\": \"An API error response\",\n")
        sb.append("        \"content\": {\n")
        sb.append("          \"application/json\": {\n")
        sb.append("            \"schema\": {\n")
        sb.append("              \"type\": \"object\",\n")
        sb.append("              \"properties\": {\n")
        sb.append("                \"status\": { \"type\": \"integer\" },\n")
        sb.append("                \"message\": { \"type\": \"string\" }\n")
        sb.append("              }\n")
        sb.append("            }\n")
        sb.append("          }\n")
        sb.append("        }\n")
        sb.append("      }\n")
        sb.append("    }\n")
        sb.append("  }\n")
        sb.append("}\n")
        return sb.toString()
    }

    // ── Path building ─────────────────────────────────────────────────────────

    private fun buildPathEntries(controllers: List<ControllerInfo>): LinkedHashMap<String, LinkedHashMap<String, String>> {
        val paths = LinkedHashMap<String, LinkedHashMap<String, String>>()
        for (controller in controllers.sortedBy { it.name }) {
            for (endpoint in controller.endpoints) {
                val fullPath = combinePaths(controller.basePath, endpoint.path)
                // Convert Spring {var} to OpenAPI {var} (strip regex qualifiers {id:[0-9]+} → {id})
                val openApiPath = fullPath.replace(Regex("\\{(\\w+):[^}]+\\}"), "{$1}")
                val methodMap = paths.getOrPut(openApiPath) { LinkedHashMap() }
                methodMap[endpoint.httpMethod.name.lowercase()] = buildOperationObject(endpoint, fullPath)
            }
        }
        return paths
    }

    private fun buildOperationObject(endpoint: EndpointInfo, fullPath: String): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("          \"operationId\": ${jsonString(endpoint.functionName)},\n")

        // parameters (path + query + header)
        val paramList = endpoint.parameters.filter {
            it.kind in listOf(ParameterKind.PATH, ParameterKind.QUERY, ParameterKind.HEADER, ParameterKind.MATRIX_VARIABLE)
        }
        if (paramList.isNotEmpty()) {
            sb.append("          \"parameters\": [\n")
            paramList.forEachIndexed { idx, p ->
                sb.append("            {\n")
                sb.append("              \"name\": ${jsonString(p.name)},\n")
                val location = when (p.kind) {
                    ParameterKind.PATH -> "path"
                    ParameterKind.HEADER -> "header"
                    else -> "query"
                }
                sb.append("              \"in\": \"$location\",\n")
                sb.append("              \"required\": ${if (p.kind == ParameterKind.PATH || p.required) "true" else "false"},\n")
                sb.append("              \"schema\": ${typeInfoToSchema(p.type)}\n")
                sb.append("            }")
                if (idx < paramList.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("          ],\n")
        }

        // requestBody
        val bodyParam = endpoint.parameters.firstOrNull { it.kind == ParameterKind.BODY }
        if (bodyParam != null) {
            sb.append("          \"requestBody\": {\n")
            sb.append("            \"required\": true,\n")
            sb.append("            \"content\": {\n")
            sb.append("              \"application/json\": {\n")
            sb.append("                \"schema\": ${typeInfoToSchema(bodyParam.type)}\n")
            sb.append("              }\n")
            sb.append("            }\n")
            sb.append("          },\n")
        }

        // responses
        sb.append("          \"responses\": {\n")
        sb.append("            \"200\": {\n")
        sb.append("              \"description\": \"Success\",\n")
        sb.append("              \"content\": {\n")
        sb.append("                \"application/json\": {\n")
        sb.append("                  \"schema\": ${typeInfoToSchema(endpoint.returnType)}\n")
        sb.append("                }\n")
        sb.append("              }\n")
        sb.append("            }\n")
        sb.append("          }\n")
        sb.append("        }")
        return sb.toString()
    }

    // ── Schema building ───────────────────────────────────────────────────────

    private fun buildSchemaEntries(
        dtos: List<TypeInfo.Dto>,
        enums: List<TypeInfo.Enum>,
        generics: List<TypeInfo.Generic>,
    ): LinkedHashMap<String, String> {
        val schemas = LinkedHashMap<String, String>()
        for (dto in dtos.sortedBy { it.name }) {
            schemas[dto.name] = buildDtoSchema(dto)
        }
        for (enum in enums.sortedBy { it.name }) {
            schemas[enum.name] = buildEnumSchema(enum)
        }
        for (generic in generics.sortedBy { it.name }) {
            schemas[generic.name] = buildGenericSchema(generic)
        }
        return schemas
    }

    private fun buildDtoSchema(dto: TypeInfo.Dto): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("        \"type\": \"object\",\n")

        val requiredFields = dto.fields.filter { !it.type.nullable }
        if (requiredFields.isNotEmpty()) {
            sb.append("        \"required\": [")
            sb.append(requiredFields.joinToString(", ") { jsonString(it.serializedName) })
            sb.append("],\n")
        }

        sb.append("        \"properties\": {\n")
        dto.fields.forEachIndexed { idx, field ->
            sb.append("          ${jsonString(field.serializedName)}: ${buildFieldSchema(field)}")
            if (idx < dto.fields.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("        }\n")
        sb.append("      }")
        return sb.toString()
    }

    private fun buildEnumSchema(enum: TypeInfo.Enum): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("        \"type\": \"string\",\n")
        sb.append("        \"enum\": [")
        sb.append(enum.constants.joinToString(", ") { jsonString(it) })
        sb.append("]\n")
        sb.append("      }")
        return sb.toString()
    }

    private fun buildGenericSchema(generic: TypeInfo.Generic): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("        \"type\": \"object\",\n")

        val requiredFields = generic.fields.filter { !it.type.nullable }
        if (requiredFields.isNotEmpty()) {
            sb.append("        \"required\": [")
            sb.append(requiredFields.joinToString(", ") { jsonString(it.serializedName) })
            sb.append("],\n")
        }

        sb.append("        \"properties\": {\n")
        generic.fields.forEachIndexed { idx, field ->
            sb.append("          ${jsonString(field.serializedName)}: ${buildFieldSchema(field)}")
            if (idx < generic.fields.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("        }\n")
        sb.append("      }")
        return sb.toString()
    }

    private fun buildFieldSchema(field: FieldInfo): String {
        val base = typeInfoToSchemaInline(field.type)
        if (field.constraints.isEmpty()) return base

        // Merge constraints into the schema object
        val constraintMap = LinkedHashMap<String, Any>()
        for (c in field.constraints) {
            when (c.keyword) {
                "minLength", "maxLength", "minimum", "maximum", "minItems", "maxItems" -> {
                    val v = c.value
                    if (v != null) constraintMap[c.keyword] = v
                }
                "pattern" -> {
                    val v = c.value
                    if (v != null) constraintMap[c.keyword] = v
                }
                "format" -> {
                    val v = c.value
                    if (v != null) constraintMap[c.keyword] = v
                }
            }
        }

        if (constraintMap.isEmpty()) return base

        // We need to merge constraints into the base schema object
        // base is a JSON string ending with "}" — inject constraint fields before the closing brace
        val trimmed = base.trimEnd()
        return if (trimmed.endsWith("}")) {
            val withoutClose = trimmed.dropLast(1).trimEnd()
            val sep = if (withoutClose.trimEnd().endsWith(",") || withoutClose.trimEnd().endsWith("{")) "" else ","
            val constraintStr = constraintMap.entries.joinToString(", ") { (k, v) ->
                "\"$k\": ${constraintValueToJson(v)}"
            }
            "$withoutClose$sep $constraintStr }"
        } else {
            base
        }
    }

    private fun constraintValueToJson(value: Any): String = when (value) {
        is Number -> value.toString()
        is String -> jsonString(value)
        else -> jsonString(value.toString())
    }

    private fun typeInfoToSchema(type: TypeInfo): String = typeInfoToSchemaInline(type)

    private fun typeInfoToSchemaInline(type: TypeInfo): String = when (type) {
        is TypeInfo.Primitive -> primitiveToSchema(type.tsType, type.nullable)
        is TypeInfo.Array -> {
            val items = typeInfoToSchemaInline(type.elementType)
            if (type.nullable) "{ \"type\": [\"array\", \"null\"], \"items\": $items }"
            else "{ \"type\": \"array\", \"items\": $items }"
        }
        is TypeInfo.Record -> {
            val values = typeInfoToSchemaInline(type.valueType)
            if (type.nullable) "{ \"type\": [\"object\", \"null\"], \"additionalProperties\": $values }"
            else "{ \"type\": \"object\", \"additionalProperties\": $values }"
        }
        is TypeInfo.Dto -> {
            if (type.nullable) "{ \"\$ref\": \"#/components/schemas/${type.name}\", \"nullable\": true }"
            else "{ \"\$ref\": \"#/components/schemas/${type.name}\" }"
        }
        is TypeInfo.Enum -> {
            if (type.nullable) "{ \"\$ref\": \"#/components/schemas/${type.name}\", \"nullable\": true }"
            else "{ \"\$ref\": \"#/components/schemas/${type.name}\" }"
        }
        is TypeInfo.Generic -> {
            if (type.nullable) "{ \"\$ref\": \"#/components/schemas/${type.name}\", \"nullable\": true }"
            else "{ \"\$ref\": \"#/components/schemas/${type.name}\" }"
        }
        is TypeInfo.SealedUnion -> {
            if (type.nullable) "{ \"\$ref\": \"#/components/schemas/${type.name}\", \"nullable\": true }"
            else "{ \"\$ref\": \"#/components/schemas/${type.name}\" }"
        }
        is TypeInfo.ValueClass -> typeInfoToSchemaInline(type.underlying)
        is TypeInfo.TypeParameter -> "{ \"type\": \"object\" }"
        is TypeInfo.StreamType -> {
            val items = typeInfoToSchemaInline(type.item)
            "{ \"type\": \"array\", \"items\": $items }"
        }
        is TypeInfo.Unknown -> "{ \"type\": \"object\" }"
    }

    private fun primitiveToSchema(tsType: String, nullable: Boolean): String {
        val jsonType = when (tsType) {
            "string" -> "string"
            "number" -> "number"
            "boolean" -> "boolean"
            "integer" -> "integer"
            else -> when {
                tsType.contains("int", ignoreCase = true) || tsType == "long" || tsType == "short" || tsType == "byte" -> "integer"
                tsType == "float" || tsType == "double" -> "number"
                else -> "string"
            }
        }
        return if (nullable) "{ \"type\": [\"$jsonType\", \"null\"] }"
        else "{ \"type\": \"$jsonType\" }"
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private fun combinePaths(base: String, path: String): String {
        val normalizedBase = base.trimEnd('/')
        val normalizedPath = path.trimStart('/')
        return if (normalizedPath.isEmpty()) normalizedBase
        else "$normalizedBase/$normalizedPath"
    }

    private fun jsonString(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
}
