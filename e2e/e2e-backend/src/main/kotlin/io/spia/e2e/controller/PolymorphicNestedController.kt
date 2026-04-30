package io.spia.e2e.controller

import io.spia.e2e.dto.Page
import io.spia.e2e.dto.animals.Animal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/poly")
class PolymorphicNestedController {
    @PostMapping("/animals/list")
    fun echoList(@RequestBody animals: List<Animal>): List<Animal> = animals

    @PostMapping("/animals/map")
    fun echoMap(@RequestBody animals: Map<String, Animal>): Map<String, Animal> = animals

    @PostMapping("/animals/page")
    fun echoPage(@RequestBody page: Page<Animal>): Page<Animal> = page
}
