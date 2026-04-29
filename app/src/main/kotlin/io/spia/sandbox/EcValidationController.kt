package io.spia.sandbox

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class EcValidationRequest(
    @field:NotNull
    @field:Size(min = 1, max = 50)
    val name: String,
    @field:Min(0)
    @field:Max(120)
    val age: Int,
    @field:Pattern(regexp = "^[A-Z]{2}-\\d{4}$")
    val code: String,
    @field:Email
    val email: String,
)

data class Wrapper<T>(
    @field:Size(min = 1, max = 30)
    @field:NotBlank
    val name: String,
    val items: List<T>,
)

data class WrapperItem(val id: String, val label: String)

@RestController
@RequestMapping("/ec-validation")
class EcValidationController {
    @PostMapping("/submit")
    fun submit(@RequestBody body: EcValidationRequest): EcValidationRequest = body

    @GetMapping("/wrapped")
    fun wrapped(): Wrapper<WrapperItem> = Wrapper(
        name = "items",
        items = listOf(WrapperItem("1", "first")),
    )
}
