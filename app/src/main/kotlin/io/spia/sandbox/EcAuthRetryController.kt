package io.spia.sandbox

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.atomic.AtomicInteger

data class EcAuthRetryResponse(val message: String, val token: String)

/**
 * EC-09 demo controller: requires Authorization header, occasionally returns 503 SERVICE_UNAVAILABLE
 * to exercise the client-side retry logic in the generated SDK.
 */
@RestController
@RequestMapping("/api/ec-auth-retry")
class EcAuthRetryController {

    private val callCounter = AtomicInteger(0)

    /**
     * Returns 200 on even calls, 503 SERVICE_UNAVAILABLE on odd calls,
     * so callers can verify retry behaviour.
     */
    @GetMapping("/protected")
    fun protectedEndpoint(
        @RequestHeader("Authorization") authorization: String,
    ): EcAuthRetryResponse {
        val count = callCounter.incrementAndGet()
        if (count % 2 != 0) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "EC-09: transient 503 — retry me")
        }
        return EcAuthRetryResponse(
            message = "EC-09: authorized access granted",
            token = authorization,
        )
    }

    /** Always returns 200 — used to verify auth header is forwarded without retry. */
    @GetMapping("/always-ok")
    fun alwaysOk(
        @RequestHeader("Authorization") authorization: String,
    ): EcAuthRetryResponse {
        return EcAuthRetryResponse(
            message = "EC-09: ok",
            token = authorization,
        )
    }
}
