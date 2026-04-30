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
}
