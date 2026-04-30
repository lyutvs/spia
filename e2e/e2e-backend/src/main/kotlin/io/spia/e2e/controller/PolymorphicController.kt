package io.spia.e2e.controller

import io.spia.e2e.dto.animals.Animal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/poly")
class PolymorphicController {
    @PostMapping("/animals")
    fun echoAnimal(@RequestBody animal: Animal): Animal = animal

    @PostMapping("/payments")
    fun echoPayment(@RequestBody event: io.spia.e2e.dto.payments.PaymentEvent): io.spia.e2e.dto.payments.PaymentEvent = event

    @PostMapping("/messages")
    fun echoMessage(@RequestBody m: io.spia.e2e.dto.messages.Message): io.spia.e2e.dto.messages.Message = m

    @PostMapping("/envelope")
    fun echoEnvelope(@RequestBody e: io.spia.e2e.dto.events.Envelope): io.spia.e2e.dto.events.Envelope = e
}
