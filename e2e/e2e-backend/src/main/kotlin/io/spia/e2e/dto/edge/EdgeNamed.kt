package io.spia.e2e.dto.edge

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(value = WithHyphen::class, name = "kebab-case"),
    JsonSubTypes.Type(value = WithDot::class, name = "dotted.name"),
    JsonSubTypes.Type(value = WithLeadingDigit::class, name = "1stKind"),
)
sealed class EdgeNamed

@JsonTypeName("kebab-case")
data class WithHyphen(val a: String) : EdgeNamed()

@JsonTypeName("dotted.name")
data class WithDot(val b: String) : EdgeNamed()

@JsonTypeName("1stKind")
data class WithLeadingDigit(val c: String) : EdgeNamed()
