package io.spia.processor.model

data class SdkConfig(
    val outputPath: String?,
    val enumStyle: EnumStyle,
    val longType: LongType,
    val apiClient: ApiClient,
    val baseUrl: String? = null,
    val schemaOutput: SchemaOutput = SchemaOutput.NONE,
)

enum class EnumStyle { UNION, ENUM }
enum class LongType { NUMBER, STRING, BIGINT }
enum class ApiClient { AXIOS, FETCH }
enum class SchemaOutput { ZOD, NONE }
