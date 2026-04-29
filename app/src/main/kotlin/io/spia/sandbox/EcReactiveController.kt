package io.spia.sandbox

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

data class UserDto(val id: String, val name: String)

@RestController
@RequestMapping("/api/ec-reactive")
class EcReactiveController {

    @GetMapping("/mono")
    fun getMono(): Mono<UserDto> = Mono.just(UserDto(id = "1", name = "Alice"))

    @GetMapping("/flux")
    fun getFlux(): Flux<UserDto> = Flux.just(
        UserDto(id = "1", name = "Alice"),
        UserDto(id = "2", name = "Bob"),
    )

    @GetMapping("/suspend")
    suspend fun getSuspend(): UserDto = UserDto(id = "1", name = "Alice")
}
