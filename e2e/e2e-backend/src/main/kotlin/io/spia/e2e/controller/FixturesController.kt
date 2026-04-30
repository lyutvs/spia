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
}
