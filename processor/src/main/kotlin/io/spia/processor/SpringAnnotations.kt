package io.spia.processor

object SpringAnnotations {
    const val REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController"
    const val REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping"
    const val GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping"
    const val POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping"
    const val PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping"
    const val DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping"
    const val PATCH_MAPPING = "org.springframework.web.bind.annotation.PatchMapping"
    const val PATH_VARIABLE = "org.springframework.web.bind.annotation.PathVariable"
    const val REQUEST_BODY = "org.springframework.web.bind.annotation.RequestBody"
    const val REQUEST_PARAM = "org.springframework.web.bind.annotation.RequestParam"
    const val REQUEST_HEADER = "org.springframework.web.bind.annotation.RequestHeader"
    const val REQUEST_PART = "org.springframework.web.bind.annotation.RequestPart"
    const val MULTIPART_FILE = "org.springframework.web.multipart.MultipartFile"
    const val RESPONSE_STATUS = "org.springframework.web.bind.annotation.ResponseStatus"
    const val EXCEPTION_HANDLER = "org.springframework.web.bind.annotation.ExceptionHandler"
    const val CONTROLLER_ADVICE = "org.springframework.web.bind.annotation.ControllerAdvice"
    const val REST_CONTROLLER_ADVICE = "org.springframework.web.bind.annotation.RestControllerAdvice"
    const val MODEL_ATTRIBUTE = "org.springframework.web.bind.annotation.ModelAttribute"
    const val COOKIE_VALUE = "org.springframework.web.bind.annotation.CookieValue"
    const val REQUEST_ATTRIBUTE = "org.springframework.web.bind.annotation.RequestAttribute"
    const val MATRIX_VARIABLE = "org.springframework.web.bind.annotation.MatrixVariable"

    const val NOT_NULL = "jakarta.validation.constraints.NotNull"
    const val SIZE = "jakarta.validation.constraints.Size"
    const val MIN = "jakarta.validation.constraints.Min"
    const val MAX = "jakarta.validation.constraints.Max"
    const val PATTERN = "jakarta.validation.constraints.Pattern"
    const val NOT_BLANK = "jakarta.validation.constraints.NotBlank"
    const val EMAIL = "jakarta.validation.constraints.Email"

    const val SERVER_SENT_EVENT = "org.springframework.http.codec.ServerSentEvent"
    const val RESOURCE = "org.springframework.core.io.Resource"
    const val FLUX = "reactor.core.publisher.Flux"

    const val PAGEABLE = "org.springframework.data.domain.Pageable"

    val HTTP_METHOD_ANNOTATIONS = mapOf(
        GET_MAPPING to "GET",
        POST_MAPPING to "POST",
        PUT_MAPPING to "PUT",
        DELETE_MAPPING to "DELETE",
        PATCH_MAPPING to "PATCH",
    )
}
