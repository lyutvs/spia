package io.spia.sandbox

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class EcTimeoutResponse(val message: String, val delayMs: Long)

/**
 * EC-10 demo controller: exposes an endpoint that artificially sleeps for 1 second
 * so callers can exercise the client-side timeout + AbortSignal logic in the generated SDK.
 */
@RestController
@RequestMapping("/api/ec-timeout")
class EcTimeoutController {

    /**
     * Sleeps for 1 second before responding — useful for testing timeoutMs + AbortSignal.
     */
    @GetMapping("/slow")
    fun slow(): EcTimeoutResponse {
        Thread.sleep(1000)
        return EcTimeoutResponse(message = "EC-10: done after 1 s delay", delayMs = 1000)
    }

    /** Responds immediately — baseline for comparison. */
    @GetMapping("/fast")
    fun fast(): EcTimeoutResponse {
        return EcTimeoutResponse(message = "EC-10: instant response", delayMs = 0)
    }
}
