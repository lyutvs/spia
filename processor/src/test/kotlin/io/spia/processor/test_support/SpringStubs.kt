/**
 * Shared KSP compile-testing stub sources. Future stub helpers (validationStubs, reactiveStubs,
 * pageableStubs, jacksonStubs) should be added as top-level functions in this same file/package.
 */
package io.spia.processor.test_support

import com.tschuchort.compiletesting.SourceFile

/** Core Spring Web MVC annotation stubs required by every processor smoke test. */
fun coreSpringStubs(): SourceFile = SourceFile.kotlin(
    "SpringStubs.kt",
    """
    package org.springframework.web.bind.annotation

    @Target(AnnotationTarget.CLASS) annotation class RestController
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class RequestMapping(val value: String = "", val method: Array<String> = [])
    @Target(AnnotationTarget.FUNCTION) annotation class GetMapping(val value: String = "")
    @Target(AnnotationTarget.FUNCTION) annotation class PostMapping(val value: String = "")
    @Target(AnnotationTarget.FUNCTION) annotation class PutMapping(val value: String = "")
    @Target(AnnotationTarget.FUNCTION) annotation class DeleteMapping(val value: String = "")
    @Target(AnnotationTarget.FUNCTION) annotation class PatchMapping(val value: String = "")
    @Target(AnnotationTarget.VALUE_PARAMETER) annotation class PathVariable(val value: String = "", val name: String = "")
    @Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestParam(
        val value: String = "",
        val required: Boolean = true,
        val defaultValue: String = ""
    )
    @Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestBody
    @Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestHeader(val value: String = "", val name: String = "")
    @Target(AnnotationTarget.VALUE_PARAMETER) annotation class RequestPart(val value: String = "", val name: String = "")
    """.trimIndent()
)

/** Stub for {@code org.springframework.web.multipart.MultipartFile} used by multipart upload tests. */
fun parameterStubs(): SourceFile = SourceFile.kotlin(
    "MultipartFileStub.kt",
    """
    package org.springframework.web.multipart

    interface MultipartFile {
        fun getOriginalFilename(): String?
        fun getBytes(): ByteArray
    }
    """.trimIndent()
)
