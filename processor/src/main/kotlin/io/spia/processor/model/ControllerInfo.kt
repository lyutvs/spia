package io.spia.processor.model

data class ControllerInfo(
    val name: String,
    val basePath: String,
    val endpoints: List<EndpointInfo>,
)

data class EndpointInfo(
    val functionName: String,
    val httpMethod: HttpMethod,
    val path: String,
    val parameters: List<ParameterInfo>,
    val returnType: TypeInfo,
    val jsdoc: String?,
    val errorResponses: Map<Int, TypeInfo> = emptyMap(),
)

data class ParameterInfo(
    val name: String,
    val type: TypeInfo,
    val kind: ParameterKind,
    val required: Boolean = true,
    val defaultValue: String? = null,
    val headerName: String? = null,
)

enum class HttpMethod { GET, POST, PUT, DELETE, PATCH }

enum class ParameterKind { PATH, QUERY, BODY, HEADER, MULTIPART }
