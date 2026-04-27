package io.spia.sandbox

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@JvmInline
value class UserId(val raw: String)

@JvmInline
value class OrderId(val raw: Long)

data class UserWithId(val id: UserId, val name: String)

@RestController
@RequestMapping("/api/ec-value-class")
class EcValueClassController {

    @GetMapping("/users/{id}")
    fun getUser(@PathVariable id: String): UserWithId =
        UserWithId(id = UserId(id), name = "stub")

    @PostMapping("/users")
    fun createUser(@RequestBody body: UserWithId): UserWithId = body

    @GetMapping("/orders/{id}")
    fun getOrderId(@PathVariable id: Long): OrderId = OrderId(id)
}
