package sample

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class PingResponse(val message: String, val echo: String?)

@RestController
@RequestMapping("/ping")
class PingController {
    @GetMapping
    fun ping(@RequestParam(required = false) echo: String?): PingResponse {
        return PingResponse(message = "pong", echo = echo)
    }
}
