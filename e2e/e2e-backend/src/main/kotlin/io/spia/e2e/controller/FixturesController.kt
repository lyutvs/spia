package io.spia.e2e.controller

import io.spia.e2e.dto.animals.Animal
import io.spia.e2e.dto.animals.Bird
import io.spia.e2e.dto.animals.Cat
import io.spia.e2e.dto.animals.Dog
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/poly")
class FixturesController {
    @GetMapping("/animals/fixtures")
    fun animalFixtures(): List<Animal> = listOf(
        Dog(name = "Rex", breed = "Husky"),
        Cat(name = "Whiskers", livesLeft = 9),
        Bird(name = "Tweety", wingspanCm = 12.5),
    )

    @GetMapping("/animals/list/fixtures")
    fun animalListFixtures(): List<Animal> = animalFixtures()

    @GetMapping("/animals/map/fixtures")
    fun animalMapFixtures(): Map<String, Animal> = mapOf(
        "alpha" to Dog(name = "Rex", breed = "Husky"),
        "beta" to Cat(name = "Whiskers", livesLeft = 9),
        "gamma" to Bird(name = "Tweety", wingspanCm = 12.5),
    )

    @GetMapping("/animals/page/fixtures")
    fun animalPageFixtures(): io.spia.e2e.dto.Page<Animal> = io.spia.e2e.dto.Page(
        items = animalFixtures(),
        page = 0,
        total = 3,
    )

    @GetMapping("/payments/fixtures")
    fun paymentFixtures(): List<io.spia.e2e.dto.payments.PaymentEvent> = listOf(
        io.spia.e2e.dto.payments.PaymentEvent(
            kind = "card",
            payload = io.spia.e2e.dto.payments.CardPayload(last4 = "4242", brand = "visa"),
            amountCents = 1000,
        ),
        io.spia.e2e.dto.payments.PaymentEvent(
            kind = "bank",
            payload = io.spia.e2e.dto.payments.BankTransferPayload(account = "DE89-3704-0044-0532-0130-00"),
            amountCents = 250000,
        ),
    )

    @GetMapping("/messages/fixtures")
    fun messageFixtures(): List<io.spia.e2e.dto.messages.Message> = listOf(
        io.spia.e2e.dto.messages.TextMessage(body = "hello"),
        io.spia.e2e.dto.messages.ImageMessage(url = "https://example.com/x.png", widthPx = 1024),
    )
}
