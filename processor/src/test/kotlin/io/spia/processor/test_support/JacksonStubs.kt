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
    """.trimIndent()
)
