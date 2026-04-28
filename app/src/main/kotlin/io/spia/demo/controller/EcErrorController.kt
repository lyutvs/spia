package io.spia.demo.controller

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

data class BadRequestErrorDto(val message: String, val field: String? = null)
data class NotFoundErrorDto(val message: String, val resource: String? = null)
data class InternalErrorDto(val message: String, val traceId: String? = null)

class BadRequestException(message: String, val field: String? = null) : RuntimeException(message)
class ResourceNotFoundException(message: String, val resource: String? = null) : RuntimeException(message)

@RestControllerAdvice
class EcErrorController {

    @ExceptionHandler(BadRequestException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(ex: BadRequestException): BadRequestErrorDto {
        return BadRequestErrorDto(message = ex.message ?: "Bad request", field = ex.field)
    }

    @ExceptionHandler(ResourceNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(ex: ResourceNotFoundException): NotFoundErrorDto {
        return NotFoundErrorDto(message = ex.message ?: "Not found", resource = ex.resource)
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleInternalError(ex: Exception): InternalErrorDto {
        return InternalErrorDto(message = ex.message ?: "Internal server error")
    }
}
