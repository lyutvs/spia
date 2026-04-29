package io.spia.sandbox

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class EcPageableItem(val id: Long, val name: String)

/**
 * EC-06 demo controller: exercises Spring Data Pageable parameter binding.
 * The processor maps the unannotated Pageable param to inline query fields:
 * page?, size?, sort?.
 */
@RestController
@RequestMapping("/api/ec/pageable")
class EcPageableController {

    @GetMapping
    fun list(pageable: Pageable): Page<EcPageableItem> = TODO()
}
