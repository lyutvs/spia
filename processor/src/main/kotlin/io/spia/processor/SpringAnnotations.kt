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

    val HTTP_METHOD_ANNOTATIONS = mapOf(
        GET_MAPPING to "GET",
        POST_MAPPING to "POST",
        PUT_MAPPING to "PUT",
        DELETE_MAPPING to "DELETE",
        PATCH_MAPPING to "PATCH",
    )
}
