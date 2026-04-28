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
    @Target(AnnotationTarget.CLASS) annotation class ControllerAdvice
    @Target(AnnotationTarget.CLASS) annotation class RestControllerAdvice
    @Target(AnnotationTarget.FUNCTION) annotation class ExceptionHandler(vararg val value: kotlin.reflect.KClass<*> = [])
    """.trimIndent()
)

/**
 * Spring HTTP status enum stub.
 * Include alongside coreSpringStubs() in tests that use @ResponseStatus.
 */
fun httpStatusStubs(): SourceFile = SourceFile.kotlin(
    "HttpStatusStub.kt",
    """
    package org.springframework.http

    enum class HttpStatus(val value: Int) {
        OK(200),
        CREATED(201),
        NO_CONTENT(204),
        BAD_REQUEST(400),
        UNAUTHORIZED(401),
        FORBIDDEN(403),
        NOT_FOUND(404),
        CONFLICT(409),
        UNPROCESSABLE_ENTITY(422),
        INTERNAL_SERVER_ERROR(500),
        SERVICE_UNAVAILABLE(503),
    }
    """.trimIndent()
)

/**
 * Spring ResponseStatus annotation stub.
 * Requires httpStatusStubs() in the same compilation.
 */
fun responseStatusStubs(): SourceFile = SourceFile.kotlin(
    "ResponseStatusStub.kt",
    """
    package org.springframework.web.bind.annotation

    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    annotation class ResponseStatus(
        val value: org.springframework.http.HttpStatus = org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
        val code: org.springframework.http.HttpStatus = org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
    )
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

/** Stub for {@code reactor.core.publisher.Flux} used by SSE/streaming tests. */
fun reactorStubs(): SourceFile = SourceFile.kotlin(
    "ReactorStubs.kt",
    """
    package reactor.core.publisher
    class Flux<T>
    """.trimIndent()
)

/** Stub for {@code org.springframework.http.codec.ServerSentEvent} used by SSE tests. */
fun sseStubs(): SourceFile = SourceFile.kotlin(
    "SseStubs.kt",
    """
    package org.springframework.http.codec
    class ServerSentEvent<T>
    """.trimIndent()
)

/** Stub for {@code org.springframework.core.io.Resource} used by file download tests. */
fun resourceStubs(): SourceFile = SourceFile.kotlin(
    "ResourceStubs.kt",
    """
    package org.springframework.core.io
    interface Resource
    """.trimIndent()
)

/** Stub for {@code org.springframework.http.ResponseEntity} used by file download tests. */
fun responseEntityStubs(): SourceFile = SourceFile.kotlin(
    "ResponseEntityStubs.kt",
    """
    package org.springframework.http
    class ResponseEntity<T>
    """.trimIndent()
)
