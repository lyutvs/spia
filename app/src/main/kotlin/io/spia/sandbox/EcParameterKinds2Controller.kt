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

/**
 * Demo controller exercising Spring's parameter-binding annotations so the SDK
 * generator can be smoke-tested against real Spring routing. User-supplied
 * inputs are NOT echoed back verbatim — reflecting untrusted input would be a
 * stored/reflected XSS vector and an anti-pattern for production code. Each
 * endpoint instead returns a safe summary that demonstrates the binding
 * occurred (length, presence, or charset-sanitized value).
 */
@RestController
@RequestMapping("/api/ec-param-kinds2")
class EcParameterKinds2Controller {

    /** Endpoint using @ModelAttribute — DTO fields flattened into query string. */
    @GetMapping("/search")
    fun search(@ModelAttribute filter: SearchFilter): List<String> =
        listOf("result:keyword-len=${filter.keyword.length}:page=${filter.page}:size=${filter.size}")

    /** Endpoint using @CookieValue — cookie value injected from request cookies. */
    @GetMapping("/whoami")
    fun whoami(@CookieValue("session-id") sessionId: String): String =
        "session-present:${sessionId.isNotBlank()}"

    /** Endpoint using @RequestAttribute — server-side only attribute, excluded from SDK. */
    @GetMapping("/request-attr")
    fun requestAttr(@RequestAttribute("requestId") requestId: String): String =
        "requestId-len:${requestId.length}"

    /** Endpoint using @MatrixVariable — key=value segments in path. */
    @GetMapping("/items/{id}")
    fun getItemWithMatrix(
        @PathVariable id: Long,
        @MatrixVariable color: String,
    ): String {
        val safeColor = color.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(32)
        return "item:$id:color=$safeColor"
    }
}
