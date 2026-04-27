package io.spia.processor.test_support

import com.tschuchort.compiletesting.SourceFile

/** Stub annotations for com.fasterxml.jackson.annotation used by Jackson smoke tests. */
fun jacksonStubs(): SourceFile = SourceFile.kotlin(
    "JacksonStubs.kt",
    """
    package com.fasterxml.jackson.annotation

    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY,
            AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.CLASS)
    annotation class JsonProperty(val value: String = "")

    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY,
            AnnotationTarget.VALUE_PARAMETER)
    annotation class JsonAlias(vararg val value: String)

    @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY,
            AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.CLASS)
    annotation class JsonInclude(val value: Include = Include.USE_DEFAULTS) {
        enum class Include {
            ALWAYS, NON_NULL, NON_ABSENT, NON_EMPTY, NON_DEFAULT, CUSTOM, USE_DEFAULTS
        }
    }

    @Target(AnnotationTarget.CLASS)
    annotation class JsonTypeInfo(
        val use: Id = Id.NONE,
        val include: As = As.PROPERTY,
        val property: String = "",
    ) {
        enum class Id { NONE, CLASS, MINIMAL_CLASS, NAME, DEDUCTION, CUSTOM }
        enum class As { PROPERTY, WRAPPER_OBJECT, WRAPPER_ARRAY, EXTERNAL_PROPERTY, EXISTING_PROPERTY }
    }

    @Target(AnnotationTarget.CLASS)
    annotation class JsonTypeName(val value: String = "")

    @Target(AnnotationTarget.CLASS)
    annotation class JsonSubTypes(vararg val value: Type) {
        @Target(AnnotationTarget.ANNOTATION_CLASS)
        annotation class Type(val value: kotlin.reflect.KClass<*>, val name: String = "")
    }
    """.trimIndent()
)
