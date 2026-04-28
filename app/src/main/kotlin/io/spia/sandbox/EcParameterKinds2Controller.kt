package io.spia.sandbox

import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.MatrixVariable
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SearchFilter(
    val keyword: String,
    val page: Int = 0,
    val size: Int = 20,
)

@RestController
@RequestMapping("/api/ec-param-kinds2")
class EcParameterKinds2Controller {

    /** Endpoint using @ModelAttribute — DTO fields flattened into query string. */
    @GetMapping("/search")
    fun search(@ModelAttribute filter: SearchFilter): List<String> =
        listOf("result:${filter.keyword}:${filter.page}:${filter.size}")

    /** Endpoint using @CookieValue — cookie value injected from request cookies. */
    @GetMapping("/whoami")
    fun whoami(@CookieValue("session-id") sessionId: String): String =
        "session:$sessionId"

    /** Endpoint using @RequestAttribute — server-side only attribute, excluded from SDK. */
    @GetMapping("/request-attr")
    fun requestAttr(@RequestAttribute("requestId") requestId: String): String =
        "requestId:$requestId"

    /** Endpoint using @MatrixVariable — key=value segments in path. */
    @GetMapping("/items/{id}")
    fun getItemWithMatrix(
        @PathVariable id: Long,
        @MatrixVariable color: String,
    ): String = "item:$id:color=$color"
}
