package io.spia.e2e.controller

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/poly")
class UnknownDiscriminatorController {
    // Returns hand-rolled JSON with a subtype the client TS doesn't know.
    // Bypasses Jackson on the way out so we can inject any discriminator value.
    @GetMapping("/animals/unknown", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun unknownSubtype(): String =
        """{"type":"reptile","name":"Iggy","scaleColor":"green"}"""
}
